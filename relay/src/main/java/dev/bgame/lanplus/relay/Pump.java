package dev.bgame.lanplus.relay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

final class Pump {

    private Pump() {}

    /** Copies {@code a↔b} until either side ends, closes both, then returns. Blocks the caller. */
    static void bidirectional(Socket a, Socket b, ExecutorService pool) {
        Future<?> other = pool.submit(() -> copy(a, b));
        copy(b, a);
        other.cancel(true);
        closeQuietly(a);
        closeQuietly(b);
    }

    private static void copy(Socket from, Socket to) {
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                out.flush();
            }
        } catch (IOException ignored) {
        } finally {
            try {
                to.shutdownOutput();
            } catch (IOException ignored) {
            }
        }
    }

    static void closeQuietly(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // nothing to do
            }
        }
    }
}
