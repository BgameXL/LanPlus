package dev.bgame.lanplus.api;

import java.util.Objects;

public record PresenceSnapshot(
        GameplayState state,
        String worldName,
        String address,
        String joinCode,
        SkinRef skin
) {
    public PresenceSnapshot {
        Objects.requireNonNull(state, "state");
    }
}
