package dev.bgame.lanplus.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

final class AssetHash {

    record Cached(String hash, long mtime, long size) {
    }

    private AssetHash() {
    }

    static <K> String cached(Path file, K key, Map<K, Cached> cache, int maxBytes) {
        if (file == null) {
            return null;
        }
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            if (!attrs.isRegularFile() || attrs.size() > maxBytes) {
                return null;
            }
            long mtime = attrs.lastModifiedTime().toMillis();
            Cached hit = cache.get(key);
            if (hit != null && hit.mtime() == mtime && hit.size() == attrs.size()) {
                return hit.hash();
            }
            String hash = Store.sha256Hex(Files.readAllBytes(file));
            cache.put(key, new Cached(hash, mtime, attrs.size()));
            return hash;
        } catch (IOException e) {
            return null;
        }
    }
}