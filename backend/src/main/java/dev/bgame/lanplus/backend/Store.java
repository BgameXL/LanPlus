package dev.bgame.lanplus.backend;

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
 * Simplifications vs a real backend: users auto-register on first contact; friend links are made
 * mutual on add (so two players see each other without both adding); there is no authentication.
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

    record Ticket(UUID uuid, String domain, long expiresAt) {}

    record Invite(String address, String worldName, long expiresAt) {}

    private final long ttlMs;
    private final String baseDomain;

    private final Map<UUID, User> users = new ConcurrentHashMap<>();
    private final Map<UUID, Presence> presences = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> friends = new ConcurrentHashMap<>();
    private final Map<String, Invite> invites = new ConcurrentHashMap<>();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    Store(long ttlMs, String baseDomain) {
        this.ttlMs = ttlMs;
        this.baseDomain = baseDomain;
    }

    User ensureUser(UUID uuid, String username) {
        User u = users.computeIfAbsent(uuid, k -> new User(k, username, uniqueFriendCode(username), uniqueDomain()));
        if (username != null && !username.isBlank()) {
            u.username = username;
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
    void addFriend(UUID uuid, UUID friendUuid) {
        ensureUser(friendUuid, null);
        friends.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(friendUuid);
        friends.computeIfAbsent(friendUuid, k -> ConcurrentHashMap.newKeySet()).add(uuid); // mutual
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

    String createInvite(String address, String worldName) {
        String code;
        do {
            code = randomCode();
        } while (invites.putIfAbsent(code, new Invite(address, worldName, System.currentTimeMillis() + 3_600_000)) != null);
        return code;
    }

    Map<String, Object> resolveInvite(String code) {
        Invite i = invites.get(code);
        if (i == null || System.currentTimeMillis() > i.expiresAt()) {
            return null;
        }
        return ordered("address", i.address(), "worldName", i.worldName());
    }

    // relay tickets
    Object[] mintTicketToken(UUID uuid) {
        User u = ensureUser(uuid, null);
        String token = randomHex(32);
        Ticket t = new Ticket(uuid, u.domain, System.currentTimeMillis() + 3_600_000);
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
    }

    // helpers
    private String uniqueFriendCode(String username) {
        String base = codeBase(username);
        String code;
        do {
            code = base + "-" + (1000 + RNG.nextInt(9000));
        } while (friendCodeTaken(code));
        return code;
    }

    private boolean friendCodeTaken(String code) {
        for (User u : users.values()) {
            if (u.friendCode.equals(code)) {
                return true;
            }
        }
        return false;
    }

    private static String codeBase(String username) {
        String letters = username == null ? "" : username.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);
        if (letters.isEmpty()) {
            letters = "PLR";
        }
        return (letters + "XXXX").substring(0, 4);
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
