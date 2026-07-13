package dev.bgame.lanplus.profiles;

import dev.bgame.lanplus.api.CatalogImage;
import dev.bgame.lanplus.api.PlayerIdentity;
import dev.bgame.lanplus.api.Profile;
import dev.bgame.lanplus.core.AssetCache;
import dev.bgame.lanplus.network.LanPlusNetwork;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class DefaultProfilesService implements ProfilesService {

    // catalog entries are capped at 512 KB server-side; allow a little slack while streaming
    private static final int MAX_IMAGE_BYTES = 512 * 1024;
    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(10);

    private final LanPlusNetwork network;
    private final Supplier<PlayerIdentity> identity;
    private final AssetCache assets;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Executor fetchExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lanplus-profile-assets");
        t.setDaemon(true);
        return t;
    });

    public DefaultProfilesService(LanPlusNetwork network, Supplier<PlayerIdentity> identity, AssetCache assets) {
        this.network = network;
        this.identity = identity;
        this.assets = assets;
    }

    @Override
    public CompletableFuture<Profile> get(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(null);
        }
        PlayerIdentity id = identity.get();
        UUID viewer = id == null ? null : id.uuid();
        return network.getProfile(uuid, viewer);
    }

    @Override
    public CompletableFuture<String> save(String bio, String pronouns, Map<String, String> links,
                                          Map<String, String> prompts, boolean invisible,
                                          boolean favoriteVisible, boolean currentlyPlayingVisible,
                                          boolean recentlyPlayedVisible) {
        PlayerIdentity id = identity.get();
        if (id == null) {
            return CompletableFuture.completedFuture("offline");
        }
        return network.updateProfile(id.uuid(), bio, pronouns, links, prompts, invisible,
                favoriteVisible, currentlyPlayingVisible, recentlyPlayedVisible);
    }

    @Override
    public CompletableFuture<String> setFavoriteModpack(String modpackId) {
        PlayerIdentity id = identity.get();
        if (id == null) {
            return CompletableFuture.completedFuture("offline");
        }
        return network.setFavoriteModpack(id.uuid(), modpackId);
    }

    @Override
    public CompletableFuture<String> setBackground(String style, int color, int opacity, String imageId) {
        PlayerIdentity id = identity.get();
        if (id == null) {
            return CompletableFuture.completedFuture("offline");
        }
        return network.setBackground(id.uuid(), style, color, opacity, imageId);
    }

    @Override
    public CompletableFuture<String> setBanner(String bannerId) {
        PlayerIdentity id = identity.get();
        if (id == null) {
            return CompletableFuture.completedFuture("offline");
        }
        return network.setBanner(id.uuid(), bannerId);
    }

    @Override
    public CompletableFuture<List<CatalogImage>> backgrounds() {
        return network.getBackgrounds();
    }

    @Override
    public CompletableFuture<List<CatalogImage>> banners() {
        return network.getBanners();
    }

    @Override
    public CompletableFuture<byte[]> imageBytes(CatalogImage image) {
        if (image == null || image.url() == null) {
            return CompletableFuture.completedFuture(null);
        }

        String key = image.url();
        byte[] cached = assets == null ? null : assets.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return CompletableFuture.supplyAsync(() -> {
            byte[] bytes = download(image.url());
            if (bytes != null && assets != null) {
                assets.put(key, bytes);
            }
            return bytes;
        }, fetchExecutor).exceptionally(e -> null);
    }

    private byte[] download(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(FETCH_TIMEOUT).GET().build();
            HttpResponse<InputStream> resp = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream in = resp.body()) {
                if (resp.statusCode() != 200) {
                    return null;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int total = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > MAX_IMAGE_BYTES) {
                        return null;
                    }
                    out.write(buf, 0, n);
                }
                return out.toByteArray();
            }
        } catch (IOException | RuntimeException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public CompletableFuture<Void> reportAdvancement(String advancementId) {
        PlayerIdentity id = identity.get();
        if (id == null || advancementId == null) {
            return CompletableFuture.completedFuture(null);
        }
        return network.reportAdvancement(id.uuid(), advancementId);
    }
}