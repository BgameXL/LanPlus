package dev.bgame.lanplus.relay;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

final class MinecraftListener {

    private final RoutingTable table;
    private final RateLimiter limiter;
    private final GuestTokenValidator guestTokens;

    MinecraftListener(RoutingTable table, RateLimiter limiter, GuestTokenValidator guestTokens) {
        this.table = table;
        this.limiter = limiter;
        this.guestTokens = guestTokens;
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
            HostSession host = route(hs.serverAddress);
            if (host == null) {
                return;
            }
            table.noteVoiceIp(ip, host);
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

    private HostSession route(String serverAddress) {
        HostSession direct = table.lookup(serverAddress);
        if (direct != null) {
            return direct.requireToken() ? null : direct;
        }
        int dot = serverAddress.indexOf('.');
        String token = dot < 0 ? serverAddress : serverAddress.substring(0, dot);
        String domain = guestTokens.validate(token);
        return domain == null ? null : table.lookup(domain);
    }
}
