package dev.bgame.lanplus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class AssetCacheTest {

    @TempDir
    Path cacheDir;

    @Test
    void persistsAssetsUsingAHashedFilename() throws IOException {
        String key = "https://cdn.example.test/assets/banner.png?version=2";
        byte[] data = {1, 2, 3, 4};

        new AssetCache(cacheDir).put(key, data);

        assertArrayEquals(data, new AssetCache(cacheDir).get(key));
        try (var files = Files.list(cacheDir)) {
            List<Path> entries = files.toList();
            assertEquals(1, entries.size());
            assertEquals(64, entries.getFirst().getFileName().toString().length());
            assertFalse(entries.getFirst().getFileName().toString().contains("banner"));
        }
    }

    @Test
    void callersCannotMutateCachedBytes() {
        String key = "skin";
        byte[] source = {1, 2, 3};
        AssetCache cache = new AssetCache(cacheDir);

        cache.put(key, source);
        source[0] = 9;
        byte[] firstRead = cache.get(key);
        firstRead[1] = 9;

        assertArrayEquals(new byte[]{1, 2, 3}, cache.get(key));
    }

    @Test
    void missingAndNullAssetsReturnNull() {
        AssetCache cache = new AssetCache(cacheDir);

        assertNull(cache.get("missing"));
        assertNull(cache.get(null));
        cache.put(null, new byte[]{1});
        cache.put("ignored", null);
    }
}