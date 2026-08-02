package dev.bgame.lanplus.profiles;

import dev.bgame.lanplus.api.CatalogImage;
import dev.bgame.lanplus.api.Profile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ProfilesService {

    CompletableFuture<Profile> get(UUID uuid);

    CompletableFuture<String> save(String bio, String pronouns, Map<String, String> links,
                                   Map<String, String> prompts, boolean invisible,
                                   boolean favoriteVisible, boolean currentlyPlayingVisible,
                                   boolean recentlyPlayedVisible);

    CompletableFuture<String> setFavoriteModpack(String modpackId);

    CompletableFuture<String> setBackground(String style, int color, int opacity, String imageId);

    CompletableFuture<String> setBanner(String bannerId);

    CompletableFuture<List<CatalogImage>> backgrounds();

    CompletableFuture<List<CatalogImage>> banners();

    CompletableFuture<byte[]> imageBytes(CatalogImage image);

    CompletableFuture<Void> reportAdvancement(String advancementId);
}