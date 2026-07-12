package dev.bgame.lanplus.backend;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
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
 * (TTL-bounded) and correct to lose on restart.
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
        volatile Object persistedSkin;
        volatile String modpackId;
        volatile String accessMode;
        volatile Set<UUID> allowedUuids = Set.of();
        volatile long lastHeartbeat;
        volatile boolean hosting;
        volatile boolean hostingAnnounced;
        volatile boolean onlinePersisted;
        volatile boolean disconnectRecorded;
    }

    record Ticket(UUID uuid, String domain, boolean requireToken, long expiresAt) {}
    record Invite(UUID hostUuid, String address, String worldName, long expiresAt) {}
    record GuestToken(String hostDomain, long expiresAt) {}
    record Session(UUID uuid, boolean verified) {}
    record AuthResult(String token, UUID uuid, boolean verified, long expiresInSeconds) {}
    enum AddResult { ACCEPTED, REQUESTED, BLOCKED }

    private static final long CHALLENGE_TTL_MS = 60_000;

    private final long ttlMs;
    private final String baseDomain;
    private final String sessionServerUrl;
    private final boolean allowOffline;
    private final long sessionTtlMs;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final Object lock = new Object();
    private final Connection connection;
    private final AssetCatalog backgrounds;
    private final AssetCatalog banners;

    private final Map<UUID, Presence> presences = new ConcurrentHashMap<>();
    private final Map<String, Invite> invites = new ConcurrentHashMap<>();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<String, GuestToken> guestTokens = new ConcurrentHashMap<>();
    private final Map<String, Long> challenges = new ConcurrentHashMap<>(); // serverId -> expiresAt

    Store(long ttlMs, String baseDomain, String dataFile,
          String sessionServerUrl, boolean allowOffline, long sessionTtlMs,
          AssetCatalog backgrounds, AssetCatalog banners) {
        this.ttlMs = ttlMs;
        this.baseDomain = baseDomain;
        this.sessionServerUrl = sessionServerUrl;
        this.allowOffline = allowOffline;
        this.sessionTtlMs = sessionTtlMs;
        this.backgrounds = backgrounds;
        this.banners = banners;
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
                st.executeUpdate("CREATE TABLE IF NOT EXISTS profile_bio ("
                        + "uuid TEXT PRIMARY KEY, text TEXT NOT NULL DEFAULT '', updated_at INTEGER NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS profile_identity ("
                        + "uuid TEXT PRIMARY KEY, pronouns TEXT)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS profile_links ("
                        + "uuid TEXT PRIMARY KEY, discord TEXT, instagram TEXT, twitter TEXT, youtube TEXT, "
                        + "twitch TEXT, tiktok TEXT, paypal TEXT, kofi TEXT)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS profile_privacy ("
                        + "uuid TEXT PRIMARY KEY, invisible_mode INTEGER NOT NULL DEFAULT 0)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS profile_prompts ("
                        + "uuid TEXT NOT NULL, prompt_id TEXT NOT NULL, answer TEXT NOT NULL, "
                        + "updated_at INTEGER NOT NULL, PRIMARY KEY (uuid, prompt_id))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS modpack_registry ("
                        + "modpack_id TEXT PRIMARY KEY, name TEXT NOT NULL, icon_url TEXT, author TEXT, "
                        + "team TEXT, download_url TEXT, description TEXT)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS profile_settings ("
                        + "uuid TEXT PRIMARY KEY, favorite_modpack_id TEXT, "
                        + "favorite_modpack_visible INTEGER NOT NULL DEFAULT 1, "
                        + "recently_played_visible INTEGER NOT NULL DEFAULT 1, "
                        + "currently_playing_visible INTEGER NOT NULL DEFAULT 1, "
                        + "bg_style TEXT, bg_color INTEGER, bg_opacity INTEGER, "
                        + "bg_image_id TEXT, banner_id TEXT, "
                        + "updated_at INTEGER NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS presence_state ("
                        + "uuid TEXT PRIMARY KEY, online INTEGER NOT NULL DEFAULT 0, last_disconnect_at INTEGER)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS xp_events ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, source TEXT NOT NULL, "
                        + "amount INTEGER NOT NULL, detail TEXT, created_at INTEGER NOT NULL)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_xp_events_uuid ON xp_events(uuid)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS advancements ("
                        + "uuid TEXT NOT NULL, advancement_id TEXT NOT NULL, earned_at INTEGER NOT NULL, "
                        + "PRIMARY KEY (uuid, advancement_id))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS advancement_xp_window ("
                        + "uuid TEXT NOT NULL, window_start INTEGER NOT NULL, count INTEGER NOT NULL DEFAULT 0, "
                        + "PRIMARY KEY (uuid, window_start))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS playtime ("
                        + "uuid TEXT NOT NULL, modpack_id TEXT NOT NULL, seconds INTEGER NOT NULL DEFAULT 0, "
                        + "updated_at INTEGER NOT NULL, PRIMARY KEY (uuid, modpack_id))");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS social_time ("
                        + "uuid TEXT PRIMARY KEY, seconds INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS user_skin ("
                        + "uuid TEXT PRIMARY KEY, skin_json TEXT NOT NULL, updated_at INTEGER NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS skin_data ("
                        + "uuid TEXT PRIMARY KEY, png BLOB NOT NULL, hash TEXT NOT NULL, model TEXT, "
                        + "updated_at INTEGER NOT NULL)");
                st.executeUpdate("CREATE TABLE IF NOT EXISTS sessions ("
                        + "token_hash TEXT PRIMARY KEY, uuid TEXT NOT NULL, verified INTEGER NOT NULL DEFAULT 0, "
                        + "created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_sessions_uuid ON sessions(uuid)");
                if (columnExists(st, "users", "last_seen")) {
                    st.executeUpdate("ALTER TABLE users DROP COLUMN last_seen");
                }
                if (columnExists(st, "profile_settings", "most_played_visible")) {
                    st.executeUpdate("ALTER TABLE profile_settings "
                            + "RENAME COLUMN most_played_visible TO recently_played_visible");
                }
                if (!columnExists(st, "profile_settings", "bg_style")) {
                    st.executeUpdate("ALTER TABLE profile_settings ADD COLUMN bg_style TEXT");
                    st.executeUpdate("ALTER TABLE profile_settings ADD COLUMN bg_color INTEGER");
                    st.executeUpdate("ALTER TABLE profile_settings ADD COLUMN bg_opacity INTEGER");
                }
                if (!columnExists(st, "profile_settings", "bg_image_id")) {
                    st.executeUpdate("ALTER TABLE profile_settings ADD COLUMN bg_image_id TEXT");
                    st.executeUpdate("ALTER TABLE profile_settings ADD COLUMN banner_id TEXT");
                }
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
                           String address, String joinCode, Object skin, String modpackId,
                           String accessMode, Set<UUID> allowedUuids) {
        ensureUser(uuid, username);
        Presence p = presences.computeIfAbsent(uuid, k -> new Presence());
        long now = System.currentTimeMillis();
        long prevHeartbeat = p.lastHeartbeat;
        String prevModpackId = p.modpackId;
        boolean wasOnline = p.onlinePersisted;
        p.state = state;
        p.worldName = worldName;
        p.address = address;
        p.joinCode = joinCode;
        p.skin = skin;
        p.modpackId = modpackId;
        p.accessMode = accessMode;
        p.allowedUuids = allowedUuids == null ? Set.of() : allowedUuids;
        p.lastHeartbeat = now;
        p.hosting = "HOSTING".equals(state);
        if (!p.onlinePersisted) {
            p.onlinePersisted = true;
            p.disconnectRecorded = false;
            markOnline(uuid);
        }
        if (wasOnline && prevHeartbeat > 0) {
            long delta = now - prevHeartbeat;
            if (delta > 0 && delta <= 2 * ttlMs) {
                long elapsedSeconds = Math.round(delta / 1000.0);
                if (prevModpackId != null) {
                    addPlaytime(uuid, prevModpackId, elapsedSeconds);
                }
                if (anyFriendOnline(uuid)) {
                    addSocialTime(uuid, elapsedSeconds);
                }
            }
        }
        if (modpackId != null && !modpackId.isBlank() && !modpackId.equals(prevModpackId)) {
            setLastModpack(uuid, modpackId);
        }
        if (skin != null && !skin.equals(p.persistedSkin)) {
            p.persistedSkin = skin;
            saveSkinRef(uuid, Json.write(skin), now);
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

    boolean joinCodeVisibleTo(UUID host, UUID viewer) {
        Presence p = presences.get(host);
        if (p == null || !"INVITED".equals(p.accessMode)) {
            return true;
        }
        return viewer != null && p.allowedUuids.contains(viewer);
    }

    private static boolean isLive(String connectivity) {
        return "ONLINE".equals(connectivity) || "STALE".equals(connectivity);
    }

    private void saveSkinRef(UUID uuid, String skinJson, long now) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO user_skin (uuid, skin_json, updated_at) VALUES (?,?,?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET skin_json=excluded.skin_json, "
                            + "updated_at=excluded.updated_at")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, skinJson);
                ps.setLong(3, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw fail("saveSkinRef", e);
            }
        }
    }

    private Object persistedSkinLocked(UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT skin_json FROM user_skin WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Json.parse(rs.getString(1)) : null;
            }
        }
    }

    // hosted skins: the one deliberate content exception — a user-uploaded, validated, capped PNG.
    void putHostedSkin(UUID uuid, byte[] png, String hash, String model) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO skin_data (uuid, png, hash, model, updated_at) VALUES (?,?,?,?,?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET png=excluded.png, hash=excluded.hash, "
                            + "model=excluded.model, updated_at=excluded.updated_at")) {
                ps.setString(1, uuid.toString());
                ps.setBytes(2, png);
                ps.setString(3, hash);
                ps.setString(4, model);
                ps.setLong(5, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw fail("putHostedSkin", e);
            }
        }
    }

    void deleteHostedSkin(UUID uuid) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM skin_data WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw fail("deleteHostedSkin", e);
            }
        }
    }

    byte[] hostedSkinPng(UUID uuid) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT png FROM skin_data WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getBytes(1) : null;
                }
            } catch (SQLException e) {
                throw fail("hostedSkinPng", e);
            }
        }
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

    // profile privacy
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
        Map<UUID, Integer> tiers = new HashMap<>();
        Map<UUID, Object> persistedSkins = new HashMap<>();
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
                for (UUID fid : names.keySet()) {
                    tiers.put(fid, tierFor(totalXpLocked(fid)));
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
                List<UUID> needPersisted = new ArrayList<>();
                for (UUID fid : names.keySet()) {
                    Presence p = presences.get(fid);
                    if (p == null || p.skin == null) {
                        needPersisted.add(fid);
                    }
                }
                if (!needPersisted.isEmpty()) {
                    String placeholders = String.join(",", java.util.Collections.nCopies(needPersisted.size(), "?"));
                    try (PreparedStatement ps = connection.prepareStatement(
                            "SELECT uuid, skin_json FROM user_skin WHERE uuid IN (" + placeholders + ")")) {
                        for (int i = 0; i < needPersisted.size(); i++) {
                            ps.setString(i + 1, needPersisted.get(i).toString());
                        }
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                Object persisted = Json.parse(rs.getString(2));
                                if (persisted != null) {
                                    persistedSkins.put(UUID.fromString(rs.getString(1)), persisted);
                                }
                            }
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
            String conn = suppress ? "UNKNOWN" : (hidden ? "OFFLINE" : connectivity(fid));
            boolean live = !hidden && ("ONLINE".equals(conn) || "STALE".equals(conn));
            Presence p = presences.get(fid);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("uuid", fid.toString());
            m.put("username", e.getValue());
            m.put("connectivity", conn);
            m.put("state", live && p != null ? p.state : null);
            m.put("worldName", live && p != null ? p.worldName : null);
            m.put("joinCode", (suppress || hidden || !joinCodeVisibleTo(fid, uuid)) ? null : hostingJoinCode(fid));
            m.put("skin", p != null && p.skin != null ? p.skin : persistedSkins.get(fid));
            m.put("muted", muted);
            m.put("blocked", blocked);
            m.put("tier", tiers.getOrDefault(fid, 0));
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

    private static final Set<String> VISIBILITY_COLUMNS = Set.of(
            "favorite_modpack_visible", "currently_playing_visible", "recently_played_visible");

    private static final Set<String> BACKGROUND_STYLES = Set.of("DARK", "SOLID", "MINECRAFT", "IMAGE");
    static final String DEFAULT_BG_STYLE = "DARK";
    static final int DEFAULT_BG_COLOR = 0x0A0C10;
    static final int DEFAULT_BG_OPACITY = 92;

    boolean isBackgroundStyle(String style) {
        return style != null && BACKGROUND_STYLES.contains(style);
    }

    boolean isLinkPlatform(String platform) {
        for (String p : LINK_PLATFORMS) {
            if (p.equals(platform)) {
                return true;
            }
        }
        return false;
    }

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

    // progression
    private static final long[] TIER_THRESHOLDS = {150, 450, 1000, 2000};
    private static final int XP_PER_ADVANCEMENT = 1;
    private static final long XP_WINDOW_MS = 5 * 60 * 1000L;
    private static final int XP_WINDOW_MAX = 10;
    private static final long SECONDS_PER_PLAYTIME_XP = 600;
    private static final long SECONDS_PER_SOCIAL_XP = 300;

    static int tierFor(long xp) {
        int tier = 0;
        for (long threshold : TIER_THRESHOLDS) {
            if (xp >= threshold) {
                tier++;
            }
        }
        return tier;
    }

    boolean recordAdvancement(UUID uuid, String advancementId) {
        synchronized (lock) {
            try {
                long now = System.currentTimeMillis();
                int changed;
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT OR IGNORE INTO advancements (uuid, advancement_id, earned_at) VALUES (?,?,?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, advancementId);
                    ps.setLong(3, now);
                    changed = ps.executeUpdate();
                }
                if (changed == 0) {
                    return false;
                }
                long windowStart = now - (now % XP_WINDOW_MS);
                int count;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT count FROM advancement_xp_window WHERE uuid=? AND window_start=?")) {
                    ps.setString(1, uuid.toString());
                    ps.setLong(2, windowStart);
                    try (ResultSet rs = ps.executeQuery()) {
                        count = rs.next() ? rs.getInt(1) : 0;
                    }
                }
                if (count >= XP_WINDOW_MAX) {
                    return false;
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO advancement_xp_window (uuid, window_start, count) VALUES (?,?,1) "
                                + "ON CONFLICT(uuid, window_start) DO UPDATE SET count=count+1")) {
                    ps.setString(1, uuid.toString());
                    ps.setLong(2, windowStart);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO xp_events (uuid, source, amount, detail, created_at) VALUES (?,?,?,?,?)")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, "advancement");
                    ps.setInt(3, XP_PER_ADVANCEMENT);
                    ps.setString(4, advancementId);
                    ps.setLong(5, now);
                    ps.executeUpdate();
                }
                return true;
            } catch (SQLException e) {
                throw fail("recordAdvancement", e);
            }
        }
    }

    private void creditCumulativeXpLocked(UUID uuid, String source, long totalUnits, long unitsPerXp, long now)
            throws SQLException {
        long target = totalUnits / unitsPerXp;
        if (target <= 0) {
            return;
        }
        long awarded;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(SUM(amount), 0) FROM xp_events WHERE uuid=? AND source=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, source);
            try (ResultSet rs = ps.executeQuery()) {
                awarded = rs.next() ? rs.getLong(1) : 0;
            }
        }
        long delta = target - awarded;
        if (delta <= 0) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO xp_events (uuid, source, amount, detail, created_at) VALUES (?,?,?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, source);
            ps.setLong(3, delta);
            ps.setString(4, null);
            ps.setLong(5, now);
            ps.executeUpdate();
        }
    }

    private long totalXpLocked(UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(SUM(amount), 0) FROM xp_events WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private int advancementCountLocked(UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM advancements WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private Map<String, Object> xpBySourceLocked(UUID uuid) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("advancement", 0);
        m.put("playtime", 0);
        m.put("social", 0);
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT source, COALESCE(SUM(amount), 0) FROM xp_events WHERE uuid=? GROUP BY source")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    m.put(rs.getString(1), rs.getInt(2));
                }
            }
        }
        return m;
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
                Presence skinPres = presences.get(uuid);
                m.put("skin", skinPres != null && skinPres.skin != null
                        ? skinPres.skin : persistedSkinLocked(uuid));
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
                boolean recentlyPlayedVisible = true;
                String bgStyle = DEFAULT_BG_STYLE;
                int bgColor = DEFAULT_BG_COLOR;
                int bgOpacity = DEFAULT_BG_OPACITY;
                String bgImageId = null;
                String bannerId = null;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT favorite_modpack_id, favorite_modpack_visible, currently_playing_visible, "
                                + "recently_played_visible, bg_style, bg_color, bg_opacity, "
                                + "bg_image_id, banner_id "
                                + "FROM profile_settings WHERE uuid=?")) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            favoriteId = rs.getString(1);
                            favoriteVisible = rs.getInt(2) != 0;
                            currentlyPlayingVisible = rs.getInt(3) != 0;
                            recentlyPlayedVisible = rs.getInt(4) != 0;
                            String style = rs.getString(5);
                            if (isBackgroundStyle(style)) {
                                bgStyle = style;
                            }
                            int color = rs.getInt(6);
                            if (!rs.wasNull()) {
                                bgColor = color & 0xFFFFFF;
                            }
                            int opacity = rs.getInt(7);
                            if (!rs.wasNull()) {
                                bgOpacity = Math.max(0, Math.min(100, opacity));
                            }
                            bgImageId = rs.getString(8);
                            bannerId = rs.getString(9);
                        }
                    }
                }

                Presence pres = presences.get(uuid);
                String mp = pres == null ? null : pres.modpackId;
                boolean showPlaying = live && (self || (!invisible && currentlyPlayingVisible));
                m.put("currentlyPlaying", showPlaying ? resolveModpackLocked(mp) : null);
                boolean showLastPlayed = self || (!invisible && recentlyPlayedVisible);
                m.put("lastPlayed", showLastPlayed ? resolveModpackLocked(lastModpackLocked(uuid)) : null);
                m.put("favorite", (self || favoriteVisible) ? resolveModpackLocked(favoriteId) : null);
                m.put("recentlyPlayed", (self || recentlyPlayedVisible) ? recentlyPlayedLocked(uuid) : null);
                String bgImageHash = bgImageId == null || backgrounds == null ? null : backgrounds.hash(bgImageId);
                if (bgImageHash == null) {
                    bgImageId = null;
                    if ("IMAGE".equals(bgStyle)) {
                        bgStyle = DEFAULT_BG_STYLE;
                    }
                }
                Map<String, Object> background = new LinkedHashMap<>();
                background.put("style", bgStyle);
                background.put("color", bgColor);
                background.put("opacity", bgOpacity);
                background.put("imageId", bgImageId);
                background.put("image", bgImageId == null ? null : backgrounds.url(bgImageId));
                background.put("imageHash", bgImageHash);
                m.put("background", background);

                String bannerHash = bannerId == null || banners == null ? null : banners.hash(bannerId);
                if (bannerHash == null) {
                    m.put("banner", null);
                } else {
                    Map<String, Object> banner = new LinkedHashMap<>();
                    banner.put("id", bannerId);
                    banner.put("url", banners.url(bannerId));
                    banner.put("hash", bannerHash);
                    m.put("banner", banner);
                }

                if (self) {
                    Map<String, Object> settings = new LinkedHashMap<>();
                    settings.put("favoriteVisible", favoriteVisible);
                    settings.put("currentlyPlayingVisible", currentlyPlayingVisible);
                    settings.put("recentlyPlayedVisible", recentlyPlayedVisible);
                    m.put("settings", settings);
                }

                long xp = totalXpLocked(uuid);
                Map<String, Object> progression = new LinkedHashMap<>();
                progression.put("tier", tierFor(xp));
                progression.put("advancements", advancementCountLocked(uuid));
                if (self) {
                    progression.put("xp", xp);
                    progression.put("sources", xpBySourceLocked(uuid));
                }
                m.put("progression", progression);
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

    void addPlaytime(UUID uuid, String modpackId, long seconds) {
        if (modpackId == null || modpackId.isBlank() || seconds <= 0) {
            return;
        }
        synchronized (lock) {
            try {
                long now = System.currentTimeMillis();
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO playtime (uuid, modpack_id, seconds, updated_at) "
                                + "SELECT ?, ?, ?, ? WHERE EXISTS (SELECT 1 FROM modpack_registry WHERE modpack_id=?) "
                                + "ON CONFLICT(uuid, modpack_id) DO UPDATE SET "
                                + "seconds=seconds+excluded.seconds, updated_at=excluded.updated_at")) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, modpackId);
                    ps.setLong(3, seconds);
                    ps.setLong(4, now);
                    ps.setString(5, modpackId);
                    ps.executeUpdate();
                }
                long totalPlay = scalarLongLocked(
                        "SELECT COALESCE(SUM(seconds), 0) FROM playtime WHERE uuid=?", uuid);
                creditCumulativeXpLocked(uuid, "playtime", totalPlay, SECONDS_PER_PLAYTIME_XP, now);
            } catch (SQLException e) {
                throw fail("addPlaytime", e);
            }
        }
    }

    void setLastModpack(UUID uuid, String modpackId) {
        if (modpackId == null || modpackId.isBlank()) {
            return;
        }
        String value = modpackId.length() > 100 ? modpackId.substring(0, 100) : modpackId;
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE users SET last_modpack=? WHERE uuid=?")) {
                ps.setString(1, value);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw fail("setLastModpack", e);
            }
        }
    }

    void addSocialTime(UUID uuid, long seconds) {
        if (seconds <= 0) {
            return;
        }
        synchronized (lock) {
            try {
                long now = System.currentTimeMillis();
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO social_time (uuid, seconds, updated_at) VALUES (?,?,?) "
                                + "ON CONFLICT(uuid) DO UPDATE SET "
                                + "seconds=seconds+excluded.seconds, updated_at=excluded.updated_at")) {
                    ps.setString(1, uuid.toString());
                    ps.setLong(2, seconds);
                    ps.setLong(3, now);
                    ps.executeUpdate();
                }
                long totalSocial = scalarLongLocked(
                        "SELECT COALESCE(seconds, 0) FROM social_time WHERE uuid=?", uuid);
                creditCumulativeXpLocked(uuid, "social", totalSocial, SECONDS_PER_SOCIAL_XP, now);
            } catch (SQLException e) {
                throw fail("addSocialTime", e);
            }
        }
    }

    private String lastModpackLocked(UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT last_modpack FROM users WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private long scalarLongLocked(String sql, UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private boolean anyFriendOnline(UUID uuid) {
        for (UUID fid : friendsOf(uuid)) {
            String c = connectivity(fid);
            if ("ONLINE".equals(c) || "STALE".equals(c)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> recentlyPlayedLocked(UUID uuid) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT r.modpack_id, r.name, r.download_url FROM playtime p "
                        + "JOIN modpack_registry r ON r.modpack_id = p.modpack_id "
                        + "WHERE p.uuid=? ORDER BY p.updated_at DESC LIMIT 1")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("modpackId", rs.getString(1));
                m.put("name", rs.getString(2));
                m.put("downloadUrl", rs.getString(3));
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

    void setBackground(UUID uuid, String style, int color, int opacity) {
        String s = isBackgroundStyle(style) ? style : DEFAULT_BG_STYLE;
        int c = color & 0xFFFFFF;
        int o = Math.max(0, Math.min(100, opacity));
        synchronized (lock) {
            try {
                ensureSettingsRowLocked(uuid);
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE profile_settings SET bg_style=?, bg_color=?, bg_opacity=?, updated_at=? "
                                + "WHERE uuid=?")) {
                    ps.setString(1, s);
                    ps.setInt(2, c);
                    ps.setInt(3, o);
                    ps.setLong(4, System.currentTimeMillis());
                    ps.setString(5, uuid.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                throw fail("setBackground", e);
            }
        }
    }

    void setBackgroundImage(UUID uuid, String imageId) {
        setSettingsText(uuid, "bg_image_id", imageId, "setBackgroundImage");
    }

    void setBanner(UUID uuid, String bannerId) {
        setSettingsText(uuid, "banner_id", bannerId, "setBanner");
    }

    private void setSettingsText(UUID uuid, String column, String value, String op) {
        String v = value == null || value.isBlank() ? null : value;
        synchronized (lock) {
            try {
                ensureSettingsRowLocked(uuid);
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE profile_settings SET " + column + "=?, updated_at=? WHERE uuid=?")) {
                    ps.setString(1, v);
                    ps.setLong(2, System.currentTimeMillis());
                    ps.setString(3, uuid.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                throw fail(op, e);
            }
        }
    }

    String backgroundImageId(UUID uuid) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT bg_image_id FROM profile_settings WHERE uuid=?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getString(1) : null;
                }
            } catch (SQLException e) {
                throw fail("backgroundImageId", e);
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

    // Replace the player's whole set of prompt answers (not a patch). Blank/null answers are dropped.
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

    // auth / sessions
    boolean isOfflineAllowed() {
        return allowOffline;
    }

    String newChallenge() {
        String serverId = randomHex(20);
        challenges.put(serverId, System.currentTimeMillis() + CHALLENGE_TTL_MS);
        return serverId;
    }

    AuthResult authVerify(String username, String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return null;
        }
        Long expiry = challenges.remove(serverId);
        if (expiry == null || System.currentTimeMillis() > expiry) {
            return null;
        }
        UUID uuid = mojangHasJoined(username, serverId);
        if (uuid == null) {
            return null;
        }
        ensureUser(uuid, username);
        return issueSession(uuid, true);
    }

    AuthResult authOffline(String username) {
        UUID uuid = offlineUuid(username);
        ensureUser(uuid, username);
        return issueSession(uuid, false);
    }

    static UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    Session validateSession(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String hash = sha256Hex(token);
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT uuid, verified, expires_at FROM sessions WHERE token_hash=?")) {
                ps.setString(1, hash);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || System.currentTimeMillis() > rs.getLong(3)) {
                        return null;
                    }
                    return new Session(UUID.fromString(rs.getString(1)), rs.getInt(2) != 0);
                }
            } catch (SQLException e) {
                throw fail("validateSession", e);
            }
        }
    }

    private AuthResult issueSession(UUID uuid, boolean verified) {
        String token = randomHex(32);
        long now = System.currentTimeMillis();
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sessions (token_hash, uuid, verified, created_at, expires_at) VALUES (?,?,?,?,?)")) {
                ps.setString(1, sha256Hex(token));
                ps.setString(2, uuid.toString());
                ps.setInt(3, verified ? 1 : 0);
                ps.setLong(4, now);
                ps.setLong(5, now + sessionTtlMs);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw fail("issueSession", e);
            }
        }
        return new AuthResult(token, uuid, verified, sessionTtlMs / 1000);
    }

    private UUID mojangHasJoined(String username, String serverId) {
        if (username == null || username.isBlank()) {
            return null;
        }
        try {
            String url = sessionServerUrl + "/session/minecraft/hasJoined?username="
                    + URLEncoder.encode(username, StandardCharsets.UTF_8)
                    + "&serverId=" + URLEncoder.encode(serverId, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return null;
            }
            Object id = Json.parseObject(resp.body()).get("id");
            return id instanceof String s ? uuidFromUndashed(s) : null;
        } catch (Exception e) {
            BackendServer.log("hasJoined check failed: " + e);
            return null;
        }
    }

    private static UUID uuidFromUndashed(String s) {
        if (s == null || s.length() != 32) {
            return null;
        }
        try {
            return UUID.fromString(s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16)
                    + "-" + s.substring(16, 20) + "-" + s.substring(20, 32));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String sha256Hex(String s) {
        return sha256Hex(s.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256Hex(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    void sweep() {
        long now = System.currentTimeMillis();
        invites.entrySet().removeIf(e -> now > e.getValue().expiresAt());
        tickets.entrySet().removeIf(e -> now > e.getValue().expiresAt());
        guestTokens.entrySet().removeIf(e -> now > e.getValue().expiresAt());
        challenges.entrySet().removeIf(e -> now > e.getValue());
        sweepSessions(now);
        for (Map.Entry<UUID, Presence> e : presences.entrySet()) {
            Presence p = e.getValue();
            if (!p.disconnectRecorded && now - p.lastHeartbeat > 2 * ttlMs) {
                p.disconnectRecorded = true;
                p.onlinePersisted = false;
                markOffline(e.getKey(), p.lastHeartbeat);
            }
        }
    }

    private void sweepSessions(long now) {
        synchronized (lock) {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM sessions WHERE expires_at < ?")) {
                ps.setLong(1, now);
                ps.executeUpdate();
            } catch (SQLException e) {
                BackendServer.log("SQLite error (sweepSessions): " + e.getMessage());
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