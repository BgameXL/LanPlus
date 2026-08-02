package dev.bgame.lanplus.backend;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

final class EventHub {

    private final Map<UUID, Set<WebSocket>> byUser = new ConcurrentHashMap<>();
    private final Map<WebSocket, UUID> userOf = new ConcurrentHashMap<>();

    void register(UUID uuid, WebSocket ws) {
        userOf.put(ws, uuid);
        byUser.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(ws);
    }

    void unregister(WebSocket ws) {
        UUID uuid = userOf.remove(ws);
        if (uuid != null) {
            Set<WebSocket> set = byUser.get(uuid);
            if (set != null) {
                set.remove(ws);
                if (set.isEmpty()) {
                    byUser.remove(uuid);
                }
            }
        }
    }

    void send(UUID uuid, Object event) {
        Set<WebSocket> set = byUser.get(uuid);
        if (set == null || set.isEmpty()) {
            return;
        }
        String json = Json.write(event);
        for (WebSocket ws : set) {
            // Offload to avoid blocking REST/WebSocket worker threads on a dead peer.
            ForkJoinPool.commonPool().execute(() -> sendOne(ws, json));
        }
    }

    void pingAll() {
        String ping = Json.write(Map.of("type", "PING"));
        for (WebSocket ws : userOf.keySet()) {
            ForkJoinPool.commonPool().execute(() -> sendOne(ws, ping));
        }
    }

    private void sendOne(WebSocket ws, String json) {
        try {
            ws.sendText(json);
        } catch (IOException e) {
            ws.close();
            unregister(ws);
        }
    }
}
