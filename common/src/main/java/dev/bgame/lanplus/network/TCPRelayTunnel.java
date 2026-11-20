package dev.bgame.lanplus.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.Config;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class TCPRelayTunnel implements RelayTunnel {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final long DRAIN_MS = 5000;
    private final boolean plaintext;
    private final ExecutorService pool = Executors.newCachedThreadPool(daemon("lanplus-relay"));
    private final ConcurrentMap<Socket, Long> sessions = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();
    private volatile Socket control;
    private volatile boolean open;
    private volatile VoiceTunnel voice;
    private volatile long generation;
    private long voiceGeneration = -1;

    public TCPRelayTunnel(boolean plaintext) {
        this.plaintext = plaintext;
    }

    @Override
    public CompletableFuture<String> open(int localPort, RelayTicket ticket) {
        long session = reset();
        if (ticket == null || ticket.relayHost() == null || ticket.relayHost().isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<String> result = new CompletableFuture<>();
        pool.execute(() -> runControl(ticket, localPort, session, result));
        return result;
    }

    @Override
    public void close() {
        reset();
    }

    private long reset() {
        Socket c;
        VoiceTunnel v;
        long session;
        synchronized (lifecycleLock) {
            session = ++generation;
            open = false;
            c = control;
            control = null;
            v = voice;
            voice = null;
            voiceGeneration = -1;
        }
        if (v != null) {
            v.close();
        }
        closeQuietly(c);
        closeSessionsBefore(session);
        return session;
    }

    @Override
    public boolean isOpen() {
        Socket c = control;
        return open && c != null && !c.isClosed();
    }

    private void runControl(RelayTicket ticket, int localPort, long session, CompletableFuture<String> result) {
        Socket c = null;
        try {
            c = connect(ticket.relayHost(), ticket.relayPort());
            if (!installControl(session, c)) {
                return;
            }
            OutputStream out = c.getOutputStream();
            writeLine(out, GSON.toJson(Map.of("type", "HELLO", "ticket", nullToEmpty(ticket.ticket()))));

            BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            JsonObject first = parse(reader.readLine());
            String domain = first == null ? null : str(first, "domain");
            if (first == null || !"ASSIGNED".equals(str(first, "type")) || domain == null || domain.isBlank()) {
                LOGGER.warn("LAN+ relay rejected tunnel: {}", first == null ? "no response" : str(first, "reason"));
                return;
            }
            if (!markOpen(session, c)) {
                return;
            }
            result.complete(domain);
            LOGGER.info("LAN+ relay tunnel open: {}", domain);
            maybeStartVoice(ticket, str(first, "voiceHost"), str(first, "voiceKey"), localPort, session);

            String line;
            while (isCurrent(session) && (line = reader.readLine()) != null) {
                JsonObject msg = parse(line);
                if (msg == null) {
                    continue;
                }
                String type = str(msg, "type");
                if ("SESSION".equals(type)) {
                    String id = str(msg, "id");
                    if (id != null && !id.isBlank()) {
                        pool.execute(() -> proxySession(ticket, id, localPort, session));
                    }
                } else if ("PING".equals(type)) {
                    writeLine(out, GSON.toJson(Map.of("type", "PONG")));
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("LAN+ relay tunnel closed: {}", e.toString());
        } finally {
            result.complete(null);
            finishControl(session, c);
        }
    }

    private void proxySession(RelayTicket ticket, String id, int localPort, long session) {
        Socket data = null;
        Socket local = null;

        try {
            data = connect(ticket.relayHost(), ticket.relayPort());
            if (!track(session, data)) {
                return;
            }
            writeLine(data.getOutputStream(), GSON.toJson(Map.of("type", "DATA", "id", id)));
            local = new Socket();
            if (!track(session, local)) {
                return;
            }
            local.connect(new InetSocketAddress("127.0.0.1", localPort), CONNECT_TIMEOUT_MS);
            local.setTcpNoDelay(true);
            if (!isCurrent(session)) {
                return;
            }
            pump(data, local);
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("LAN+ relay session {} failed: {}", id, e.toString());
        } finally {
            untrack(data, session);
            untrack(local, session);
            closeQuietly(data);
            closeQuietly(local);
        }
    }

    private void maybeStartVoice(RelayTicket ticket, String voiceHost, String voiceKey, int svcPort, long session) {
        if (!isCurrent(session)) {
            return;
        }
        if (voiceHost != null && !voiceHost.isBlank()) {
            if (!voiceHost.equals(Config.voiceHost)) {
                Config.voiceHost = voiceHost;
                Config.save();
            }
            SVCBridge.applyVoiceHost(voiceHost);
        }
        if (!Config.voiceEnabled || voiceKey == null || voiceKey.isBlank() || !SVCBridge.installed()) {
            return;
        }
        pool.execute(() -> startVoice(ticket, voiceKey, svcPort, session));
    }

    private void startVoice(RelayTicket ticket, String voiceKey, int svcPort, long session) {
        Socket socket = null;
        try {
            if (!isCurrent(session)) {
                return;
            }
            socket = connect(ticket.relayHost(), ticket.relayPort());
            if (!isCurrent(session)) {
                return;
            }
            writeLine(socket.getOutputStream(), GSON.toJson(Map.of("type", "VOICE", "key", voiceKey)));
            VoiceTunnel vt = new VoiceTunnel(socket, svcPort, pool);
            socket = null;
            if (!installVoice(session, vt)) {
                vt.close();
                return;
            }
            LOGGER.info("LAN+ voice tunnel open (svc {})", svcPort);
            try {
                vt.run();
            } finally {
                clearVoice(session, vt);
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("LAN+ voice tunnel failed: {}", e.toString());
        } finally {
            closeQuietly(socket);
        }
    }

    private void pump(Socket a, Socket b) {
        try {
            Future<?> other = pool.submit(() -> copy(a, b));
            copy(b, a);
            try {
                other.get(DRAIN_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                other.cancel(true);
            } catch (ExecutionException | TimeoutException e) {
                other.cancel(true);
            }
        } finally {
            closeQuietly(a);
            closeQuietly(b);
        }
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

    private Socket connect(String host, int port) throws IOException {
        if (plaintext) {
            Socket s = new Socket();
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setTcpNoDelay(true);
            return s;
        }
        Socket connection = new Socket();
        try {
            connection.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket socket = (SSLSocket) factory.createSocket(connection, host, port, true);
            SSLParameters params = socket.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(params);
            socket.setSoTimeout(CONNECT_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            socket.startHandshake();
            socket.setSoTimeout(0);
            return socket;
        } catch (IOException | RuntimeException e) {
            closeQuietly(connection);
            throw e;
        }
    }

    private boolean installControl(long session, Socket socket) {
        synchronized (lifecycleLock) {
            if (generation != session) {
                return false;
            }
            control = socket;
            return true;
        }
    }

    private boolean markOpen(long session, Socket socket) {
        synchronized (lifecycleLock) {
            if (generation != session || control != socket) {
                return false;
            }
            open = true;
            return true;
        }
    }

    private void finishControl(long session, Socket socket) {
        VoiceTunnel staleVoice = null;
        boolean ended = false;
        synchronized (lifecycleLock) {
            if (control == socket) {
                control = null;
            }
            if (generation == session) {
                generation++;
                open = false;
                ended = true;
                if (voiceGeneration == session) {
                    staleVoice = voice;
                    voice = null;
                    voiceGeneration = -1;
                }
            }
        }
        closeQuietly(socket);
        if (staleVoice != null) {
            staleVoice.close();
        }
        if (ended) {
            closeSessions(session);
        }
    }

    private boolean installVoice(long session, VoiceTunnel tunnel) {
        synchronized (lifecycleLock) {
            if (generation != session || voice != null) {
                return false;
            }
            voice = tunnel;
            voiceGeneration = session;
            return true;
        }
    }

    private void clearVoice(long session, VoiceTunnel tunnel) {
        synchronized (lifecycleLock) {
            if (voiceGeneration == session && voice == tunnel) {
                voice = null;
                voiceGeneration = -1;
            }
        }
    }

    private boolean isCurrent(long session) {
        return generation == session;
    }

    private boolean track(long session, Socket socket) {
        if (!isCurrent(session)) {
            closeQuietly(socket);
            return false;
        }
        sessions.put(socket, session);
        if (isCurrent(session)) {
            return true;
        }
        if (sessions.remove(socket, session)) {
            closeQuietly(socket);
        }
        return false;
    }

    private void untrack(Socket socket, long session) {
        if (socket != null) {
            sessions.remove(socket, session);
        }
    }

    private void closeSessionsBefore(long session) {
        sessions.forEach((socket, owner) -> {
            if (owner < session && sessions.remove(socket, owner)) {
                closeQuietly(socket);
            }
        });
    }

    private void closeSessions(long session) {
        sessions.forEach((socket, owner) -> {
            if (owner == session && sessions.remove(socket, owner)) {
                closeQuietly(socket);
            }
        });
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
