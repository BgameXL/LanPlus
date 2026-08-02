package dev.bgame.lanplus.api;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record PresenceSnapshot(
        GameplayState state,
        String worldName,
        String address,
        String joinCode,
        SkinRef skin,
        String modpackId,
        HostAccessMode accessMode,
        Set<UUID> allowedUuids
) {
    public PresenceSnapshot {
        Objects.requireNonNull(state, "state");
        allowedUuids = allowedUuids == null ? Set.of() : Set.copyOf(allowedUuids);
    }
}
