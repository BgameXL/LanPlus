package dev.bgame.lanplus.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProfileCache {

    private final Path dir;
    private final ConcurrentHashMap<UUID, String> memory = new ConcurrentHashMap<>();

    public ProfileCache(Path dir) {
        this.dir = dir;
    }

    public String get(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        String cached = memory.get(uuid);
        if (cached != null) {
            return cached;
        }
        try {
            Path file = dir.resolve(uuid + ".json");
            if (!Files.isRegularFile(file)) {
                return null;
            }
            String body = Files.readString(file, StandardCharsets.UTF_8);
            memory.put(uuid, body);
            return body;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public void put(UUID uuid, String body) {
        if (uuid == null || body == null) {
            return;
        }
        memory.put(uuid, body);
        try {
            Files.createDirectories(dir);
            Path file = dir.resolve(uuid + ".json");
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, body, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
        }
    }
}
