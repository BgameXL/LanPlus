package dev.bgame.lanplus.relay;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

final class MinecraftHandshake {

    final byte[] raw;
    final String serverAddress;
    final int nextState;

    private MinecraftHandshake(byte[] raw, String serverAddress, int nextState) {
        this.raw = raw;
        this.serverAddress = serverAddress;
        this.nextState = nextState;
    }

    static MinecraftHandshake read(InputStream in) throws IOException {
        PushbackInputStream pin = new PushbackInputStream(in, 1);
        int first = pin.read();
        if (first == -1) {
            return null;
        }
        if (first == 0xFE) {
            return null; // legacy server-list ping — unsupported
        }
        pin.unread(first);

        ByteArrayOutputStream raw = new ByteArrayOutputStream(64);
        int length = readVarInt(pin, raw);
        if (length <= 0 || length > 32 * 1024) {
            throw new IOException("invalid handshake length: " + length);
        }
        byte[] body = pin.readNBytes(length);
        if (body.length != length) {
            throw new EOFException("truncated handshake");
        }
        raw.write(body);

        ByteArrayInputStream b = new ByteArrayInputStream(body);
        String addr = null;
        int nextState = -1;
        if (readVarInt(b, null) == 0x00) { // packet id 0x00 = handshake
            readVarInt(b, null);           // protocol version (ignored)
            addr = normalize(readString(b));
            skipFully(b, 2);               // server port (unsigned short, ignored)
            nextState = readVarInt(b, null);
        }
        return new MinecraftHandshake(raw.toByteArray(), addr, nextState);
    }

    static String normalize(String host) {
        if (host == null) {
            return null;
        }
        int nul = host.indexOf('\0');
        if (nul >= 0) {
            host = host.substring(0, nul);
        }
        host = host.toLowerCase(Locale.ROOT);
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        return host.isBlank() ? null : host;
    }

    private static int readVarInt(InputStream in, ByteArrayOutputStream raw) throws IOException {
        int value = 0;
        int pos = 0;
        int b;
        do {
            b = in.read();
            if (b == -1) {
                throw new EOFException("VarInt");
            }
            if (raw != null) {
                raw.write(b);
            }
            value |= (b & 0x7F) << pos;
            pos += 7;
            if (pos > 35) {
                throw new IOException("VarInt too big");
            }
        } while ((b & 0x80) != 0);
        return value;
    }

    private static String readString(InputStream in) throws IOException {
        int len = readVarInt(in, null);
        if (len < 0 || len > 32767) {
            throw new IOException("bad string length: " + len);
        }
        byte[] s = in.readNBytes(len);
        if (s.length != len) {
            throw new EOFException("truncated string");
        }
        return new String(s, StandardCharsets.UTF_8);
    }

    private static void skipFully(InputStream in, int n) throws IOException {
        while (n > 0) {
            long skipped = in.skip(n);
            if (skipped <= 0) {
                if (in.read() == -1) {
                    throw new EOFException();
                }
                skipped = 1;
            }
            n -= (int) skipped;
        }
    }
}
