package dev.bgame.lanplus.relay;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class RateLimiter {

    private final int perMinute;
    private final Map<String, long[]> windows = new ConcurrentHashMap<>(); // ip -> [windowStartMs, count]

    RateLimiter(int perMinute) {
        this.perMinute = perMinute;
    }

    boolean allow(String ip) {
        long now = System.currentTimeMillis();
        long[] w = windows.computeIfAbsent(ip, k -> new long[]{now, 0});
        synchronized (w) {
            if (now - w[0] >= 60_000) {
                w[0] = now;
                w[1] = 0;
            }
            if (w[1] >= perMinute) {
                return false;
            }
            w[1]++;
            return true;
        }
    }

    void sweep() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return now - e.getValue()[0] >= 120_000;
            }
        });
    }
}
