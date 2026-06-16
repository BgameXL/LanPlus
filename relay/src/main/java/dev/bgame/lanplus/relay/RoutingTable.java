package dev.bgame.lanplus.relay;

import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class RoutingTable {

    static final class Pending {
        final Socket player;
        final byte[] handshake;
        final long createdAt = System.currentTimeMillis();

        Pending(Socket player, byte[] handshake) {
            this.player = player;
            this.handshake = handshake;
        }
    }

    private final Map<String, HostSession> domains = new ConcurrentHashMap<>();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    boolean register(String domain, HostSession session) {
        return domains.putIfAbsent(domain, session) == null;
    }

    HostSession lookup(String domain) {
        return domains.get(domain);
    }

    void unregister(String domain, HostSession session) {
        domains.remove(domain, session);
    }

    int size() {
        return domains.size();
    }

    String addPending(Socket player, byte[] handshake) {
        String sid = UUID.randomUUID().toString();
        pending.put(sid, new Pending(player, handshake));
        return sid;
    }

    Pending claimPending(String sid) {
        return sid == null ? null : pending.remove(sid);
    }

    void pingAll() {
        String ping = Json.obj("type", "PING");
        for (HostSession s : domains.values()) {
            s.send(ping);
        }
    }

    void sweep(long maxAgeMs) {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> {
            if (now - e.getValue().createdAt > maxAgeMs) {
                Pump.closeQuietly(e.getValue().player);
                return true;
            }
            return false;
        });
    }
}
