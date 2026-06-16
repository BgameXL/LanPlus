package dev.bgame.lanplus.backend;

import java.net.InetSocketAddress;

final class BackendConfig {

    final InetSocketAddress bind;
    final String baseDomain;
    final String relayHost;
    final int relayPort;
    final int heartbeatTtlMs;
    final String dataFile;

    private BackendConfig(InetSocketAddress bind, String baseDomain, String relayHost, int relayPort,
                          int heartbeatTtlMs, String dataFile) {
        this.bind = bind;
        this.baseDomain = baseDomain;
        this.relayHost = relayHost;
        this.relayPort = relayPort;
        this.heartbeatTtlMs = heartbeatTtlMs;
        this.dataFile = dataFile;
    }

    static BackendConfig fromEnv() {
        return new BackendConfig(
                addr(env("LANPLUS_BACKEND_BIND", ":8080")),
                env("LANPLUS_BACKEND_BASE_DOMAIN", "lanplus.dev"),
                env("LANPLUS_BACKEND_RELAY_HOST", "relay.lanplus.dev"),
                intEnv("LANPLUS_BACKEND_RELAY_PORT", 8443),
                intEnv("LANPLUS_BACKEND_HEARTBEAT_TTL_SECONDS", 45) * 1000,
                env("LANPLUS_BACKEND_DATA_FILE", "lanplus-data.json"));
    }

    private static InetSocketAddress addr(String s) {
        int i = s.lastIndexOf(':');
        if (i < 0) {
            throw new IllegalArgumentException("expected host:port, got: " + s);
        }
        String host = s.substring(0, i);
        int port = Integer.parseInt(s.substring(i + 1).trim());
        return host.isEmpty() ? new InetSocketAddress(port) : new InetSocketAddress(host, port);
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v.trim();
    }

    private static int intEnv(String key, int def) {
        try {
            return Integer.parseInt(env(key, Integer.toString(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
