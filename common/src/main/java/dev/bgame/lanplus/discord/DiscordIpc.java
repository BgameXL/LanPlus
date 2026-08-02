package dev.bgame.lanplus.discord;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Low-level Discord IPC transport + framing (no Minecraft, no Discord library).
 *
 * Discord exposes a local socket named {@code discord-ipc-N}: a Unix domain socket
 * on Linux/macOS, a named pipe on Windows. Frames are {@code int32 opcode | int32 length | UTF-8
 * payload}, all little-endian. This class only moves bytes; the activity protocol lives in
 * {@link DiscordRichPresence}.
 */
final class DiscordIpc implements Closeable {
    
    static final int OP_HANDSHAKE = 0;
    static final int OP_FRAME = 1;
    static final int OP_CLOSE = 2;
    static final int OP_PING = 3;
    static final int OP_PONG = 4;

    record Frame(int opcode, byte[] data) {}

    private final Transport transport;

    private DiscordIpc(Transport transport) {
        this.transport = transport;
    }

    static DiscordIpc open() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Transport t = os.contains("win") ? openWindows() : openUnix();
        return new DiscordIpc(t);
    }

    void send(int opcode, byte[] payload) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(opcode).putInt(payload.length);
        transport.writeFully(header.array());
        transport.writeFully(payload);
    }

    Frame read() throws IOException {
        byte[] head = new byte[8];
        transport.readFully(head);
        ByteBuffer header = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        int opcode = header.getInt();
        int length = header.getInt();
        if (length < 0 || length > 64 * 1024) {
            throw new IOException("Discord IPC frame length out of range: " + length);
        }
        byte[] data = new byte[length];
        transport.readFully(data);
        return new Frame(opcode, data);
    }

    @Override
    public void close() throws IOException {
        transport.close();
    }

    private static Transport openUnix() throws IOException {
        IOException last = null;
        for (Path path : unixCandidates()) {
            try {
                SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                ch.connect(UnixDomainSocketAddress.of(path));
                return new UnixTransport(ch);
            } catch (IOException | UnsupportedOperationException e) {
                last = e instanceof IOException io ? io : new IOException(e);
            }
        }
        throw last != null ? last : new IOException("no discord-ipc socket found");
    }

    private static List<Path> unixCandidates() {
        List<String> bases = new ArrayList<>();
        addIfPresent(bases, System.getenv("XDG_RUNTIME_DIR"));
        addIfPresent(bases, System.getenv("TMPDIR"));
        addIfPresent(bases, System.getenv("TMP"));
        addIfPresent(bases, System.getenv("TEMP"));
        bases.add("/tmp");

        String[] subdirs = {"", "app/com.discordapp.Discord/", "snap.discord/"};
        List<Path> paths = new ArrayList<>();
        for (String base : bases) {
            for (String sub : subdirs) {
                for (int i = 0; i < 10; i++) {
                    paths.add(Path.of(base, sub + "discord-ipc-" + i));
                }
            }
        }
        return paths;
    }

    private static void addIfPresent(List<String> out, String value) {
        if (value != null && !value.isBlank()) {
            out.add(value);
        }
    }

    private static Transport openWindows() throws IOException {
        IOException last = null;
        for (int i = 0; i < 10; i++) {
            try {
                return new WindowsTransport(new RandomAccessFile("\\\\?\\pipe\\discord-ipc-" + i, "rw"));
            } catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("no discord-ipc pipe found");
    }

    private interface Transport extends Closeable {
        void writeFully(byte[] b) throws IOException;

        void readFully(byte[] b) throws IOException;
    }

    private static final class UnixTransport implements Transport {
        private final SocketChannel ch;

        UnixTransport(SocketChannel ch) {
            this.ch = ch;
        }

        @Override
        public void writeFully(byte[] b) throws IOException {
            ByteBuffer buf = ByteBuffer.wrap(b);
            while (buf.hasRemaining()) {
                ch.write(buf);
            }
        }

        @Override
        public void readFully(byte[] b) throws IOException {
            ByteBuffer buf = ByteBuffer.wrap(b);
            while (buf.hasRemaining()) {
                if (ch.read(buf) < 0) {
                    throw new EOFException("discord-ipc closed");
                }
            }
        }

        @Override
        public void close() throws IOException {
            ch.close();
        }
    }

    private static final class WindowsTransport implements Transport {
        private final RandomAccessFile pipe;

        WindowsTransport(RandomAccessFile pipe) {
            this.pipe = pipe;
        }

        @Override
        public void writeFully(byte[] b) throws IOException {
            pipe.write(b);
        }

        @Override
        public void readFully(byte[] b) throws IOException {
            pipe.readFully(b);
        }

        @Override
        public void close() throws IOException {
            pipe.close();
        }
    }
}