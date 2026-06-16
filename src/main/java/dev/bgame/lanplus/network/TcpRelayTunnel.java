package dev.bgame.lanplus.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.api.RelayTicket;
import org.slf4j.Logger;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class TcpRelayTunnel implements RelayTunnel {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    private final boolean plaintext;
    private final ExecutorService pool = Executors.newCachedThreadPool(daemon("lanplus-relay"));

    private volatile Socket control;
    private volatile int localPort;
    private volatile boolean open;

    public TcpRelayTunnel(boolean plaintext) {
        this.plaintext = plaintext;
    }

    @Override
    public CompletableFuture<String> open(int localPort, RelayTicket ticket) {
        close();
        if (ticket == null || ticket.relayHost() == null || ticket.relayHost().isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        this.localPort = localPort;
        CompletableFuture<String> result = new CompletableFuture<>();
        pool.execute(() -> runControl(ticket, result));
        return result;
    }

    @Override
    public void close() {
        open = false;
        Socket c = control;
        control = null;
        closeQuietly(c);
    }

    @Override
    public boolean isOpen() {
        Socket c = control;
        return open && c != null && !c.isClosed();
    }

    // --- control connection -----------------------------------------------------------------------
    private void runControl(RelayTicket ticket, CompletableFuture<String> result) {
        try {
            Socket c = connect(ticket.relayHost(), ticket.relayPort());
            this.control = c;
            OutputStream out = c.getOutputStream();
            writeLine(out, GSON.toJson(Map.of("type", "HELLO", "ticket", nullToEmpty(ticket.ticket()))));

            BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            JsonObject first = parse(reader.readLine());
            if (first == null || !"ASSIGNED".equals(str(first, "type"))) {
                LOGGER.warn("LAN+ relay rejected tunnel: {}", first == null ? "no response" : str(first, "reason"));
                closeQuietly(c);
                result.complete(null);
                return;
            }
            String domain = str(first, "domain");
            this.open = true;
            result.complete(domain);
            LOGGER.info("LAN+ relay tunnel open: {}", domain);

            String line;
            while ((line = reader.readLine()) != null) {
                JsonObject msg = parse(line);
                if (msg == null) {
                    continue;
                }
                switch (str(msg, "type")) {
                    case "SESSION" -> {
                        String id = str(msg, "id");
                        pool.execute(() -> proxySession(ticket, id));
                    }
                    case "PING" -> writeLine(out, GSON.toJson(Map.of("type", "PONG")));
                    default -> { /* ignored */ }
                }
            }
        } catch (IOException e) {
            LOGGER.debug("LAN+ relay tunnel closed: {}", e.toString());
            result.complete(null); // no-op if already completed
        } finally {
            open = false;
        }
    }

    // --- per-player data connection ---------------------------------------------------------------

    private void proxySession(RelayTicket ticket, String id) {
        Socket data = null;
        Socket local = null;

        try {

            LOGGER.info("SESSION {}", id);
            LOGGER.info("Connecting local minecraft on {}", localPort);
            LOGGER.info("Connecting to relay {}", ticket.relayHost());

            data = connect(ticket.relayHost(), ticket.relayPort());
            writeLine(data.getOutputStream(), GSON.toJson(Map.of("type", "DATA", "id", id)));
            local = new Socket();
            local.connect(new InetSocketAddress("127.0.0.1", localPort), CONNECT_TIMEOUT_MS);

            LOGGER.info("Connected to local minecraft");

            local.setTcpNoDelay(true);
            pump(data, local);
        } catch (IOException e) {
            LOGGER.debug("LAN+ relay session {} failed: {}", id, e.toString());
            closeQuietly(data);
            closeQuietly(local);
        }
    }

    private void pump(Socket a, Socket b) {
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
            // peer closed — tear down
        } finally {
            try {
                to.shutdownOutput();
            } catch (IOException ignored) {
                // already closed
            }
        }
    }

    // --- helpers ----------------------------------------------------------------------------------

    private Socket connect(String host, int port) throws IOException {
        if (plaintext) {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setTcpNoDelay(true);
            return s;
        }
        SSLSocket s = (SSLSocket) SSLSocketFactory.getDefault().createSocket(host, port);
        SSLParameters params = s.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS"); // verify the relay's cert hostname
        s.setSSLParameters(params);
        s.setTcpNoDelay(true);
        s.startHandshake();
        return s;
    }

    private static void writeLine(OutputStream out, String json) throws IOException {
        out.write((json + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static JsonObject parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        try {
            return JsonParser.parseString(line).getAsJsonObject();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String str(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : null;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static void closeQuietly(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // nothing to do
            }
        }
    }

    private static ThreadFactory daemon(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
