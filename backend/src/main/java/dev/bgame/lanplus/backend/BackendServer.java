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
        this.store = new Store(cfg.heartbeatTtlMs, cfg.baseDomain);
    }

    public static void main(String[] args) throws Exception {
        new BackendServer(BackendConfig.fromEnv()).run();
    }

    private void run() throws IOException {
        ExecutorService pool = Executors.newCachedThreadPool(daemon("backend-worker"));
        ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(daemon("backend-sched"));
        sched.scheduleAtFixedRate(hub::pingAll, 20, 20, TimeUnit.SECONDS);
        sched.scheduleAtFixedRate(store::sweep, 30, 30, TimeUnit.SECONDS);

        ServerSocket server = new ServerSocket();
        server.setReuseAddress(true);
        server.bind(cfg.bind);
        log("LAN+ backend up — http+ws " + cfg.bind + ", relay " + cfg.relayHost + ":" + cfg.relayPort
                + ", base domain " + cfg.baseDomain);
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
                Map<String, Object> r = store.resolveInvite(path.substring("/invite/".length()));
                return r == null ? NOT_FOUND : ok(r);
            }
            if (m.equals("POST") && path.equals("/relay/ticket")) {
                return relayTicket(req);
            }
            if (m.equals("GET") && path.equals("/relay/validate")) {
                return relayValidate(req);
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
        store.addFriend(uuid((String) b.get("uuid")), uuid((String) b.get("friendUuid")));
        return ok(Map.of("success", true));
    }

    private Resp inviteCreate(Http.Request req) {
        Map<String, Object> b = Json.parseObject(req.body());
        String code = store.createInvite((String) b.get("address"), (String) b.get("worldName"));
        return ok(ordered("code", code, "expiresIn", 3600));
    }

    private Resp relayTicket(Http.Request req) {
        Map<String, Object> b = Json.parseObject(req.body());
        Object[] minted = store.mintTicketToken(uuid((String) b.get("uuid")));
        String token = (String) minted[0];
        Store.Ticket ticket = (Store.Ticket) minted[1];
        log("relay ticket issued for " + ticket.uuid() + " -> " + ticket.domain());
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
        return ok(ordered("uuid", ticket.uuid().toString(), "domain", ticket.domain()));
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
