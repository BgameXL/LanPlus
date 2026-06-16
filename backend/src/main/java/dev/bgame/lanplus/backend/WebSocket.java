package dev.bgame.lanplus.backend;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

final class WebSocket {

    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    private WebSocket(Socket socket) throws IOException {
        this.socket = socket;
        this.in = socket.getInputStream();
        this.out = socket.getOutputStream();
    }

    static WebSocket accept(Http.Request req, Socket socket) throws IOException {
        String key = req.headers().get("sec-websocket-key");
        if (key == null) {
            throw new IOException("missing Sec-WebSocket-Key");
        }
        String acceptKey = sha1Base64(key + MAGIC);
        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(response.getBytes(StandardCharsets.UTF_8));
        out.flush();
        return new WebSocket(socket);
    }

    String readText() throws IOException {
        while (true) {
            int b0 = in.read();
            if (b0 == -1) {
                return null;
            }
            int opcode = b0 & 0x0F;
            int b1 = in.read();
            if (b1 == -1) {
                return null;
            }
            boolean masked = (b1 & 0x80) != 0;
            long len = b1 & 0x7F;
            if (len == 126) {
                len = (read1() << 8) | read1();
            } else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) {
                    len = (len << 8) | read1();
                }
            }
            byte[] mask = new byte[4];
            if (masked) {
                readFully(mask);
            }
            byte[] payload = new byte[(int) len];
            readFully(payload);
            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i & 3];
                }
            }
            switch (opcode) {
                case 0x1 -> {
                    return new String(payload, StandardCharsets.UTF_8);
                }
                case 0x8 -> {
                    return null;
                }
                case 0x9 -> sendFrame(0xA, payload);
                default -> { /* pong / continuation: ignore */ }
            }
        }
    }

    void sendText(String message) throws IOException {
        sendFrame(0x1, message.getBytes(StandardCharsets.UTF_8));
    }

    void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private synchronized void sendFrame(int opcode, byte[] payload) throws IOException {
        out.write(0x80 | opcode); // FIN + opcode
        int n = payload.length;
        if (n < 126) {
            out.write(n);
        } else if (n < 65536) {
            out.write(126);
            out.write((n >> 8) & 0xFF);
            out.write(n & 0xFF);
        } else {
            out.write(127);
            for (int i = 7; i >= 0; i--) {
                out.write((int) (((long) n >> (8 * i)) & 0xFF));
            }
        }
        out.write(payload);
        out.flush();
    }

    private int read1() throws IOException {
        int b = in.read();
        if (b == -1) {
            throw new IOException("unexpected EOF");
        }
        return b;
    }

    private void readFully(byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n == -1) {
                throw new IOException("unexpected EOF");
            }
            off += n;
        }
    }

    private static String sha1Base64(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            return Base64.getEncoder().encodeToString(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
