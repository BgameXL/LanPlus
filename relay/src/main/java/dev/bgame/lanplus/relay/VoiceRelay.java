package dev.bgame.lanplus.relay;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Arrays;

final class VoiceRelay {

    private final RoutingTable table;
    private final DatagramSocket socket;

    VoiceRelay(RoutingTable table, DatagramSocket socket) {
        this.table = table;
        this.socket = socket;
    }

    void receiveLoop() {
        byte[] buf = new byte[4096];
        while (!socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                InetSocketAddress from = (InetSocketAddress) packet.getSocketAddress();
                HostSession host = table.voiceForIp(from.getAddress().getHostAddress());
                if (host == null) {
                    continue;
                }
                byte[] data = Arrays.copyOfRange(packet.getData(), packet.getOffset(),
                        packet.getOffset() + packet.getLength());
                host.forwardFromGuest(from, data);
            } catch (IOException e) {
                if (!socket.isClosed()) {
                    RelayServer.log("voice recv error: " + e);
                }
            }
        }
    }

    void sendToGuest(InetSocketAddress guest, byte[] data) {
        if (guest == null) {
            return;
        }
        try {
            socket.send(new DatagramPacket(data, data.length, guest));
        } catch (IOException ignored) {
        }
    }
}
