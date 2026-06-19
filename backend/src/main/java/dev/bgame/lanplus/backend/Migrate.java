package dev.bgame.lanplus.backend;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

public final class Migrate {

    private Migrate() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: Migrate <lanplus-data.json> <lanplus.db>");
            System.exit(2);
            return;
        }
        Path jsonPath = Path.of(args[0]);
        String dbPath = args[1];
        if (!Files.isReadable(jsonPath)) {
            System.err.println("cannot read JSON file: " + jsonPath);
            System.exit(1);
            return;
        }

        Map<String, Object> root = Json.parseObject(Files.readString(jsonPath, StandardCharsets.UTF_8));

        Class.forName("org.sqlite.JDBC");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            c.setAutoCommit(false);
            createSchema(c);
            int users = insertUsers(c, root.get("users"));
            int friends = insertFriends(c, root.get("friends"));
            int requests = insertRequests(c, root.get("requests"));
            c.commit();
            System.out.println("migrated -> " + dbPath + ": "
                    + users + " users, " + friends + " friend pair(s), " + requests + " pending request(s)");
            verify(c);
        }
    }

    private static void createSchema(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS users ("
                    + "uuid TEXT PRIMARY KEY, username TEXT, friend_code TEXT UNIQUE NOT NULL, "
                    + "domain TEXT UNIQUE NOT NULL, last_seen INTEGER, last_modpack TEXT)");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS friends ("
                    + "a TEXT NOT NULL, b TEXT NOT NULL, PRIMARY KEY (a, b), CHECK (a < b))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS friend_requests ("
                    + "from_uuid TEXT NOT NULL, to_uuid TEXT NOT NULL, PRIMARY KEY (from_uuid, to_uuid))");
            st.executeUpdate("CREATE TABLE IF NOT EXISTS relationships ("
                    + "uuid TEXT NOT NULL, target TEXT NOT NULL, muted INTEGER NOT NULL DEFAULT 0, "
                    + "blocked INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (uuid, target))");
        }
    }

    private static int insertUsers(Connection c, Object node) throws SQLException {
        if (!(node instanceof List<?> list)) {
            return 0;
        }
        int n = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO users (uuid, username, friend_code, domain) VALUES (?,?,?,?)")) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) {
                    continue;
                }
                ps.setString(1, str(m.get("uuid")));
                ps.setString(2, m.get("username") == null ? null : str(m.get("username")));
                ps.setString(3, str(m.get("friendCode")));
                ps.setString(4, str(m.get("domain")));
                n += ps.executeUpdate();
            }
        }
        return n;
    }

    private static int insertFriends(Connection c, Object node) throws SQLException {
        if (!(node instanceof Map<?, ?> m)) {
            return 0;
        }
        int n = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO friends (a, b) VALUES (?,?)")) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String key = str(e.getKey());
                if (!(e.getValue() instanceof List<?> list)) {
                    continue;
                }
                for (Object o : list) {
                    String other = str(o);
                    String lo = key;
                    String hi = other;
                    if (lo.compareTo(hi) > 0) {
                        String t = lo;
                        lo = hi;
                        hi = t;
                    }
                    ps.setString(1, lo);
                    ps.setString(2, hi);
                    n += ps.executeUpdate();
                }
            }
        }
        return n;
    }

    private static int insertRequests(Connection c, Object node) throws SQLException {
        if (!(node instanceof Map<?, ?> m)) {
            return 0;
        }
        int n = 0;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO friend_requests (from_uuid, to_uuid) VALUES (?,?)")) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String recipient = str(e.getKey());
                if (!(e.getValue() instanceof List<?> list)) {
                    continue;
                }
                for (Object o : list) {
                    ps.setString(1, str(o)); // requester
                    ps.setString(2, recipient);
                    n += ps.executeUpdate();
                }
            }
        }
        return n;
    }

    private static void verify(Connection c) throws SQLException {
        System.out.println("verify (rows in db): "
                + "users=" + count(c, "users")
                + ", friends=" + count(c, "friends")
                + ", friend_requests=" + count(c, "friend_requests"));
    }

    private static int count(Connection c, String table) throws SQLException {
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
