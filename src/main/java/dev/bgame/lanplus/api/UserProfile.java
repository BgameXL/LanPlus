package dev.bgame.lanplus.api;

import java.util.Objects;
import java.util.UUID;

public record UserProfile(UUID uuid, String username, String friendCode) {
    public UserProfile {
        Objects.requireNonNull(uuid, "uuid");
    }
}
