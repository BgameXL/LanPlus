package dev.bgame.lanplus.profiles;

import dev.bgame.lanplus.api.Profile;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ProfilesService {

    CompletableFuture<Profile> get(UUID uuid);

    CompletableFuture<String> save(String bio, String pronouns, Map<String, String> links, boolean invisible);
}