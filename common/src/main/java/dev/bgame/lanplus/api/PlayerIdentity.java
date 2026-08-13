package dev.bgame.lanplus.api;

import java.util.Objects;
import java.util.UUID;

public record PlayerIdentity(UUID uuid, String username) {
    public PlayerIdentity {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(username, "username");
    }
}
