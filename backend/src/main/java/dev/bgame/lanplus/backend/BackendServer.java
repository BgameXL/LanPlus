package dev.bgame.lanplus.backend;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class BackendServer {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final BackendConfig cfg;
    private final Store store;
    private final EventHub hub = new EventHub();

    private BackendServer(BackendConfig cfg) {
        this.cfg = cfg;
        this.store = new Store(cfg.heartbeatTtlMs, cfg.baseDomain, cfg.dataFile);
    }

    public static void main(String[] args) throws Exception {
        new BackendServer(BackendConfig.fromEnv()).run();
    }

    private void run() throws IOException {
        ExecutorService pool = Executors.newCachedThreadPool(daemon("backend-worker"));
        ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(daemon("backend-sched"));
        sched.scheduleAtFixedRate(hub::pingAll, 20, 20, TimeUnit.SECONDS);
        sched.scheduleAtFixedRate(store::sweep, 30, 30, TimeUnit.SECONDS);
        sched.scheduleAtFixedRate(store::flush, 10, 10, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(store::flush, "backend-flush"));

        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(cfg.bind);
        log("LAN+ backend up — http+ws " + cfg.bind + ", relay " + cfg.relayHost + ":" + cfg.relayPort
                + ", base domain " + cfg.baseDomain
                + ", data " + (cfg.dataFile == null || cfg.dataFile.isBlank() ? "in-memory" : cfg.dataFile));
        while (true) {
            Socket socket = server.accept();
            pool.execute(() -> handle(socket));
        }
    }

    private void handle(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
            InputStream in = socket.getInputStream();
            Http.Request req = Http.read(in);
            if (req == null) {
                socket.close();
                return;
            }
            if (req.path().equals("/events") && req.isWebSocketUpgrade()) {
                serveEvents(socket, req); // owns the socket until close
                return;
            }
            Resp r = route(req);
            Http.writeJson(socket.getOutputStream(), r.status, r.body);
            socket.close();
        } catch (IOException e) {
            close(socket);
        }
    }

    // REST routing
    private record Resp(int status, Object body) {}

    private static Resp ok(Object body) {
        return new Resp(200, body);
    }

    private static final Resp OK_EMPTY = new Resp(200, Map.of());
    private static final Resp NOT_FOUND = new Resp(404, null);
    private static final Resp BAD = new Resp(400, null);

    private Resp route(Http.Request req) {
        String m = req.method();
        String path = req.path();
        try {
            if (m.equals("POST") && path.equals("/presence")) {
                return presence(req);
            }
            if (m.equals("POST") && path.equals("/friends/add")) {
                return friendsAdd(req);
            }
            if (m.equals("POST") && path.equals("/friends/remove")) {
                return friendsRemove(req);
            }
            if (m.equals("POST") && path.equals("/friends/accept")) {
                return friendsAccept(req);
            }
            if (m.equals("POST") && path.equals("/friends/decline")) {
                return friendsDecline(req);
            }
            if (m.equals("GET") && path.equals("/friends/requests")) {
                return ok(store.friendRequests(uuid(req.param("uuid"))));
            }
            if (m.equals("GET") && path.startsWith("/friends/")) {
                return ok(store.friendList(uuid(path.substring("/friends/".length()))));
            }
            if (m.equals("GET") && path.equals("/users/me")) {
                return ok(store.me(uuid(req.param("uuid"))));
            }
            if (m.equals("GET") && path.equals("/users/resolve")) {
                Map<String, Object> r = store.resolve(req.param("query"));
                return r == null ? NOT_FOUND : ok(r);
            }
            if (m.equals("GET") && path.equals("/users/search")) {
                return ok(store.search(req.param("q")));
            }
            if (m.equals("POST") && path.equals("/invite/create")) {
                return inviteCreate(req);
            }
            if (m.equals("GET") && path.startsWith("/invite/")) {
                return inviteResolve(req, path.substring("/invite/".length()));
            }
            if (m.equals("POST") && path.equals("/relay/ticket")) {
                return relayTicket(req);
            }
            if (m.equals("GET") && path.equals("/relay/validate")) {
                return relayValidate(req);
            }
            if (m.equals("GET") && path.equals("/relay/guest/validate")) {
                return relayGuestValidate(req);
            }
        } catch (RuntimeException e) {
            return BAD;
        }
        return NOT_FOUND;
    }

    private Resp presence(Http.Request req) {
        Map<String, Object> b = Json.parseObject(req.body());
        UUID uuid = uuid((String) b.get("uuid"));
        boolean announceHosting = store.upsertPresence(uuid,
                (String) b.get("username"), (String) b.get("state"), (String) b.get("worldName"),
                (String) b.get("address"), (String) b.get("joinCode"), b.get("skin"));

        Map<String, Object> data = ordered(
                "uuid", uuid.toString(),
                "connectivity", store.connectivity(uuid),
                "state", b.get("state"),
                "worldName", b.get("worldName"),
                "joinCode", b.get("joinCode"));
        Object presenceUpdate = Map.of("type", "PRESENCE_UPDATE", "data", data);
        for (UUID friend : store.friendsOf(uuid)) {
            hub.send(friend, presenceUpdate);
        }
        if (announceHosting) {
            Object event = ordered("type", "FRIEND_STARTED_HOSTING", "uuid", uuid.toString(), "joinCode", b.get("joinCode"));
            for (UUID friend : store.friendsOf(uuid)) {
                hub.send(friend, event);
            }
        }
        return OK_EMPTY;
    }

    private Resp friendsAdd(Http.Request req) {
        Map<String, Object> b = Json.parseObject(req.body());
        UUID uuid = uuid((String) b.get("uuid"));
        UUID friendUuid = uuid((String) b.get("friendUuid"));
        boolean accepted = store.addFriendRequest(uuid, friendUuid);
        if (!accepted) {
            Object username = store.me(uuid).get("username");
            hub.send(friendUuid, ordered("type", "FRIEND_REQUEST",
                    "fromUuid", uuid.toString(), "fromUsername", username));
        }
        return ok(Map.of("success", true));
    }

    private Resp friendsRemove(Http.Request req) {
        Map<String, Object> b = Json.parseObject(req.body());
        store.removeFriend(uuid((String) b.get("uuid")), uuid((String) b.get("friendUuid")));
        return ok(Map.of("success", true));
    }

    private Resp friendsAccept(Http.Request req) {
        Map<String, Object> b = Json.parseObject(req.body());
        store.acceptRequest(uuid((String) b.get("uuid")), uuid((String) b.get("friendUuid")));
        return ok(Map.of("success", true));
    }

    private Resp friendsDecline(Http.Request req) {
        Map<String, Object> b = Json.parseObject(req.body());
        store.declineRequest(uuid((String) b.get("uuid")), uuid((String) b.get("friendUuid")));
        return ok(Map.of("success", true));
    }

    private Resp inviteCreate(Http.Request req) {
        Map<String, Object> b = Json.parseObject(req.body());
        UUID hostUuid = b.get("hostUuid") != null ? uuid((String) b.get("hostUuid")) : null;
        boolean gated = Boolean.TRUE.equals(b.get("gated"));
        String code = store.createInvite(hostUuid, (String) b.get("address"), (String) b.get("worldName"), gated);
        Store.Invite inv = store.invite(code);
        String address = inv != null ? inv.address() : (String) b.get("address");
        return ok(ordered("code", code, "address", address, "expiresIn", 3600));
    }

    private Resp inviteResolve(Http.Request req, String code) {
        Map<String, Object> r = store.resolveInvite(code);
        if (r == null) {
            return NOT_FOUND;
        }
        String guest = req.param("uuid");
        if (guest != null && !guest.isBlank()) {
            Store.Invite invite = store.invite(code);
            if (invite != null && invite.hostUuid() != null) {
                UUID guestUuid = uuid(guest);
                hub.send(invite.hostUuid(), ordered("type", "INVITE_REDEEMED",
                        "guestUuid", guestUuid.toString(), "code", code));
                log("invite " + code + " redeemed by " + guestUuid + " -> host " + invite.hostUuid());
            }
        }
        return ok(r);
    }

    private Resp relayTicket(Http.Request req) {
        Map<String, Object> b = Json.parseObject(req.body());
        boolean gated = Boolean.TRUE.equals(b.get("gated"));
        Object[] minted = store.mintTicketToken(uuid((String) b.get("uuid")), gated);
        String token = (String) minted[0];
        Store.Ticket ticket = (Store.Ticket) minted[1];
        log("relay ticket issued for " + ticket.uuid() + " -> " + ticket.domain() + (gated ? " (gated)" : ""));
        return ok(ordered(
                "ticket", token,
                "relayHost", cfg.relayHost,
                "relayPort", cfg.relayPort,
                "domain", ticket.domain(),
                "expiresIn", 3600));
    }

    private Resp relayValidate(Http.Request req) {
        Store.Ticket ticket = store.validateTicket(req.param("ticket"));
        if (ticket == null) {
            return new Resp(401, null);
        }
        return ok(ordered("uuid", ticket.uuid().toString(), "domain", ticket.domain(),
                "requireToken", ticket.requireToken()));
    }

    private Resp relayGuestValidate(Http.Request req) {
        String domain = store.validateGuestToken(req.param("token"));
        if (domain == null) {
            return new Resp(401, null);
        }
        return ok(ordered("domain", domain));
    }

    // WebSocket events
    private void serveEvents(Socket socket, Http.Request req) {
        WebSocket ws = null;
        try {
            ws = WebSocket.accept(req, socket);
            String message;
            while ((message = ws.readText()) != null) {
                Map<String, Object> m = Json.parseObject(message);
                if ("AUTH".equals(m.get("type"))) {
                    UUID uuid = uuid((String) m.get("uuid"));
                    hub.register(uuid, ws);
                    log("ws auth " + uuid);
                }
            }
        } catch (IOException | RuntimeException e) {
        } finally {
            if (ws != null) {
                hub.unregister(ws);
                ws.close();
            } else {
                close(socket);
            }
        }
    }

    // helpers
    private static UUID uuid(String s) {
        return UUID.fromString(s);
    }

    private static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private static void close(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    static void log(String msg) {
        System.out.println(LocalTime.now().format(TIME) + " [backend] " + msg);
    }

    private static ThreadFactory daemon(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
