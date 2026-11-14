package dev.bgame.lanplus.profiles;

import dev.bgame.lanplus.api.PlayerIdentity;
import dev.bgame.lanplus.api.Profile;
import dev.bgame.lanplus.core.ProfileCache;
import dev.bgame.lanplus.network.LanPlusNetwork;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultProfilesServiceTest {

    @TempDir
    Path cacheDir;

    @Test
    void fallsBackToCachedOwnProfileWhenRequestFails() {
        UUID uuid = UUID.randomUUID();
        Profile cached = profile(uuid);
        ProfileCache cache = cachedProfile(uuid);
        CompletableFuture<Profile> failure = CompletableFuture.failedFuture(new IOException("offline"));
        DefaultProfilesService service = service(uuid, cache, network(failure, cached));

        Profile result = service.get(uuid).join();

        assertTrue(result.cached());
        assertEquals(uuid, result.uuid());
    }

    @Test
    void fallsBackToCachedOwnProfileWhenNetworkReturnsNull() {
        UUID uuid = UUID.randomUUID();
        Profile cached = profile(uuid);
        ProfileCache cache = cachedProfile(uuid);
        CompletableFuture<Profile> emptyResponse = CompletableFuture.completedFuture(null);
        DefaultProfilesService service = service(uuid, cache, network(emptyResponse, cached));

        Profile result = service.get(uuid).join();

        assertTrue(result.cached());
        assertEquals(uuid, result.uuid());
    }

    @Test
    void doesNotExposeAnotherPlayersCachedProfile() {
        UUID viewer = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        ProfileCache cache = cachedProfile(target);
        CompletableFuture<Profile> failure = CompletableFuture.failedFuture(new IOException("offline"));
        DefaultProfilesService service = service(viewer, cache, network(failure, profile(target)));

        assertNull(service.get(target).join());
    }

    @Test
    void returnsNullWhenOwnProfileIsNotCached() {
        UUID uuid = UUID.randomUUID();
        CompletableFuture<Profile> failure = CompletableFuture.failedFuture(new IOException("offline"));
        DefaultProfilesService service = service(uuid, new ProfileCache(cacheDir), network(failure, profile(uuid)));

        assertNull(service.get(uuid).join());
    }

    @Test
    void keepsImagesAtTheConfiguredLimit() throws IOException {
        byte[] bytes = {1, 2, 3};

        assertArrayEquals(bytes, DefaultProfilesService.getBytes(new ByteArrayInputStream(bytes), 3));
    }

    @Test
    void rejectsImagesOverTheConfiguredLimit() throws IOException {
        byte[] bytes = {1, 2, 3, 4};

        assertNull(DefaultProfilesService.getBytes(new ByteArrayInputStream(bytes), 3));
    }

    private ProfileCache cachedProfile(UUID uuid) {
        ProfileCache cache = new ProfileCache(cacheDir);
        cache.put(uuid, "cached-profile-json");
        return cache;
    }

    private static DefaultProfilesService service(UUID viewer, ProfileCache cache, LanPlusNetwork network) {
        PlayerIdentity identity = new PlayerIdentity(viewer, "Viewer");
        return new DefaultProfilesService(network, () -> identity, null, cache);
    }

    private static LanPlusNetwork network(CompletableFuture<Profile> response, Profile parsedProfile) {
        return (LanPlusNetwork) Proxy.newProxyInstance(
                LanPlusNetwork.class.getClassLoader(),
                new Class<?>[]{LanPlusNetwork.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getProfile" -> response;
                    case "parseProfileJson" -> parsedProfile;
                    case "setProfileBodySink" -> null;
                    default -> throw new AssertionError("Unexpected network call: " + method.getName());
                });
    }

    private static Profile profile(UUID uuid) {
        return new Profile(uuid, "Player", "friend-code", null, null, "bio", Map.of(), Map.of(),
                false, 0, false, null, null, null, null,
                true, true, true, true, true,
                0, 0, -1, Map.of(), null, null, null, false);
    }
}