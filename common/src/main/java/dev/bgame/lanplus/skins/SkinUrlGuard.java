package dev.bgame.lanplus.skins;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches skin bytes from an untrusted URL with SSRF and size guards. Custom skin URLs are supplied
 * by other players, so the resolver (us) must not be tricked into hitting localhost / internal hosts.
 */
final class SkinUrlGuard {

    static final int MAX_BYTES = 256 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private SkinUrlGuard() {}

    static boolean isSafe(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }
        InetAddress[] addrs;
        try {
            addrs = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            return false;
        }
        if (addrs.length == 0) {
            return false;
        }
        for (InetAddress a : addrs) {
            if (!isPublic(a)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPublic(InetAddress a) {
        if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return false;
        }
        byte[] b = a.getAddress();
        // IPv6 unique-local fc00::/7 is not covered by isSiteLocalAddress.
        return !(b.length == 16 && (b[0] & 0xFE) == 0xFC);
    }

    /** Download up to MAX_BYTES, aborting while reading if the body grows past it. Null on any failure. */
    static byte[] fetch(HttpClient http, String url) {
        if (!isSafe(url)) {
            return null;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = resp.body()) {
                if (resp.statusCode() != 200) {
                    return null;
                }
                return readCapped(in);
            }
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static byte[] readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > MAX_BYTES) {
                return null;
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}