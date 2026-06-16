package dev.bgame.lanplus.relay;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

final class RelayConfig {

    final InetSocketAddress relayBind;
    final InetSocketAddress mcBind;
    final boolean tls;
    final String certPath;
    final String keyPath;
    final boolean noAuth;
    final String backendUrl;
    final String baseDomain;
    final int mcRatePerMin;

    private RelayConfig(InetSocketAddress relayBind, InetSocketAddress mcBind, boolean tls,
                        String certPath, String keyPath, boolean noAuth, String backendUrl,
                        String baseDomain, int mcRatePerMin) {
        this.relayBind = relayBind;
        this.mcBind = mcBind;
        this.tls = tls;
        this.certPath = certPath;
        this.keyPath = keyPath;
        this.noAuth = noAuth;
        this.backendUrl = backendUrl;
        this.baseDomain = baseDomain;
        this.mcRatePerMin = mcRatePerMin;
    }

    static RelayConfig fromEnv() {
        boolean noAuth = bool("LANPLUS_RELAY_NO_AUTH", false);
        boolean tls = bool("LANPLUS_RELAY_TLS", !noAuth);
        String cert = env("LANPLUS_RELAY_CERT", "");
        String key = env("LANPLUS_RELAY_KEY", "");
        if (tls && (cert.isEmpty() || key.isEmpty())) {
            throw new IllegalStateException(
                    "TLS enabled but LANPLUS_RELAY_CERT/LANPLUS_RELAY_KEY are not set "
                            + "(set them, or LANPLUS_RELAY_TLS=false for local dev)");
        }
        if (tls) {
            requireReadable("LANPLUS_RELAY_CERT", cert);
            requireReadable("LANPLUS_RELAY_KEY", key);
        }
        String backend = stripTrailingSlash(env("LANPLUS_RELAY_BACKEND_URL", ""));
        if (!noAuth && backend.isEmpty()) {
            throw new IllegalStateException(
                    "LANPLUS_RELAY_BACKEND_URL is required unless LANPLUS_RELAY_NO_AUTH=true");
        }
        return new RelayConfig(
                addr(env("LANPLUS_RELAY_BIND", ":8443")),
                addr(env("LANPLUS_RELAY_MC_BIND", ":25565")),
                tls, cert, key, noAuth, backend,
                env("LANPLUS_RELAY_BASE_DOMAIN", "lanplus.local"),
                intEnv("LANPLUS_RELAY_MC_RATE_PER_MIN", 30));
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

    private static boolean bool(String key, boolean def) {
        String v = System.getenv(key);
        return v == null ? def : v.equalsIgnoreCase("true") || v.equals("1");
    }

    private static int intEnv(String key, int def) {
        try {
            return Integer.parseInt(env(key, Integer.toString(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void requireReadable(String envKey, String path) {
        Path p = Path.of(path);
        if (!Files.isReadable(p)) {
            throw new IllegalStateException(
                    envKey + " points to a missing or unreadable file: " + path
                            + " (provide a valid PEM file, or set LANPLUS_RELAY_TLS=false "
                            + "for a local plaintext relay)");
        }
    }

    private static String stripTrailingSlash(String s) {
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
