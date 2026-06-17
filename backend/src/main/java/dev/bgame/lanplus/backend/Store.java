package dev.bgame.lanplus.backend;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory state for the backend stub: users, presence, friends, invites and relay tickets, plus
 * backend-derived connectivity.
 * No persistence — everything is lost on restart, which is fine for a reference/dev backend.
 *
 * Simplifications vs a real backend: users auto-register on first contact; adding a friend sends a
 * request the other side must accept (auto-accepted if mutual); there is no authentication.
 */
final class Store {

    private static final String[] WORDS = {
            "amber", "brisk", "calm", "dawn", "ember", "fern", "glow", "hazel", "iris", "jade",
            "koi", "lush", "moss", "nova", "opal", "pine", "quartz", "reef", "sage", "tide",
            "umber", "vale", "willow", "zephyr", "cedar", "drift", "flint", "grove", "haven", "lark"
    };
    private static final SecureRandom RNG = new SecureRandom();

    static final class User {
        final UUID uuid;
        volatile String username;
        final String friendCode;
        final String domain;

        User(UUID uuid, String username, String friendCode, String domain) {
            this.uuid = uuid;
            this.username = username;
            this.friendCode = friendCode;
            this.domain = domain;
        }
    }

    static final class Presence {
        volatile String state;
        volatile String worldName;
        volatile String address;
        volatile String joinCode;
        volatile Object skin;
        volatile long lastHeartbeat;
        volatile boolean hosting;
        volatile boolean hostingAnnounced;
    }

    record Ticket(UUID uuid, String domain, boolean requireToken, long expiresAt) {}

    record Invite(UUID hostUuid, String address, String worldName, long expiresAt) {}

    /** A guest invite token bound to a host's relay domain (offline/gated hosting). */
    record GuestToken(String hostDomain, long expiresAt) {}

    private final long ttlMs;
    private final String baseDomain;
    private final Path dataFile;
    private volatile boolean dirty = false;

    private final Map<UUID, User> users = new ConcurrentHashMap<>();
    private final Map<UUID, Presence> presences = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> friends = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> requests = new ConcurrentHashMap<>();
    private final Map<String, Invite> invites = new ConcurrentHashMap<>();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<String, GuestToken> guestTokens = new ConcurrentHashMap<>();

    Store(long ttlMs, String baseDomain, String dataFile) {
        this.ttlMs = ttlMs;
        this.baseDomain = baseDomain;
        this.dataFile = (dataFile == null || dataFile.isBlank()) ? null : Path.of(dataFile);
        load();
    }

    User ensureUser(UUID uuid, String username) {
        boolean[] created = {false};
        User u = users.computeIfAbsent(uuid, k -> {
            created[0] = true;
            return new User(k, username, uniqueFriendCode(), uniqueDomain());
        });
        boolean changed = created[0];
        if (username != null && !username.isBlank() && !username.equals(u.username)) {
            u.username = username;
            changed = true;
        }
        if (changed) {
            markDirty();
        }
        return u;
    }

    Map<String, Object> me(UUID uuid) {
        User u = ensureUser(uuid, null);
        return ordered("uuid", uuid.toString(), "username", u.username == null ? "Player" : u.username, "friendCode", u.friendCode);
    }

    Map<String, Object> resolve(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        for (User u : users.values()) {
            if (query.equalsIgnoreCase(u.friendCode) || (u.username != null && query.equalsIgnoreCase(u.username))) {
                return ordered("uuid", u.uuid.toString(), "username", u.username, "online", "ONLINE".equals(connectivity(u.uuid)));
            }
        }
        return null;
    }

    List<Object> search(String q) {
        List<Object> out = new ArrayList<>();
        if (q == null || q.isBlank()) {
            return out;
        }
        String needle = q.toLowerCase(Locale.ROOT);
        for (User u : users.values()) {
            boolean match = (u.username != null && u.username.toLowerCase(Locale.ROOT).contains(needle))
                    || u.friendCode.toLowerCase(Locale.ROOT).contains(needle);
            if (match) {
                out.add(ordered("uuid", u.uuid.toString(), "username", u.username, "online", "ONLINE".equals(connectivity(u.uuid))));
            }
        }
        return out;
    }

    // presence
    boolean upsertPresence(UUID uuid, String username, String state, String worldName,
                           String address, String joinCode, Object skin) {
        ensureUser(uuid, username);
        Presence p = presences.computeIfAbsent(uuid, k -> new Presence());
        p.state = state;
        p.worldName = worldName;
        p.address = address;
        p.joinCode = joinCode;
        p.skin = skin;
        p.lastHeartbeat = System.currentTimeMillis();
        p.hosting = "HOSTING".equals(state);
        if (!p.hosting) {
            p.hostingAnnounced = false;
            return false;
        }
        if (joinCode != null && !p.hostingAnnounced) {
            p.hostingAnnounced = true;
            return true;
        }
        return false;
    }

    String connectivity(UUID uuid) {
        Presence p = presences.get(uuid);
        if (p == null) {
            return "UNKNOWN";
        }
        long age = System.currentTimeMillis() - p.lastHeartbeat;
        if (age <= ttlMs) {
            return "ONLINE";
        }
        if (age <= 2 * ttlMs) {
            return "STALE";
        }
        return "OFFLINE";
    }

    String hostingJoinCode(UUID uuid) {
        Presence p = presences.get(uuid);
        String c = connectivity(uuid);
        if (p != null && p.hosting && ("ONLINE".equals(c) || "STALE".equals(c))) {
            return p.joinCode;
        }
        return null;
    }

    // friends
    boolean addFriendRequest(UUID uuid, UUID friendUuid) {
        if (uuid.equals(friendUuid)) {
            return false;
        }
        ensureUser(uuid, null);
        ensureUser(friendUuid, null);
        if (friendsOf(uuid).contains(friendUuid)) {
            return true; // already friends
        }
        if (requests.getOrDefault(uuid, Set.of()).contains(friendUuid)) {
            return acceptRequest(uuid, friendUuid); // they already asked us — accept
        }
        requests.computeIfAbsent(friendUuid, k -> ConcurrentHashMap.newKeySet()).add(uuid);
        markDirty();
        return false;
    }

    boolean acceptRequest(UUID uuid, UUID friendUuid) {
        clearRequest(uuid, friendUuid);
        linkFriends(uuid, friendUuid);
        return true;
    }

    void declineRequest(UUID uuid, UUID friendUuid) {
        clearRequest(uuid, friendUuid);
    }

    void removeFriend(UUID uuid, UUID friendUuid) {
        boolean changed = false;
        Set<UUID> a = friends.get(uuid);
        if (a != null) {
            changed |= a.remove(friendUuid);
        }
        Set<UUID> b = friends.get(friendUuid);
        if (b != null) {
            changed |= b.remove(uuid);
        }
        if (changed) {
            markDirty();
        }
    }

    List<Object> friendRequests(UUID uuid) {
        List<Object> out = new ArrayList<>();
        for (UUID rid : requests.getOrDefault(uuid, Set.of())) {
            User u = users.get(rid);
            if (u != null) {
                out.add(ordered("uuid", rid.toString(), "username", u.username, "online", "ONLINE".equals(connectivity(rid))));
            }
        }
        return out;
    }

    private void clearRequest(UUID a, UUID b) {
        boolean changed = false;
        Set<UUID> ra = requests.get(a);
        if (ra != null) {
            changed |= ra.remove(b);
        }
        Set<UUID> rb = requests.get(b);
        if (rb != null) {
            changed |= rb.remove(a);
        }
        if (changed) {
            markDirty();
        }
    }

    private void linkFriends(UUID a, UUID b) {
        friends.computeIfAbsent(a, k -> ConcurrentHashMap.newKeySet()).add(b);
        friends.computeIfAbsent(b, k -> ConcurrentHashMap.newKeySet()).add(a);
        markDirty();
    }

    Set<UUID> friendsOf(UUID uuid) {
        return friends.getOrDefault(uuid, Set.of());
    }

    List<Object> friendList(UUID uuid) {
        List<Object> out = new ArrayList<>();
        for (UUID fid : friendsOf(uuid)) {
            User fu = users.get(fid);
            if (fu == null) {
                continue;
            }
            String conn = connectivity(fid);
            boolean live = "ONLINE".equals(conn) || "STALE".equals(conn);
            Presence p = presences.get(fid);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uuid", fid.toString());
            m.put("username", fu.username);
            m.put("connectivity", conn);
            m.put("state", live && p != null ? p.state : null);
            m.put("worldName", live && p != null ? p.worldName : null);
            m.put("joinCode", hostingJoinCode(fid));
            m.put("skin", p == null ? null : p.skin);
            out.add(m);
        }
        return out;
    }

    String createInvite(UUID hostUuid, String address, String worldName, boolean gated) {
        long expiresAt = System.currentTimeMillis() + 3_600_000;
        String guestAddress = address;
        if (gated && hostUuid != null) {
            // Offline/gated host: bind a single-label relay token to the host's domain and hand guests a
            // token address instead of the host's own domain — the relay validates the token before
            // routing (the in-world uuid is spoofable in offline-mode, so it can't be the gate).
            String hostDomain = ensureUser(hostUuid, null).domain;
            String token = uniqueGuestToken();
            guestTokens.put(token, new GuestToken(hostDomain, expiresAt));
            guestAddress = token + "." + baseDomain + portSuffix(address);
        }
        String code;
        do {
            code = randomCode();
        } while (invites.putIfAbsent(code, new Invite(hostUuid, guestAddress, worldName, expiresAt)) != null);
        return code;
    }

    /** Resolve a guest invite token to its host's relay domain, or null if unknown/expired. */
    String validateGuestToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        GuestToken t = guestTokens.get(token);
        if (t == null || System.currentTimeMillis() > t.expiresAt()) {
            return null;
        }
        return t.hostDomain();
    }

    Invite invite(String code) {
        Invite i = invites.get(code);
        return (i == null || System.currentTimeMillis() > i.expiresAt()) ? null : i;
    }

    Map<String, Object> resolveInvite(String code) {
        Invite i = invite(code);
        return i == null ? null : ordered("address", i.address(), "worldName", i.worldName());
    }

    // relay tickets
    Object[] mintTicketToken(UUID uuid, boolean gated) {
        User u = ensureUser(uuid, null);
        String token = randomHex(32);
        Ticket t = new Ticket(uuid, u.domain, gated, System.currentTimeMillis() + 3_600_000);
        tickets.put(token, t);
        return new Object[]{token, t};
    }

    Ticket validateTicket(String token) {
        Ticket t = tickets.get(token);
        if (t == null || System.currentTimeMillis() > t.expiresAt()) {
            return null;
        }
        return t;
    }

    void sweep() {
        long now = System.currentTimeMillis();
        invites.entrySet().removeIf(e -> now > e.getValue().expiresAt());
        tickets.entrySet().removeIf(e -> now > e.getValue().expiresAt());
        guestTokens.entrySet().removeIf(e -> now > e.getValue().expiresAt());
    }

    private void markDirty() {
        dirty = true;
    }

    synchronized void flush() {
        if (!dirty || dataFile == null) {
            return;
        }
        try {
            List<Object> userList = new ArrayList<>();
            for (User u : users.values()) {
                userList.add(ordered("uuid", u.uuid.toString(), "username", u.username,
                        "friendCode", u.friendCode, "domain", u.domain));
            }
            String json = Json.write(ordered("users", userList,
                    "friends", edgesJson(friends), "requests", edgesJson(requests)));
            if (dataFile.getParent() != null) {
                Files.createDirectories(dataFile.getParent());
            }
            Path tmp = dataFile.resolveSibling(dataFile.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
        } catch (IOException e) {
        }
    }

    private void load() {
        if (dataFile == null || !Files.isReadable(dataFile)) {
            return;
        }
        try {
            Map<String, Object> root = Json.parseObject(Files.readString(dataFile, StandardCharsets.UTF_8));
            if (root.get("users") instanceof List<?> list) {
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) {
                        UUID uuid = UUID.fromString((String) m.get("uuid"));
                        users.put(uuid, new User(uuid, (String) m.get("username"),
                                (String) m.get("friendCode"), (String) m.get("domain")));
                    }
                }
            }
            edgesLoad(root.get("friends"), friends);
            edgesLoad(root.get("requests"), requests);
        } catch (IOException | RuntimeException e) {
        }
    }

    private static Map<String, Object> edgesJson(Map<UUID, Set<UUID>> edges) {
        Map<String, Object> obj = new LinkedHashMap<>();
        for (Map.Entry<UUID, Set<UUID>> e : edges.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            List<Object> ids = new ArrayList<>();
            for (UUID id : e.getValue()) {
                ids.add(id.toString());
            }
            obj.put(e.getKey().toString(), ids);
        }
        return obj;
    }

    private static void edgesLoad(Object node, Map<UUID, Set<UUID>> into) {
        if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                Set<UUID> set = ConcurrentHashMap.newKeySet();
                if (e.getValue() instanceof List<?> ids) {
                    for (Object id : ids) {
                        set.add(UUID.fromString((String) id));
                    }
                }
                into.put(UUID.fromString((String) e.getKey()), set);
            }
        }
    }

    // helpers
    private static final String CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ"; // no 0/1 or I/L/O/U

    private String uniqueFriendCode() {
        String code;
        do {
            code = "LAN-" + randomFrom(CODE_ALPHABET, 5);
        } while (friendCodeTaken(code));
        return code;
    }

    private static String randomFrom(String alphabet, int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(RNG.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private boolean friendCodeTaken(String code) {
        for (User u : users.values()) {
            if (u.friendCode.equals(code)) {
                return true;
            }
        }
        return false;
    }

    private String uniqueDomain() {
        String domain;
        do {
            domain = WORDS[RNG.nextInt(WORDS.length)] + "-" + WORDS[RNG.nextInt(WORDS.length)] + "." + baseDomain;
        } while (domainTaken(domain));
        return domain;
    }

    private boolean domainTaken(String domain) {
        for (User u : users.values()) {
            if (domain.equals(u.domain)) {
                return true;
            }
        }
        return false;
    }

    /** A DNS-safe single-label guest token, e.g. "g" + 20 hex chars (starts with a letter for DNS). */
    private String uniqueGuestToken() {
        String token;
        do {
            token = "g" + randomHex(10);
        } while (guestTokens.containsKey(token));
        return token;
    }

    /** The ":port" tail of a host:port address, or "" when no explicit port (MC then defaults to 25565). */
    private static String portSuffix(String address) {
        if (address == null) {
            return "";
        }
        int colon = address.lastIndexOf(':');
        if (colon < 0) {
            return "";
        }
        String port = address.substring(colon + 1).trim();
        return port.matches("\\d+") ? ":" + port : "";
    }

    private static String randomCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(alphabet.charAt(RNG.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        RNG.nextBytes(b);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte x : b) {
            sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        }
        return sb.toString();
    }

    private static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
