package dev.bgame.lanplus.backend;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

final class Http {

    private Http() {}

    record Request(String method, String path, String query, Map<String, String> headers, String body) {

        boolean isWebSocketUpgrade() {
            String upgrade = headers.getOrDefault("upgrade", "");
            return upgrade.toLowerCase().contains("websocket");
        }

        String param(String name) {
            if (query == null) {
                return null;
            }
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                String key = eq < 0 ? pair : pair.substring(0, eq);
                if (key.equals(name)) {
                    String raw = eq < 0 ? "" : pair.substring(eq + 1);
                    return URLDecoder.decode(raw, StandardCharsets.UTF_8);
                }
            }
            return null;
        }
    }

    static Request read(InputStream in) throws IOException {
        String requestLine = readLine(in);
        if (requestLine == null || requestLine.isBlank()) {
            return null;
        }
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            return null;
        }
        String method = parts[0];
        String target = parts[1];
        String path = target;
        String query = null;
        int q = target.indexOf('?');
        if (q >= 0) {
            path = target.substring(0, q);
            query = target.substring(q + 1);
        }

        Map<String, String> headers = new HashMap<>();
        String line;
        while ((line = readLine(in)) != null && !line.isEmpty()) {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(),
                        line.substring(colon + 1).trim());
            }
        }

        String body = "";
        String len = headers.get("content-length");
        if (len != null) {
            int n = Integer.parseInt(len.trim());
            byte[] buf = in.readNBytes(n);
            body = new String(buf, StandardCharsets.UTF_8);
        }
        return new Request(method, path, query, headers, body);
    }

    static void writeJson(OutputStream out, int status, Object body) throws IOException {
        byte[] payload = (body == null ? "" : Json.write(body)).getBytes(StandardCharsets.UTF_8);
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n");
        head.append("Content-Type: application/json\r\n");
        head.append("Content-Length: ").append(payload.length).append("\r\n");
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.UTF_8));
        out.write(payload);
        out.flush();
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(128);
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                byte[] a = buf.toByteArray();
                int n = a.length;
                if (n > 0 && a[n - 1] == '\r') {
                    n--;
                }
                return new String(a, 0, n, StandardCharsets.UTF_8);
            }
            if (buf.size() > 64 * 1024) {
                throw new IOException("header line too long");
            }
            buf.write(b);
        }
        return buf.size() == 0 ? null : new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            default -> "OK";
        };
    }
}
