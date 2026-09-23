package dev.bgame.lanplus.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class CosmeticAssets {

    static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Pattern ID = Pattern.compile("[a-z0-9_-]{1,64}");
    private static final Map<String, String> FILES = Map.of(
            "geo", "model.geo.json", "anim", "animation.json",
            "tex", "texture.png", "meta", "meta.json");

    private final Path dir;
    private final Map<Path, AssetHash.Cached> hashes = new HashMap<>();

    CosmeticAssets(Path dir) {
        this.dir = dir;
    }

    synchronized List<Map<String, Object>> catalog() {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        List<String> ids = new ArrayList<>();
        try (Stream<Path> entries = Files.list(dir)) {
            entries.forEach(p -> {
                String name = p.getFileName().toString();
                if (Files.isDirectory(p) && ID.matcher(name).matches()) {
                    ids.add(name);
                }
            });
        } catch (IOException e) {
            return out;
        }
        ids.sort(String::compareTo);
        for (String id : ids) {
            String geoHash = hash(id, "geo");
            String texHash = hash(id, "tex");
            if (geoHash == null || texHash == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("meta", readText(id));
            m.put("geoUrl", "/cosmetics/asset/" + id + "/geo");
            m.put("geoHash", geoHash);
            String animHash = hash(id, "anim");
            if (animHash != null) {
                m.put("animUrl", "/cosmetics/asset/" + id + "/anim");
                m.put("animHash", animHash);
            }
            m.put("texUrl", "/cosmetics/asset/" + id + "/tex");
            m.put("texHash", texHash);
            out.add(m);
        }
        return out;
    }

    static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    synchronized boolean writeBundle(String id, byte[] geo, byte[] texture, byte[] animation, byte[] meta) {
        if (!validId(id) || geo == null || texture == null
                || geo.length == 0 || geo.length > MAX_BYTES
                || texture.length == 0 || texture.length > MAX_BYTES
                || (animation != null && animation.length > MAX_BYTES)
                || (meta != null && meta.length > MAX_BYTES)) {
            return false;
        }
        try {
            Path base = dir.resolve(id);
            Files.createDirectories(base);
            Files.write(base.resolve("model.geo.json"), geo);
            Files.write(base.resolve("texture.png"), texture);
            if (animation != null && animation.length > 0) {
                Files.write(base.resolve("animation.json"), animation);
            }
            if (meta != null && meta.length > 0) {
                Files.write(base.resolve("meta.json"), meta);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    synchronized boolean delete(String id) {
        if (!validId(id)) {
            return false;
        }
        Path base = dir.resolve(id);
        if (!Files.isDirectory(base)) {
            return false;
        }
        try (Stream<Path> tree = Files.walk(base)) {
            tree.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    synchronized byte[] file(String id, String name) {
        Path f = fileOf(id, name);
        if (f == null || hashOf(f) == null) {
            return null;
        }
        try {
            return Files.readAllBytes(f);
        } catch (IOException e) {
            return null;
        }
    }

    static String contentType(String name) {
        return "tex".equals(name) ? "image/png" : "application/json";
    }

    private String readText(String id) {
        Path f = fileOf(id, "meta");
        if (f == null || !Files.isRegularFile(f)) {
            return "";
        }
        try {
            return Files.size(f) > MAX_BYTES ? "" : Files.readString(f);
        } catch (IOException e) {
            return "";
        }
    }

    private String hash(String id, String name) {
        return hashOf(fileOf(id, name));
    }

    private String hashOf(Path file) {
        return AssetHash.cached(file, file, hashes, MAX_BYTES);
    }

    private Path fileOf(String id, String name) {
        String fn = FILES.get(name);
        if (id == null || fn == null || !ID.matcher(id).matches()) {
            return null;
        }
        return dir.resolve(id).resolve(fn);
    }
}