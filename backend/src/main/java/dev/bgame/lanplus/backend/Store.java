package dev.bgame.lanplus.backend;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Backend state. Durable identity and social graph (users, friends, friend_requests, relationships)
 * lives in SQLite; presence, invites, and relay tickets stay in memory because they are ephemeral
 * (TTL-bounded) and correct to lose on restart. See docs/dev/SQLITE.md.
 *
 * Concurrency: a single shared JDBC Connection guarded by {@link #lock} (SQLite admits one writer;
 * WAL allows concurrent readers, but a single Connection is not thread-safe, so all DB access is
 * serialized). Presence/invite/ticket maps are concurrent and need no lock.
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
        final String username;
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
        volatile String modpackId;   // raw self-declared id; resolved against modpack_registry at read time
        volatile long lastHeartbeat;
        volatile boolean hosting;
        volatile boolean hostingAnnounced;
        volatile boolean onlinePersisted;   // wrote presence_state.online=1 for the current session
        volatile boolean disconnectRecorded; // wrote presence_state.online=0 + last_disconnect_at for this gap
    }

    record Ticket(UUID uuid, String domain, boolean requireToken, long expiresAt) {}

    record Invite(UUID hostUuid, String address, String worldName, long expiresAt) {}

    record GuestToken(String hostDomain, long expiresAt) {}

    /** Outcome of a friend-add: became friends, a request was queued, or it was refused (self/blocked). */
    enum AddResult { ACCEPTED, REQUESTED, BLOCKED }

    private final long ttlMs;
    private final String baseDomain;

    private final Object lock = new Object();
    private final Connection connection;

    private final Map<UUID, Presence> presences = new ConcurrentHashMap<>();
    private final Map<String, Invite> invites = new ConcurrentHashMap<>();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<String, GuestToken> guestTokens = new ConcurrentHashMap<>();

    Store(long ttlMs, String baseDomain, String dataFile) {
        this.ttlMs = ttlMs;
        this.baseDomain = baseDomain;
        String path = (dataFile == null || dataFile.isBlank()) ? ":memory:" : dataFile;
        this.connection = openDb(path);
    }

    private static Connection openDb(String path) {
        try {
            Class.forName("org.sqlite.JDBC");
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA busy_timeout=5000");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS users ("
                        + "uuid TEXT PRIMARY KEY, username TEXT, friend_code TEXT UNIQUE NOT NULL, "
                        + "domain TEXT UNIQUE NOT NULL, last_modpack TEXT)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS friends ("
                        + "a TEXT NOT NULL, b TEXT NOT NULL, PRIMARY KEY (a, b), CHECK (a < b))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS friend_requests ("
                        + "from_uuid TEXT NOT NULL, to_uuid TEXT NOT NULL, PRIMARY KEY (from_uuid, to_uuid))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS relationships ("
                        + "uuid TEXT NOT NULL, target TEXT NOT NULL, muted INTEGER NOT NULL DEFAULT 0, "
                        + "blocked INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (uuid, target))");
                // profiles (opt-in social identity; see docs/dev/PROFILES_DESIGN.md)
                st.executeUpdate("CREATE TABLE IF NOT EXISTS profile_bio ("
                        + "uuid TEXT PRIMARY KEY, text TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS profile_identity ("
                        + "uuid TEXT PRIMARY KEY, pronouns TEXT)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS profile_links ("
                        + "uuid TEXT PRIMARY KEY, discord TEXT, instagram TEXT, twitter TEXT, youtube TEXT, "
                        + "twitch TEXT, tiktok TEXT, paypal TEXT, kofi TEXT)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS profile_privacy ("
                        + "uuid TEXT PRIMARY KEY, invisible_mode INTEGER NOT NULL DEFAULT 0)");
                // profile_prompts: answers to the predefined "Questions about yourself" (<=3 per player).
                // EAV (narrow) so the prompt catalog can grow without schema migration; answer holds the
                // free text or the choice token. See PROFILES_DESIGN.md § Questions about yourself.
                st.executeUpdate("CREATE TABLE IF NOT EXISTS profile_prompts ("
                        + "uuid TEXT NOT NULL, prompt_id TEXT NOT NULL, answer TEXT NOT NULL, "
                        + "updated_at INTEGER NOT NULL, PRIMARY KEY (uuid, prompt_id))");
                // modpack_registry: the only source of modpack metadata. modpack_id is assigned by the LAN+
                // author at registration (curated, no public write endpoint); the pack's datapack only
                // self-declares this id. Unregistered ids are ignored. See PROFILES_DESIGN.md § Modpack.
                st.executeUpdate("CREATE TABLE IF NOT EXISTS modpack_registry ("
                        + "modpack_id TEXT PRIMARY KEY, name TEXT NOT NULL, icon_url TEXT, author TEXT, "
                        + "team TEXT, download_url TEXT, description TEXT)");
                // profile_settings: manual favorite modpack + per-signal visibility toggles. most_played
                // is derived from playtime in a later iteration; its toggle column exists now (default on)
                // but is not surfaced yet. currently_playing_visible is subordinate to invisible_mode (the
                // master switch). See PROFILES_DESIGN.md § Modpack.
                st.executeUpdate("CREATE TABLE IF NOT EXISTS profile_settings ("
                        + "uuid TEXT PRIMARY KEY, favorite_modpack_id TEXT, "
                        + "favorite_modpack_visible INTEGER NOT NULL DEFAULT 1, "
                        + "most_played_visible INTEGER NOT NULL DEFAULT 1, "
                        + "currently_playing_visible INTEGER NOT NULL DEFAULT 1, "
                        + "updated_at INTEGER NOT NULL)");
                // presence_state: persisted last online->offline transition + best-effort online mirror
                // (the authoritative live state stays in-memory, derived from heartbeat TTL).
                st.executeUpdate("CREATE TABLE IF NOT EXISTS presence_state ("
                        + "uuid TEXT PRIMARY KEY, online INTEGER NOT NULL DEFAULT 0, last_disconnect_at INTEGER)");
                // migrate: drop the now-unused users.last_seen (superseded by presence_state.last_disconnect_at)
                if (columnExists(st, "users", "last_seen")) {
                    st.executeUpdate("ALTER TABLE users DROP COLUMN last_seen");
                }
                // a fresh process has no live connections yet — reset the persisted online mirror
                st.executeUpdate("UPDATE presence_state SET online=0");
            }
            return c;
        } catch (ClassNotFoundException | SQLException e) {
            throw new IllegalStateException("LAN+ backend: cannot open SQLite database at " + path, e);
        }
    }

    private static boolean columnExists(Statement st, String table, String column) throws SQLException {
        try (ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    void close() {
        synchronized (lock) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
        }
    }

    // users
    User ensureUser(UUID uuid, String username) {
        synchronized (lock) {
            try {
                User u = findUser(uuid);
                if (u == null) {
                    String friendCode = uniqueFriendCode();
                    String domain = uniqueDomain();
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO users (uuid, username, friend_code, domain) VALUES (?,?,?,?)")) {
                        ps.setString(1, uuid.toString());
                        ps.setString(2, username);
                        ps.setString(3, friendCode);
                        ps.setString(4, domain);
                        ps.executeUpdate();
                    }
                    return new User(uuid, username, friendCode, domain);
                }
                if (username != null && !username.isBlank() && !username.equals(u.username)) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE users SET username=? WHERE uuid=?")) {
                        ps.setString(1, username);
                        ps.setString(2, uuid.toString());
                        ps.executeUpdate();
                    }
                    return new User(uuid, username, u.friendCode, u.domain);
                }
                return u;
            } catch (SQLException e) {
                throw fail("ensureUser", e);
            }
        }
    }

    Map<String, Object> me(UUID uuid) {
        User u = ensureUser(uuid, null);
        return ordered("uuid", uuid.toString(), "username", u.username == null ? "Player" : u.username,
                "friendCode", u.friendCode);
    }

    Map<String, Object> resolve(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid, username FROM users WHERE friend_code = ? COLLATE NOCASE "
                            + "OR username = ? COLLATE NOCASE LIMIT 1")) {
                ps.setString(1, query);
                ps.setString(2, query);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString(1));
                        return ordered("uuid", rs.getString(1), "username", rs.getString(2),
                                "online", "ONLINE".equals(connectivity(uuid)));
                    }
                }
            } catch (SQLException e) {
                throw fail("resolve", e);
            }
        }
        return null;
    }

    List<Object> search(String q) {
        List<Object> out = new ArrayList<>();
        if (q == null || q.isBlank()) {
            return out;
        }
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid, username FROM users WHERE username LIKE ? COLLATE NOCASE "
                            + "OR friend_code LIKE ? COLLATE NOCASE")) {
                String like = "%" + q + "%";
                ps.setString(1, like);
                ps.setString(2, like);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID uuid = UUID.fromString(rs.getString(1));
                        out.add(ordered("uuid", rs.getString(1), "username", rs.getString(2),
                                "online", "ONLINE".equals(connectivity(uuid))));
                    }
                }
            } catch (SQLException e) {
                throw fail("search", e);
            }
        }
        return out;
    }

    // presence (in-memory: ephemeral, derived connectivity)
    boolean upsertPresence(UUID uuid, String username, String state, String worldName,
                           String address, String joinCode, Object skin, String modpackId) {
        ensureUser(uuid, username);
        Presence p = presences.computeIfAbsent(uuid, k -> new Presence());
        p.state = state;
        p.worldName = worldName;
        p.address = address;
        p.joinCode = joinCode;
        p.skin = skin;
        p.modpackId = modpackId;
        p.lastHeartbeat = System.currentTimeMillis();
        p.hosting = "HOSTING".equals(state);
        if (!p.onlinePersisted) {
            // offline -> online edge (first heartbeat of a session, or a reconnect after a recorded gap)
            p.onlinePersisted = true;
            p.disconnectRecorded = false;
            markOnline(uuid);
        }
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

    private static boolean isLive(String connectivity) {
        return "ONLINE".equals(connectivity) || "STALE".equals(connectivity);
    }

    private void markOnline(UUID uuid) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO presence_state (uuid, online) VALUES (?,1) "
                            + "ON CONFLICT(uuid) DO UPDATE SET online=1")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw fail("markOnline", e);
            }
        }
    }

    private void markOffline(UUID uuid, long at) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO presence_state (uuid, online, last_disconnect_at) VALUES (?,0,?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET online=0, last_disconnect_at=excluded.last_disconnect_at")) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, at);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw fail("markOffline", e);
            }
        }
    }

    // profile privacy (invisible mode) — see PROFILES_DESIGN.md § Modo invisible
    boolean isInvisible(UUID uuid) {
        synchronized (lock) {
            return invisibleLocked(uuid);
        }
    }

    void setInvisible(UUID uuid, boolean invisible) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO profile_privacy (uuid, invisible_mode) VALUES (?,?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET invisible_mode=excluded.invisible_mode")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, invisible ? 1 : 0);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw fail("setInvisible", e);
            }
        }
    }

    private boolean invisibleLocked(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT invisible_mode FROM profile_privacy WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) != 0;
            }
        } catch (SQLException e) {
            throw fail("isInvisible", e);
        }
    }

    private Long lastDisconnectLocked(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT last_disconnect_at FROM presence_state WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    long v = rs.getLong(1);
                    return rs.wasNull() ? null : v;
                }
                return null;
            }
        } catch (SQLException e) {
            throw fail("lastDisconnectAt", e);
        }
    }

    // friends
    AddResult addFriendRequest(UUID uuid, UUID friendUuid) {
        if (uuid.equals(friendUuid)) {
            return AddResult.BLOCKED;
        }
        ensureUser(uuid, null);
        ensureUser(friendUuid, null);
        synchronized (lock) {
            try {
                if (areFriends(uuid, friendUuid)) {
                    return AddResult.ACCEPTED;
                }
                if (isBlockedLocked(friendUuid, uuid)) {
                    return AddResult.BLOCKED;
                }
                if (requestExists(friendUuid, uuid)) {
                    acceptRequestLocked(uuid, friendUuid);
                    return AddResult.ACCEPTED;
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT OR IGNORE INTO friend_requests (from_uuid, to_uuid) VALUES (?,?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, friendUuid.toString());
                    ps.executeUpdate();
                }
                return AddResult.REQUESTED;
            } catch (SQLException e) {
                throw fail("addFriendRequest", e);
            }
        }
    }

    boolean acceptRequest(UUID uuid, UUID friendUuid) {
        synchronized (lock) {
            try {
                return acceptRequestLocked(uuid, friendUuid);
            } catch (SQLException e) {
                throw fail("acceptRequest", e);
            }
        }
    }

    void declineRequest(UUID uuid, UUID friendUuid) {
        synchronized (lock) {
            try {
                clearRequestLocked(uuid, friendUuid);
            } catch (SQLException e) {
                throw fail("declineRequest", e);
            }
        }
    }

    void removeFriend(UUID uuid, UUID friendUuid) {
        String[] pair = normalize(uuid, friendUuid);
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM friends WHERE a=? AND b=?")) {
                ps.setString(1, pair[0]);
                ps.setString(2, pair[1]);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw fail("removeFriend", e);
            }
        }
    }

    List<Object> friendRequests(UUID uuid) {
        List<Object> out = new ArrayList<>();
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT r.from_uuid, u.username FROM friend_requests r "
                            + "JOIN users u ON u.uuid = r.from_uuid WHERE r.to_uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        UUID rid = UUID.fromString(rs.getString(1));
                        out.add(ordered("uuid", rs.getString(1), "username", rs.getString(2),
                                "online", "ONLINE".equals(connectivity(rid))));
                    }
                }
            } catch (SQLException e) {
                throw fail("friendRequests", e);
            }
        }
        return out;
    }

    Set<UUID> friendsOf(UUID uuid) {
        Set<UUID> out = new LinkedHashSet<>();
        String s = uuid.toString();
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT b FROM friends WHERE a=? UNION SELECT a FROM friends WHERE b=?")) {
                ps.setString(1, s);
                ps.setString(2, s);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        out.add(UUID.fromString(rs.getString(1)));
                    }
                }
            } catch (SQLException e) {
                throw fail("friendsOf", e);
            }
        }
        return out;
    }

    List<Object> friendList(UUID uuid) {
        Map<UUID, String> names = new LinkedHashMap<>();
        Map<UUID, int[]> rel = new HashMap<>();
        Set<UUID> invisible = new HashSet<>();
        String s = uuid.toString();
        synchronized (lock) {
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT u.uuid, u.username FROM friends f "
                                + "JOIN users u ON u.uuid = (CASE WHEN f.a=? THEN f.b ELSE f.a END) "
                                + "WHERE f.a=? OR f.b=?")) {
                    ps.setString(1, s);
                    ps.setString(2, s);
                    ps.setString(3, s);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            names.put(UUID.fromString(rs.getString(1)), rs.getString(2));
                        }
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT target, muted, blocked FROM relationships WHERE uuid=?")) {
                    ps.setString(1, s);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            rel.put(UUID.fromString(rs.getString(1)), new int[]{rs.getInt(2), rs.getInt(3)});
                        }
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT uuid FROM profile_privacy WHERE invisible_mode != 0")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            invisible.add(UUID.fromString(rs.getString(1)));
                        }
                    }
                }
            } catch (SQLException e) {
                throw fail("friendList", e);
            }
        }
        List<Object> out = new ArrayList<>();
        for (Map.Entry<UUID, String> e : names.entrySet()) {
            UUID fid = e.getKey();
            int[] r = rel.get(fid);
            boolean muted = r != null && r[0] == 1;
            boolean blocked = r != null && r[1] == 1;
            boolean suppress = muted || blocked;
            boolean hidden = invisible.contains(fid);
            // an invisible friend appears offline to everyone (presence broadcast is masked too); the
            // invite code stays the real key, so this never blocks an actual join — see PROFILES_DESIGN.md.
            String conn = suppress ? "UNKNOWN" : (hidden ? "OFFLINE" : connectivity(fid));
            boolean live = !hidden && ("ONLINE".equals(conn) || "STALE".equals(conn));
            Presence p = presences.get(fid);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uuid", fid.toString());
            m.put("username", e.getValue());
            m.put("connectivity", conn);
            m.put("state", live && p != null ? p.state : null);
            m.put("worldName", live && p != null ? p.worldName : null);
            m.put("joinCode", (suppress || hidden) ? null : hostingJoinCode(fid));
            m.put("skin", p == null ? null : p.skin);
            m.put("muted", muted);
            m.put("blocked", blocked);
            out.add(m);
        }
        return out;
    }

    private boolean acceptRequestLocked(UUID uuid, UUID friendUuid) throws SQLException {
        boolean prevAuto = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            clearRequestLocked(uuid, friendUuid);
            linkFriendsLocked(uuid, friendUuid);
            connection.commit();
            return true;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(prevAuto);
        }
    }

    private void clearRequestLocked(UUID a, UUID b) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM friend_requests WHERE (from_uuid=? AND to_uuid=?) "
                        + "OR (from_uuid=? AND to_uuid=?)")) {
            ps.setString(1, a.toString());
            ps.setString(2, b.toString());
            ps.setString(3, b.toString());
            ps.setString(4, a.toString());
            ps.executeUpdate();
        }
    }

    private void linkFriendsLocked(UUID a, UUID b) throws SQLException {
        String[] pair = normalize(a, b);
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO friends (a, b) VALUES (?,?)")) {
            ps.setString(1, pair[0]);
            ps.setString(2, pair[1]);
            ps.executeUpdate();
        }
    }

    private boolean areFriends(UUID a, UUID b) throws SQLException {
        String[] pair = normalize(a, b);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM friends WHERE a=? AND b=?")) {
            ps.setString(1, pair[0]);
            ps.setString(2, pair[1]);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean requestExists(UUID from, UUID to) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM friend_requests WHERE from_uuid=? AND to_uuid=?")) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    void setMuted(UUID uuid, UUID target, boolean muted) {
        setRelationFlag(uuid, target, "muted", muted);
    }

    void setBlocked(UUID uuid, UUID target, boolean blocked) {
        setRelationFlag(uuid, target, "blocked", blocked);
    }

    boolean isMutedOrBlocked(UUID uuid, UUID target) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM relationships WHERE uuid=? AND target=? AND (muted=1 OR blocked=1)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, target.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw fail("isMutedOrBlocked", e);
            }
        }
    }

    boolean isBlocked(UUID uuid, UUID target) {
        synchronized (lock) {
            try {
                return isBlockedLocked(uuid, target);
            } catch (SQLException e) {
                throw fail("isBlocked", e);
            }
        }
    }

    private boolean isBlockedLocked(UUID uuid, UUID target) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM relationships WHERE uuid=? AND target=? AND blocked=1")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, target.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void setRelationFlag(UUID uuid, UUID target, String col, boolean value) {
        if (uuid.equals(target)) {
            return;
        }
        synchronized (lock) {
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT OR IGNORE INTO relationships (uuid, target, muted, blocked) VALUES (?,?,0,0)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, target.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE relationships SET " + col + "=? WHERE uuid=? AND target=?")) {
                    ps.setInt(1, value ? 1 : 0);
                    ps.setString(2, uuid.toString());
                    ps.setString(3, target.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM relationships WHERE uuid=? AND target=? AND muted=0 AND blocked=0")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, target.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                throw fail("setRelationFlag", e);
            }
        }
    }

    private static final String[] LINK_PLATFORMS =
            {"discord", "instagram", "twitter", "youtube", "twitch", "tiktok", "paypal", "kofi"};

    // Whitelisted profile_settings toggle columns — guards the dynamic column name in setModpackVisibility.
    private static final Set<String> VISIBILITY_COLUMNS = Set.of(
            "favorite_modpack_visible", "currently_playing_visible", "most_played_visible");

    boolean isLinkPlatform(String platform) {
        for (String p : LINK_PLATFORMS) {
            if (p.equals(platform)) {
                return true;
            }
        }
        return false;
    }

    // Whitelist of valid "Questions about yourself" prompt IDs. Mirror of the client catalog
    // (client.gui.ProfilePromptCatalog); the backend only needs the IDs to reject junk keys, not the
    static final int MAX_PROMPTS = 3;
    private static final String[] PROMPT_IDS =
            {"delete_block", "first_night", "build_first", "useless_item",
             "difficulty", "travel", "armor", "playstyle"};

    boolean isPromptId(String id) {
        for (String p : PROMPT_IDS) {
            if (p.equals(id)) {
                return true;
            }
        }
        return false;
    }

    Map<String, Object> profile(UUID uuid, UUID viewer) {
        synchronized (lock) {
            try {
                User u = findUser(uuid);
                if (u == null) {
                    return null;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("uuid", uuid.toString());
                m.put("username", u.username);
                m.put("friendCode", u.friendCode);
                m.put("pronouns", scalar("SELECT pronouns FROM profile_identity WHERE uuid=?", uuid));
                m.put("bio", scalar("SELECT text FROM profile_bio WHERE uuid=?", uuid));
                Map<String, Object> links = new LinkedHashMap<>();
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT discord, instagram, twitter, youtube, twitch, tiktok, paypal, kofi "
                                + "FROM profile_links WHERE uuid=?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            for (int i = 0; i < LINK_PLATFORMS.length; i++) {
                                links.put(LINK_PLATFORMS[i], rs.getString(i + 1));
                            }
                        }
                    }
                }
                m.put("links", links);

                Map<String, Object> prompts = new LinkedHashMap<>();
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT prompt_id, answer FROM profile_prompts WHERE uuid=?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            prompts.put(rs.getString(1), rs.getString(2));
                        }
                    }
                }
                m.put("prompts", prompts);

                // presence: live online state is derived in-memory; last seen is the persisted transition.
                boolean self = viewer != null && viewer.equals(uuid);
                boolean invisible = invisibleLocked(uuid);
                boolean live = isLive(connectivity(uuid));
                m.put("online", live && (self || !invisible));
                m.put("lastSeen", lastDisconnectLocked(uuid));
                if (self) {
                    m.put("invisible", invisible);
                }

                String favoriteId = null;
                boolean favoriteVisible = true;
                boolean currentlyPlayingVisible = true;
                boolean mostPlayedVisible = true;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT favorite_modpack_id, favorite_modpack_visible, currently_playing_visible, "
                                + "most_played_visible FROM profile_settings WHERE uuid=?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            favoriteId = rs.getString(1);
                            favoriteVisible = rs.getInt(2) != 0;
                            currentlyPlayingVisible = rs.getInt(3) != 0;
                            mostPlayedVisible = rs.getInt(4) != 0;
                        }
                    }
                }

                Presence pres = presences.get(uuid);
                String mp = pres == null ? null : pres.modpackId;
                boolean showPlaying = live && (self || (!invisible && currentlyPlayingVisible));
                m.put("currentlyPlaying", showPlaying ? resolveModpackLocked(mp) : null);
                m.put("favorite", (self || favoriteVisible) ? resolveModpackLocked(favoriteId) : null);

                if (self) {
                    Map<String, Object> settings = new LinkedHashMap<>();
                    settings.put("favoriteVisible", favoriteVisible);
                    settings.put("currentlyPlayingVisible", currentlyPlayingVisible);
                    settings.put("mostPlayedVisible", mostPlayedVisible);
                    m.put("settings", settings);
                }
                return m;
            } catch (SQLException e) {
                throw fail("profile", e);
            }
        }
    }

    String registeredModpackOrNull(String modpackId) {
        if (modpackId == null || modpackId.isBlank()) {
            return null;
        }
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT 1 FROM modpack_registry WHERE modpack_id=?")) {
                ps.setString(1, modpackId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? modpackId : null;
                }
            } catch (SQLException e) {
                throw fail("registeredModpackOrNull", e);
            }
        }
    }

    private Map<String, Object> resolveModpackLocked(String modpackId) throws SQLException {
        if (modpackId == null || modpackId.isBlank()) {
            return null;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name, download_url FROM modpack_registry WHERE modpack_id=?")) {
            ps.setString(1, modpackId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("modpackId", modpackId);
                m.put("name", rs.getString(1));
                m.put("downloadUrl", rs.getString(2));
                return m;
            }
        }
    }

    List<Map<String, Object>> listModpacks() {
        synchronized (lock) {
            List<Map<String, Object>> out = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT modpack_id, name, download_url FROM modpack_registry ORDER BY name");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("modpackId", rs.getString(1));
                    m.put("name", rs.getString(2));
                    m.put("downloadUrl", rs.getString(3));
                    out.add(m);
                }
                return out;
            } catch (SQLException e) {
                throw fail("listModpacks", e);
            }
        }
    }

    boolean currentlyPlayingVisible(UUID uuid) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT currently_playing_visible FROM profile_settings WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return !rs.next() || rs.getInt(1) != 0;
                }
            } catch (SQLException e) {
                throw fail("currentlyPlayingVisible", e);
            }
        }
    }

    void setFavoriteModpack(UUID uuid, String modpackId) {
        String value = (modpackId == null || modpackId.isBlank()) ? null : modpackId;
        synchronized (lock) {
            try {
                ensureSettingsRowLocked(uuid);
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE profile_settings SET favorite_modpack_id=?, updated_at=? WHERE uuid=?")) {
                    ps.setString(1, value);
                    ps.setLong(2, System.currentTimeMillis());
                    ps.setString(3, uuid.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                throw fail("setFavoriteModpack", e);
            }
        }
    }

    void setModpackVisibility(UUID uuid, String column, boolean visible) {
        if (!VISIBILITY_COLUMNS.contains(column)) {
            return;
        }
        synchronized (lock) {
            try {
                ensureSettingsRowLocked(uuid);
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE profile_settings SET " + column + "=?, updated_at=? WHERE uuid=?")) {
                    ps.setInt(1, visible ? 1 : 0);
                    ps.setLong(2, System.currentTimeMillis());
                    ps.setString(3, uuid.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                throw fail("setModpackVisibility", e);
            }
        }
    }

    private void ensureSettingsRowLocked(UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR IGNORE INTO profile_settings (uuid, updated_at) VALUES (?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    void setBio(UUID uuid, String text) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO profile_bio (uuid, text, updated_at) VALUES (?,?,?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET text=excluded.text, updated_at=excluded.updated_at")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, text == null ? "" : text);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw fail("setBio", e);
            }
        }
    }

    void setPronouns(UUID uuid, String pronouns) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO profile_identity (uuid, pronouns) VALUES (?,?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET pronouns=excluded.pronouns")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, pronouns);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw fail("setPronouns", e);
            }
        }
    }

    void setLink(UUID uuid, String platform, String value) {
        if (!isLinkPlatform(platform)) {
            return;
        }
        synchronized (lock) {
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT OR IGNORE INTO profile_links (uuid) VALUES (?)")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE profile_links SET " + platform + "=? WHERE uuid=?")) {
                    ps.setString(1, (value == null || value.isBlank()) ? null : value);
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                throw fail("setLink", e);
            }
        }
    }

    // Replace the player's whole set of prompt answers (not a patch). Blank/null answers are dropped, so
    // the resulting set is exactly the non-empty entries passed in. See PROFILES_DESIGN.md.
    void setPrompts(UUID uuid, Map<String, String> answers) {
        synchronized (lock) {
            try (PreparedStatement del = connection.prepareStatement(
                    "DELETE FROM profile_prompts WHERE uuid=?")) {
                del.setString(1, uuid.toString());
                del.executeUpdate();
                long now = System.currentTimeMillis();
                try (PreparedStatement ins = connection.prepareStatement(
                        "INSERT INTO profile_prompts (uuid, prompt_id, answer, updated_at) VALUES (?,?,?,?)")) {
                    for (Map.Entry<String, String> e : answers.entrySet()) {
                        String answer = e.getValue();
                        if (answer == null || answer.isBlank()) {
                            continue;
                        }
                        ins.setString(1, uuid.toString());
                        ins.setString(2, e.getKey());
                        ins.setString(3, answer);
                        ins.setLong(4, now);
                        ins.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                throw fail("setPrompts", e);
            }
        }
    }

    private String scalar(String sql, UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    // invites (in-memory)
    String createInvite(UUID hostUuid, String address, String worldName, boolean gated) {
        long expiresAt = System.currentTimeMillis() + 3_600_000;
        String guestAddress = address;
        if (gated && hostUuid != null) {
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

    // relay tickets (in-memory)
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
        // online -> offline edge: a presence past 2*TTL is OFFLINE; record the transition once,
        // stamping last_disconnect_at with the real last heartbeat (not "now").
        for (Map.Entry<UUID, Presence> e : presences.entrySet()) {
            Presence p = e.getValue();
            if (!p.disconnectRecorded && now - p.lastHeartbeat > 2 * ttlMs) {
                p.disconnectRecorded = true;
                p.onlinePersisted = false;
                markOffline(e.getKey(), p.lastHeartbeat);
            }
        }
    }

    // helpers
    private static final String CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ"; // no 0/1 or I/L/O/U

    private User findUser(UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT username, friend_code, domain FROM users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(uuid, rs.getString(1), rs.getString(2), rs.getString(3));
                }
                return null;
            }
        }
    }

    // caller holds lock
    private String uniqueFriendCode() throws SQLException {
        String code;
        do {
            code = "LAN-" + randomFrom(CODE_ALPHABET, 5);
        } while (friendCodeTaken(code));
        return code;
    }

    private boolean friendCodeTaken(String code) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM users WHERE friend_code=?")) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String uniqueDomain() throws SQLException {
        String domain;
        do {
            domain = WORDS[RNG.nextInt(WORDS.length)] + "-" + WORDS[RNG.nextInt(WORDS.length)] + "." + baseDomain;
        } while (domainTaken(domain));
        return domain;
    }

    private boolean domainTaken(String domain) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM users WHERE domain=?")) {
            ps.setString(1, domain);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String[] normalize(UUID a, UUID b) {
        String x = a.toString();
        String y = b.toString();
        return x.compareTo(y) <= 0 ? new String[]{x, y} : new String[]{y, x};
    }

    private static String randomFrom(String alphabet, int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(alphabet.charAt(RNG.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    private String uniqueGuestToken() {
        String token;
        do {
            token = "g" + randomHex(10);
        } while (guestTokens.containsKey(token));
        return token;
    }

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

    private static RuntimeException fail(String what, SQLException e) {
        BackendServer.log("SQLite error (" + what + "): " + e.getMessage());
        return new RuntimeException(e);
    }

    private static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}