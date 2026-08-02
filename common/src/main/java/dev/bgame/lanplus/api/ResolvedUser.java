package dev.bgame.lanplus.api;

import java.util.Objects;
import java.util.UUID;

public record ResolvedUser(UUID uuid, String username, boolean online) {
    public ResolvedUser {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(username, "username");
    }
}
