package dev.bgame.lanplus.relay;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

final class HostSession {

    private final Socket control;
    private final OutputStream out;
    private final String domain;
    private final boolean requireToken;
    private final String voiceKey = UUID.randomUUID().toString();
    private final VoiceRelay voiceRelay;

    private final Map<String, Integer> guestIdByAddr = new ConcurrentHashMap<>();
    private final Map<Integer, InetSocketAddress> guestById = new ConcurrentHashMap<>();
    private final AtomicInteger nextGuestId = new AtomicInteger(1);

    private volatile Socket voice;
    private volatile DataOutputStream voiceOut;

    HostSession(Socket control, String domain, boolean requireToken, VoiceRelay voiceRelay) throws IOException {
        this.control = control;
        this.out = control.getOutputStream();
        this.domain = domain;
        this.requireToken = requireToken;
        this.voiceRelay = voiceRelay;
    }

    String domain() {
        return domain;
    }

    boolean requireToken() {
        return requireToken;
    }

    String voiceKey() {
        return voiceKey;
    }

    synchronized boolean send(String jsonLine) {
        try {
            out.write((jsonLine + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            return true;
        } catch (IOException e) {
            Pump.closeQuietly(control);
            return false;
        }
    }

    void attachVoiceChannel(Socket socket, DataInputStream in, ExecutorService pool) {
        try {
            this.voice = socket;
            this.voiceOut = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            Pump.closeQuietly(socket);
            return;
        }
        pool.execute(() -> readVoice(in));
    }

    void forwardFromGuest(InetSocketAddress guest, byte[] data) {
        DataOutputStream vo = voiceOut;
        if (vo == null) {
            return;
        }
        int id = idFor(guest);
        try {
            synchronized (vo) {
                vo.writeInt(id);
                vo.writeInt(data.length);
                vo.write(data);
                vo.flush();
            }
        } catch (IOException e) {
            closeVoice();
        }
    }

    void closeVoiceChannel() {
        closeVoice();
    }

    private void readVoice(DataInputStream in) {
        try {
            while (true) {
                int id = in.readInt();
                int len = in.readInt();
                if (len < 0 || len > 65535) {
                    break;
                }
                byte[] data = new byte[len];
                in.readFully(data);
                InetSocketAddress guest = guestById.get(id);
                if (guest != null) {
                    voiceRelay.sendToGuest(guest, data);
                }
            }
        } catch (IOException ignored) {
        } finally {
            closeVoice();
        }
    }

    private int idFor(InetSocketAddress guest) {
        String key = guest.getAddress().getHostAddress() + ":" + guest.getPort();
        Integer existing = guestIdByAddr.get(key);
        if (existing != null) {
            return existing;
        }
        int id = nextGuestId.getAndIncrement();
        Integer prior = guestIdByAddr.putIfAbsent(key, id);
        if (prior != null) {
            return prior;
        }
        guestById.put(id, guest);
        return id;
    }

    private void closeVoice() {
        Socket v = voice;
        voice = null;
        voiceOut = null;
        Pump.closeQuietly(v);
    }
}
