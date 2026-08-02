package dev.bgame.lanplus.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;

public final class AssetCache {

    private final Path dir;
    private final ConcurrentHashMap<String, byte[]> memory = new ConcurrentHashMap<>();

    public AssetCache(Path dir) {
        this.dir = dir;
    }

    public byte[] get(String key) {
        if (key == null) {
            return null;
        }
        byte[] cached = memory.get(key);
        if (cached != null) {
            return cached;
        }
        try {
            Path file = fileOf(key);
            if (!Files.isRegularFile(file)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(file);
            memory.put(key, bytes);
            return bytes;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public void put(String key, byte[] data) {
        if (key == null || data == null) {
            return;
        }
        memory.put(key, data);
        try {
            Files.createDirectories(dir);
            Path file = fileOf(key);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(tmp, data);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
        }
    }

    private Path fileOf(String key) {
        return dir.resolve(sha256Hex(key));
    }

    private static String sha256Hex(String key) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}