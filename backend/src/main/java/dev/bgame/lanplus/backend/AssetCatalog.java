package dev.bgame.lanplus.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class AssetCatalog {

    static final int MAX_BYTES = 512 * 1024;
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,64}");

    private final Path dir;
    private final String urlPrefix;
    private final Map<String, CachedHash> hashes = new HashMap<>();

    private record CachedHash(String hash, long mtime, long size) {}

    AssetCatalog(Path dir, String urlPrefix) {
        this.dir = dir;
        this.urlPrefix = urlPrefix;
    }

    synchronized List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        List<String> ids = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.forEach(p -> {
                String name = p.getFileName().toString();
                if (name.endsWith(".png")) {
                    ids.add(name.substring(0, name.length() - ".png".length()));
                }
            });
        } catch (IOException e) {
            return out;
        }
        ids.sort(String::compareTo);
        for (String id : ids) {
            String hash = hash(id);
            if (hash != null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", id);
                m.put("url", urlPrefix + id + ".png");
                m.put("hash", hash);
                out.add(m);
            }
        }
        return out;
    }

    synchronized boolean has(String id) {
        return hash(id) != null;
    }

    synchronized String url(String id) {
        return urlPrefix + id + ".png";
    }

    synchronized String hash(String id) {
        Path file = fileOf(id);
        if (file == null) {
            return null;
        }
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            if (!attrs.isRegularFile() || attrs.size() > MAX_BYTES) {
                return null;
            }
            long mtime = attrs.lastModifiedTime().toMillis();
            CachedHash cached = hashes.get(id);
            if (cached != null && cached.mtime == mtime && cached.size == attrs.size()) {
                return cached.hash;
            }
            String hash = Store.sha256Hex(Files.readAllBytes(file));
            hashes.put(id, new CachedHash(hash, mtime, attrs.size()));
            return hash;
        } catch (IOException e) {
            return null;
        }
    }

    synchronized byte[] png(String id) {
        if (hash(id) == null) {
            return null;
        }
        try {
            return Files.readAllBytes(fileOf(id));
        } catch (IOException e) {
            return null;
        }
    }

    private Path fileOf(String id) {
        if (id == null || !ID.matcher(id).matches()) {
            return null;
        }
        return dir.resolve(id + ".png");
    }
}