package dev.bgame.lanplus.network;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

final class VoiceTunnel {

    private static final Logger LOGGER = LogUtils.getLogger();
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final int svcPort;
    private final ExecutorService pool;
    private final Map<Integer, DatagramSocket> byId = new ConcurrentHashMap<>();
    private volatile boolean open = true;

    VoiceTunnel(Socket socket, int svcPort, ExecutorService pool) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        this.svcPort = svcPort;
        this.pool = pool;
    }

    void run() {
        try {
            while (open) {
                int id = in.readInt();
                int len = in.readInt();
                if (len < 0 || len > 65535) {
                    break;
                }
                byte[] data = new byte[len];
                in.readFully(data);
                DatagramSocket ds = socketFor(id);
                if (ds != null) {
                    try {
                        ds.send(new DatagramPacket(data, data.length));
                    } catch (IOException ignored) {
                    }
                }
            }
        } catch (IOException ignored) {
        } finally {
            close();
        }
    }

    private DatagramSocket socketFor(int id) {
        DatagramSocket existing = byId.get(id);
        if (existing != null) {
            return existing;
        }
        try {
            DatagramSocket ds = new DatagramSocket();
            ds.connect(InetAddress.getLoopbackAddress(), svcPort);
            DatagramSocket prior = byId.putIfAbsent(id, ds);
            if (prior != null) {
                ds.close();
                return prior;
            }
            pool.execute(() -> readFromSvc(id, ds));
            return ds;
        } catch (IOException e) {
            return null;
        }
    }

    private void readFromSvc(int id, DatagramSocket ds) {
        byte[] buf = new byte[4096];
        try {
            while (open && !ds.isClosed()) {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                ds.receive(packet);
                synchronized (out) {
                    out.writeInt(id);
                    out.writeInt(packet.getLength());
                    out.write(packet.getData(), packet.getOffset(), packet.getLength());
                    out.flush();
                }
            }
        } catch (IOException ignored) {
        }
    }

    void close() {
        open = false;
        for (DatagramSocket ds : byId.values()) {
            ds.close();
        }
        byId.clear();
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}