package dev.bgame.lanplus.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Suggestion(UUID uuid, String username, String friendCode, int mutualCount, List<String> mutualNames) {
    public Suggestion {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(username, "username");
        mutualNames = mutualNames == null ? List.of() : List.copyOf(mutualNames);
    }
}