package dev.bgame.lanplus.profiles;

import dev.bgame.lanplus.api.ModpackRef;
import dev.bgame.lanplus.api.Profile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ProfilesService {

    CompletableFuture<Profile> get(UUID uuid);

    CompletableFuture<String> save(String bio, String pronouns, Map<String, String> links,
                                   Map<String, String> prompts, boolean invisible,
                                   String favoriteModpackId, boolean favoriteVisible,
                                   boolean currentlyPlayingVisible);

    CompletableFuture<List<ModpackRef>> modpacks();
}