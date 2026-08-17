package dev.bgame.lanplus.api;

import java.util.Objects;
import java.util.UUID;

public record PresenceUpdate(UUID uuid, Connectivity connectivity, GameplayState state, String worldName,
                             String joinCode, String gameMode, String difficulty, boolean allowCommands) {
    public PresenceUpdate {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(connectivity, "connectivity");
    }
}
