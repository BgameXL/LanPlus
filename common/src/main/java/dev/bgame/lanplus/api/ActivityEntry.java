package dev.bgame.lanplus.api;

import java.util.UUID;

public record ActivityEntry(UUID actor, String actorName, Type type, String subject, long at) {

    public enum Type {
        HOSTING_STARTED,
        FRIEND_ADDED,
        UNKNOWN;

        public static Type fromWire(String raw) {
            if (raw == null) {
                return UNKNOWN;
            }
            try {
                return valueOf(raw);
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }
    }
}
