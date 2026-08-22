package dev.bgame.lanplus.api;

public record Announcement(int id, Type type, String title, String body, long createdAt, CatalogImage image) {

    public enum Type {
        UPDATE,
        MAINTENANCE,
        GENERAL,
        FREE,
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