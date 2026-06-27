package dev.bgame.lanplus.backend;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
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
        this.store = new Store(cfg.heartbeatTtlMs, cfg.baseDomain, cfg.dataFile,
                cfg.sessionServerUrl, cfg.allowOffline, cfg.sessionTtlMs);
    }

    public static void main(String[] args) throws Exception {
        new BackendServer(BackendConfig.fromEnv()).run();
    }

    private void run() throws IOException {
        ExecutorService pool = Executors.newCachedThreadPool(daemon("backend-worker"));
        ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor(daemon("backend-sched"));
        sched.scheduleAtFixedRate(hub::pingAll, 20, 20, TimeUnit.SECONDS);
        sched.scheduleAtFixedRate(store::sweep, 30, 30, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(store::close, "backend-close"));

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
    private static final Resp UNAUTHORIZED = new Resp(401, null);
    private static final Resp FORBIDDEN = new Resp(403, null);

    private Resp route(Http.Request req) {
        String m = req.method();
        String path = req.path();
        try {
            if (m.equals("POST") && path.equals("/auth/challenge")) {
                return authChallenge(req);
            }
            if (m.equals("POST") && path.equals("/auth/verify")) {
                return authVerify(req);
            }
            if (m.equals("POST") && path.equals("/auth/offline")) {
                return authOffline(req);
            }
            if (m.equals("GET") && path.equals("/relay/validate")) {
                return relayValidate(req);
            }
            if (m.equals("GET") && path.equals("/relay/guest/validate")) {
                return relayGuestValidate(req);
            }
            Store.Session session = store.validateSession(bearer(req));
            if (session == null) {
                return UNAUTHORIZED;
            }
            UUID self = session.uuid();

            if (m.equals("POST") && path.equals("/presence")) {
                return presence(req, self);
            }
            if (m.equals("POST") && path.equals("/friends/add")) {
                return friendsAdd(req, self);
            }
            if (m.equals("POST") && path.equals("/friends/remove")) {
                return friendsRemove(req, self);
            }
            if (m.equals("POST") && path.equals("/friends/accept")) {
                return friendsAccept(req, self);
            }
            if (m.equals("POST") && path.equals("/friends/decline")) {
                return friendsDecline(req, self);
            }
            if (m.equals("POST") && path.equals("/friends/mute")) {
                return friendsRelation(req, self, "mute");
            }
            if (m.equals("POST") && path.equals("/friends/unmute")) {
                return friendsRelation(req, self, "unmute");
            }
            if (m.equals("POST") && path.equals("/friends/block")) {
                return friendsRelation(req, self, "block");
            }
            if (m.equals("POST") && path.equals("/friends/unblock")) {
                return friendsRelation(req, self, "unblock");
            }
            if (m.equals("GET") && path.equals("/friends/requests")) {
                return ok(store.friendRequests(self));
            }
            if (m.equals("GET") && path.startsWith("/friends/")) {
                return ok(store.friendList(uuid(path.substring("/friends/".length()))));
            }
            if (m.equals("GET") && path.equals("/users/me")) {
                return ok(store.me(self));
            }
            if (m.equals("GET") && path.equals("/users/resolve")) {
                Map<String, Object> r = store.resolve(req.param("query"));
                return r == null ? NOT_FOUND : ok(r);
            }
            if (m.equals("GET") && path.equals("/users/search")) {
                return ok(store.search(req.param("q")));
            }
            if (m.equals("GET") && path.equals("/profile")) {
                Map<String, Object> p = store.profile(uuid(req.param("uuid")), self);
                return p == null ? NOT_FOUND : ok(p);
            }
            if (m.equals("POST") && path.equals("/profile/update")) {
                return profileUpdate(req, self);
            }
            if (m.equals("POST") && path.equals("/profile/advancement")) {
                return profileAdvancement(req, self);
            }
            if (m.equals("GET") && path.equals("/modpacks")) {
                return ok(store.listModpacks());
            }
            if (m.equals("POST") && path.equals("/invite/create")) {
                return inviteCreate(req, self);
            }
            if (m.equals("GET") && path.startsWith("/invite/")) {
                return inviteResolve(path.substring("/invite/".length()), self);
            }
            if (m.equals("POST") && path.equals("/relay/ticket")) {
                return relayTicket(req, self);
            }
        } catch (RuntimeException e) {
            return BAD;
        }
        return NOT_FOUND;
    }

    // auth
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private Resp authChallenge(Http.Request req) {
        Map<String, Object> b = Json.parseObject(req.body());
        String username = (String) b.get("username");
        if (username == null || !USERNAME.matcher(username).matches()) {
            return BAD;
        }
        return ok(Map.of("serverId", store.newChallenge()));
    }

    private Resp authVerify(Http.Request req) {
        Map<String, Object> b = Json.parseObject(req.body());
        String username = (String) b.get("username");
        if (username == null || !USERNAME.matcher(username).matches()) {
            return UNAUTHORIZED;
        }
        Store.AuthResult r = store.authVerify(username, (String) b.get("serverId"));
        return r == null ? UNAUTHORIZED : ok(authBody(r));
    }

    private Resp authOffline(Http.Request req) {
        if (!store.isOfflineAllowed()) {
            return FORBIDDEN;
        }
        Map<String, Object> b = Json.parseObject(req.body());
        String username = (String) b.get("username");
        if (username == null || !USERNAME.matcher(username).matches()) {
            return BAD;
        }
        return ok(authBody(store.authOffline(username)));
    }

    private static Map<String, Object> authBody(Store.AuthResult r) {
        return ordered("token", r.token(), "uuid", r.uuid().toString(),
                "verified", r.verified(), "expiresIn", r.expiresInSeconds());
    }

    private static String bearer(Http.Request req) {
        String h = req.headers().get("authorization");
        if (h == null) {
            return null;
        }
        h = h.trim();
        return h.regionMatches(true, 0, "Bearer ", 0, 7) ? h.substring(7).trim() : null;
    }

    private Resp presence(Http.Request req, UUID uuid) {
        Map<String, Object> b = Json.parseObject(req.body());
        boolean announceHosting = store.upsertPresence(uuid,
                (String) b.get("username"), (String) b.get("state"), (String) b.get("worldName"),
                (String) b.get("address"), (String) b.get("joinCode"), b.get("skin"),
                (String) b.get("modpackId"), (String) b.get("accessMode"),
                parseUuidSet(b.get("allowedUuids")));

        boolean invisible = store.isInvisible(uuid);
        String connectivity = invisible ? "OFFLINE" : store.connectivity(uuid);
        Object state = invisible ? null : b.get("state");
        Object worldName = invisible ? null : b.get("worldName");
        Object modpackId = (invisible || !store.currentlyPlayingVisible(uuid))
                ? null : store.registeredModpackOrNull((String) b.get("modpackId"));
        Object joinCode = b.get("joinCode");

        Set<UUID> recipients = new LinkedHashSet<>();
        for (UUID friend : store.friendsOf(uuid)) {
            if (!store.isMutedOrBlocked(friend, uuid)) {
                recipients.add(friend);
            }
        }
        for (UUID friend : recipients) {
            boolean codeVisible = !invisible && store.joinCodeVisibleTo(uuid, friend);
            Map<String, Object> data = ordered(
                    "uuid", uuid.toString(),
                    "connectivity", connectivity,
                    "state", state,
                    "worldName", worldName,
                    "joinCode", codeVisible ? joinCode : null,
                    "modpackId", modpackId);
            hub.send(friend, Map.of("type", "PRESENCE_UPDATE", "data", data));
        }
        if (announceHosting && !invisible) {
            for (UUID friend : recipients) {
                if (!store.joinCodeVisibleTo(uuid, friend)) {
                    continue;
                }
                hub.send(friend, ordered("type", "FRIEND_STARTED_HOSTING", "uuid", uuid.toString(), "joinCode", joinCode));
            }
        }
        return OK_EMPTY;
    }

    private static Set<UUID> parseUuidSet(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return Set.of();
        }
        Set<UUID> out = new LinkedHashSet<>();
        for (Object o : list) {
            if (o instanceof String s && !s.isBlank()) {
                try {
                    out.add(UUID.fromString(s.trim()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return out;
    }

    private Resp friendsAdd(Http.Request req, UUID uuid) {
        Map<String, Object> b = Json.parseObject(req.body());
        UUID friendUuid = uuid((String) b.get("friendUuid"));
        Store.AddResult result = store.addFriendRequest(uuid, friendUuid);
        if (result == Store.AddResult.REQUESTED) {
            Object username = store.me(uuid).get("username");
            hub.send(friendUuid, ordered("type", "FRIEND_REQUEST",
                    "fromUuid", uuid.toString(), "fromUsername", username));
        }
        return ok(Map.of("success", result != Store.AddResult.BLOCKED));
    }

    private Resp friendsRelation(Http.Request req, UUID uuid, String action) {
        Map<String, Object> b = Json.parseObject(req.body());
        UUID target = uuid((String) b.get("targetUuid"));
        switch (action) {
            case "mute" -> store.setMuted(uuid, target, true);
            case "unmute" -> store.setMuted(uuid, target, false);
            case "block" -> store.setBlocked(uuid, target, true);
            case "unblock" -> store.setBlocked(uuid, target, false);
            default -> {
                return BAD;
            }
        }
        return ok(Map.of("success", true));
    }

    private Resp friendsRemove(Http.Request req, UUID uuid) {
        Map<String, Object> b = Json.parseObject(req.body());
        store.removeFriend(uuid, uuid((String) b.get("friendUuid")));
        return ok(Map.of("success", true));
    }

    private Resp friendsAccept(Http.Request req, UUID uuid) {
        Map<String, Object> b = Json.parseObject(req.body());
        store.acceptRequest(uuid, uuid((String) b.get("friendUuid")));
        return ok(Map.of("success", true));
    }

    private Resp friendsDecline(Http.Request req, UUID uuid) {
        Map<String, Object> b = Json.parseObject(req.body());
        store.declineRequest(uuid, uuid((String) b.get("friendUuid")));
        return ok(Map.of("success", true));
    }

    private static final Set<String> PRONOUN_OPTIONS = Set.of("he/him", "she/her", "they/them");
    private static final Pattern HANDLE = Pattern.compile("[A-Za-z0-9_.]{1,30}");
    private static final Pattern OBVIOUS_DOMAIN = Pattern.compile("(?<![\\w.])[a-z0-9-]+\\.[a-z]{2,24}(?![\\w])");
    private static final Pattern ADVANCEMENT_ID = Pattern.compile("[a-z0-9_./:-]{1,200}");

    private Resp profileAdvancement(Http.Request req, UUID uuid) {
        Map<String, Object> b = Json.parseObject(req.body());
        String advId = (String) b.get("advancementId");
        if (advId == null || !ADVANCEMENT_ID.matcher(advId = advId.trim()).matches()) {
            return ok(Map.of("success", false));
        }
        store.recordAdvancement(uuid, advId);
        return ok(Map.of("success", true));
    }

    private Resp profileUpdate(Http.Request req, UUID uuid) {
        Map<String, Object> b = Json.parseObject(req.body());
        store.ensureUser(uuid, null);
        if (b.containsKey("bio")) {
            String bio = b.get("bio") == null ? "" : String.valueOf(b.get("bio"));
            if (bio.length() > 300) {
                return ok(error("bio_too_long"));
            }
            if (containsObviousLink(bio)) {
                return ok(error("bio_link"));
            }
            store.setBio(uuid, bio);
        }
        if (b.containsKey("pronouns")) {
            Object pr = b.get("pronouns");
            String pronouns = pr == null ? null : String.valueOf(pr);
            if (pronouns != null && !PRONOUN_OPTIONS.contains(pronouns)) {
                return ok(error("bad_pronouns"));
            }
            store.setPronouns(uuid, pronouns);
        }
        if (b.get("links") instanceof Map<?, ?> links) {
            for (Map.Entry<?, ?> e : links.entrySet()) {
                String platform = String.valueOf(e.getKey());
                if (!store.isLinkPlatform(platform)) {
                    continue;
                }
                String value = e.getValue() == null ? null : String.valueOf(e.getValue());
                if (value != null && !value.isBlank() && !HANDLE.matcher(value).matches()) {
                    return ok(error("bad_link"));
                }
                store.setLink(uuid, platform, value);
            }
        }
        if (b.get("invisible") instanceof Boolean invisible) {
            store.setInvisible(uuid, invisible);
        }
        if (b.get("prompts") instanceof Map<?, ?> prompts) {
            Map<String, String> answers = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : prompts.entrySet()) {
                String id = String.valueOf(e.getKey());
                if (!store.isPromptId(id)) {
                    return ok(error("bad_prompt"));
                }
                String answer = e.getValue() == null ? null : String.valueOf(e.getValue());
                if (answer == null || answer.isBlank()) {
                    continue;
                }
                if (answer.length() > 140) {
                    return ok(error("prompt_too_long"));
                }
                if (containsObviousLink(answer)) {
                    return ok(error("prompt_link"));
                }
                answers.put(id, answer);
            }
            if (answers.size() > Store.MAX_PROMPTS) {
                return ok(error("too_many_prompts"));
            }
            store.setPrompts(uuid, answers);
        }
        if (b.containsKey("favoriteModpackId")) {
            Object fav = b.get("favoriteModpackId");
            String favoriteId = fav == null ? null : String.valueOf(fav);
            if (favoriteId != null && !favoriteId.isBlank() && store.registeredModpackOrNull(favoriteId) == null) {
                return ok(error("bad_modpack"));
            }
            store.setFavoriteModpack(uuid, favoriteId);
        }
        if (b.get("favoriteVisible") instanceof Boolean favVis) {
            store.setModpackVisibility(uuid, "favorite_modpack_visible", favVis);
        }
        if (b.get("currentlyPlayingVisible") instanceof Boolean playVis) {
            store.setModpackVisibility(uuid, "currently_playing_visible", playVis);
        }
        if (b.get("recentlyPlayedVisible") instanceof Boolean recentVis) {
            store.setModpackVisibility(uuid, "recently_played_visible", recentVis);
        }
        return ok(Map.of("success", true));
    }

    private static Map<String, Object> error(String code) {
        return ordered("success", false, "error", code);
    }

    private static boolean containsObviousLink(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        if (t.contains("http://") || t.contains("https://") || t.contains("www.")) {
            return true;
        }
        return OBVIOUS_DOMAIN.matcher(t).find();
    }

    private Resp inviteCreate(Http.Request req, UUID hostUuid) {
        Map<String, Object> b = Json.parseObject(req.body());
        boolean gated = Boolean.TRUE.equals(b.get("gated"));
        String code = store.createInvite(hostUuid, (String) b.get("address"), (String) b.get("worldName"), gated);
        Store.Invite inv = store.invite(code);
        String address = inv != null ? inv.address() : (String) b.get("address");
        return ok(ordered("code", code, "address", address, "expiresIn", 3600));
    }

    private Resp inviteResolve(String code, UUID guestUuid) {
        Map<String, Object> r = store.resolveInvite(code);
        if (r == null) {
            return NOT_FOUND;
        }
        Store.Invite invite = store.invite(code);
        if (invite != null && invite.hostUuid() != null) {
            hub.send(invite.hostUuid(), ordered("type", "INVITE_REDEEMED",
                    "guestUuid", guestUuid.toString(), "code", code));
            log("invite " + code + " redeemed by " + guestUuid + " -> host " + invite.hostUuid());
        }
        return ok(r);
    }

    private Resp relayTicket(Http.Request req, UUID uuid) {
        Map<String, Object> b = Json.parseObject(req.body());
        boolean gated = Boolean.TRUE.equals(b.get("gated"));
        Object[] minted = store.mintTicketToken(uuid, gated);
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
                    Store.Session session = store.validateSession((String) m.get("token"));
                    if (session == null) {
                        break;
                    }
                    hub.register(session.uuid(), ws);
                    log("ws auth " + session.uuid());
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
