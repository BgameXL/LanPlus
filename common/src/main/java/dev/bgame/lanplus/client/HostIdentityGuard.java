package dev.bgame.lanplus.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

public final class HostIdentityGuard {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE_NAME = "lanplus-host.json";

    private HostIdentityGuard() {
    }

    public record Decision(boolean offline, boolean overridden) {
    }

    public static Decision resolve(MinecraftServer server, boolean requestedOffline, Set<UUID> hostIds) {
        Path marker = server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
        Path playerData = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        Boolean committed = read(marker);
        if (committed == null) {
            committed = infer(playerData, hostIds);
        }
        if (committed != null && committed != requestedOffline && hasGuestData(playerData, hostIds)) {
            write(marker, committed);
            return new Decision(committed, true);
        }
        write(marker, requestedOffline);
        return new Decision(requestedOffline, false);
    }

    private static Boolean read(Path marker) {
        if (!Files.isRegularFile(marker)) {
            return null;
        }
        try (BufferedReader reader = Files.newBufferedReader(marker, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            return json.has("offline") ? json.get("offline").getAsBoolean() : null;
        } catch (Exception e) {
            LOGGER.warn("LAN+ host identity marker at {} is unreadable", marker, e);
            return null;
        }
    }

    private static void write(Path marker, boolean offline) {
        JsonObject json = new JsonObject();
        json.addProperty("offline", offline);
        try {
            Files.createDirectories(marker.getParent());
            Path tmp = marker.resolveSibling(FILE_NAME + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                writer.write(json.toString());
            }
            try {
                Files.move(tmp, marker, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, marker, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to write LAN+ host identity marker to {}", marker, e);
        }
    }

    private static Boolean infer(Path playerData, Set<UUID> hostIds) {
        if (!Files.isDirectory(playerData)) {
            return null;
        }
        boolean sawPremium = false;
        boolean sawOffline = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playerData, "*.dat")) {
            for (Path dat : stream) {
                UUID id = parseUuid(dat);
                if (id == null || hostIds.contains(id)) {
                    continue;
                }
                if (id.version() == 3) {
                    sawOffline = true;
                } else {
                    sawPremium = true;
                }
            }
        } catch (IOException e) {
            return null;
        }
        if (sawPremium) {
            return false;
        }
        return sawOffline ? Boolean.TRUE : null;
    }

    private static boolean hasGuestData(Path playerData, Set<UUID> hostIds) {
        if (!Files.isDirectory(playerData)) {
            return false;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(playerData, "*.dat")) {
            for (Path dat : stream) {
                UUID id = parseUuid(dat);
                if (id != null && !hostIds.contains(id)) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private static UUID parseUuid(Path dat) {
        String name = dat.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

