package dev.bgame.lanplus.api;

import java.util.Objects;

public record SkinRef(SkinType type, String id, String hash, String model) {
    public SkinRef {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
    }

    public boolean slim() {
        return "slim".equalsIgnoreCase(model);
    }
}