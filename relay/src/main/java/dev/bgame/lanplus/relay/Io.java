package dev.bgame.lanplus.relay;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class Io {

    private Io() {}

    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(64);
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') {
                byte[] a = buf.toByteArray();
                int len = a.length;
                if (len > 0 && a[len - 1] == '\r') {
                    len--;
                }
                return new String(a, 0, len, StandardCharsets.UTF_8);
            }
            if (buf.size() > 16 * 1024) {
                throw new IOException("control line too long");
            }
            buf.write(b);
        }
        return buf.size() == 0 ? null : new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }
}
