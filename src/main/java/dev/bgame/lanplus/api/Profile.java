package dev.bgame.lanplus.api;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record Profile(
        UUID uuid,
        String username,
        String friendCode,
        SkinRef skin,
        String pronouns,
        String bio,
        Map<String, String> links,
        Map<String, String> prompts,
        boolean online,
        long lastSeen,
        boolean invisible,
        ModpackRef currentlyPlaying,
        ModpackRef lastPlayed,
        ModpackRef favorite,
        ModpackRef recentlyPlayed,
        boolean favoriteVisible,
        boolean currentlyPlayingVisible,
        boolean recentlyPlayedVisible,
        int tier,
        int advancements,
        int xp,
        Map<String, Integer> xpSources,
        ProfileBackground background
) {
    public Profile {
        Objects.requireNonNull(uuid, "uuid");
        links = links == null ? Map.of() : Map.copyOf(links);
        prompts = prompts == null ? Map.of() : Map.copyOf(prompts);
        xpSources = xpSources == null ? Map.of() : Map.copyOf(xpSources);
        background = background == null ? ProfileBackground.DEFAULT : background;
    }

    public String link(String platform) {
        return links.get(platform);
    }

    public String prompt(String promptId) {
        return prompts.get(promptId);
    }

    public boolean hasXp() {
        return xp >= 0;
    }

    public int xpFrom(String source) {
        Integer v = xpSources.get(source);
        return v == null ? 0 : v;
    }
}