package dev.bgame.lanplus.api;

import java.util.Objects;

public record SkinRef(SkinType type, String id, String hash) {
    public SkinRef {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
    }
}
