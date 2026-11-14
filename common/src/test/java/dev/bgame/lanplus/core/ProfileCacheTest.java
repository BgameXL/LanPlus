package dev.bgame.lanplus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProfileCacheTest {

    @TempDir
    Path cacheDir;

    @Test
    void persistsProfilesAcrossCacheInstances() {
        UUID uuid = UUID.randomUUID();
        String body = "{\"username\":\"Alex\"}";

        new ProfileCache(cacheDir).put(uuid, body);

        assertEquals(body, new ProfileCache(cacheDir).get(uuid));
    }

    @Test
    void replacesExistingProfile() {
        UUID uuid = UUID.randomUUID();
        ProfileCache cache = new ProfileCache(cacheDir);

        cache.put(uuid, "old");
        cache.put(uuid, "new");

        assertEquals("new", new ProfileCache(cacheDir).get(uuid));
    }

    @Test
    void missingAndNullProfilesReturnNull() {
        ProfileCache cache = new ProfileCache(cacheDir);

        assertNull(cache.get(UUID.randomUUID()));
        assertNull(cache.get(null));
        cache.put(null, "ignored");
        cache.put(UUID.randomUUID(), null);
    }
}