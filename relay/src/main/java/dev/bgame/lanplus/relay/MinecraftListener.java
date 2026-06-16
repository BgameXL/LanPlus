package dev.bgame.lanplus.relay;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Handles public Minecraft player connections: reads the handshake,
 * routes by hostname, and hands the socket to the matching host as a parked session. The relay
 * does not pump here — the host dials back a data connection that does the pumping.
 */
final class MinecraftListener {

    private final RoutingTable table;
    private final RateLimiter limiter;

    MinecraftListener(RoutingTable table, RateLimiter limiter) {
        this.table = table;
        this.limiter = limiter;
    }

    void handle(Socket player) {
        boolean parked = false;
        try {
            player.setTcpNoDelay(true);
            String ip = ((InetSocketAddress) player.getRemoteSocketAddress()).getAddress().getHostAddress();
            if (!limiter.allow(ip)) {
                return;
            }
            InputStream in = player.getInputStream();
            MinecraftHandshake hs = MinecraftHandshake.read(in);
            if (hs == null || hs.serverAddress == null) {
                return;
            }
            HostSession host = table.lookup(hs.serverAddress);
            if (host == null) {
                return; // unknown domain / host offline
            }
            String sid = table.addPending(player, hs.raw);
            if (!host.send(Json.obj("type", "SESSION", "id", sid))) {
                table.claimPending(sid);
                return;
            }
            parked = true;
        } catch (IOException e) {
        } finally {
            if (!parked) {
                Pump.closeQuietly(player);
            }
        }
    }
}
