package dev.bgame.lanplus.api;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record Profile(
        UUID uuid,
        String username,
        String friendCode,
        String pronouns,
        String bio,
        Map<String, String> links
) {
    public Profile {
        Objects.requireNonNull(uuid, "uuid");
        links = links == null ? Map.of() : Map.copyOf(links);
    }

    public String link(String platform) {
        return links.get(platform);
    }
}