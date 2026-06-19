package dev.bgame.lanplus.api;

import java.util.Objects;
import java.util.UUID;

public record Friend(
        UUID uuid,
        String username,
        Connectivity connectivity,
        GameplayState state,
        String worldName,
        String joinCode,
        SkinRef skin,
        boolean muted,
        boolean blocked
) {
    public Friend {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(connectivity, "connectivity");
    }
}
