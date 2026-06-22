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
        Map<String, String> links,
        Map<String, String> prompts,
        boolean online,
        long lastSeen,
        boolean invisible,
        ModpackRef currentlyPlaying,
        ModpackRef favorite,
        boolean favoriteVisible,
        boolean currentlyPlayingVisible,
        boolean mostPlayedVisible
) {
    public Profile {
        Objects.requireNonNull(uuid, "uuid");
        links = links == null ? Map.of() : Map.copyOf(links);
        prompts = prompts == null ? Map.of() : Map.copyOf(prompts);
    }

    public String link(String platform) {
        return links.get(platform);
    }

    public String prompt(String promptId) {
        return prompts.get(promptId);
    }
}