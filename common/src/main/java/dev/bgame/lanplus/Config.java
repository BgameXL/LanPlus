package dev.bgame.lanplus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bgame.lanplus.platform.PlatformHolder;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loader-agnostic configuration for LAN+. Values are stored in
 * {@code <configDir>/lanplus.json}. The file is created automatically with the
 * default values on first load.
 */
public final class Config {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "lanplus.json";

    public static boolean enabled = true;
    public static String backendUrl = "https://backend.lanplus.dev";
    public static int heartbeatSeconds = 15;
    public static boolean relayEnabled = true;
    public static String relayDevAddress = "";
    public static boolean relayDevPlaintext = false;
    public static String skinUrl = "";
    public static boolean skinSlim = false;
    public static boolean skinCustomActive = true;
    public static boolean discordEnabled = true;
    public static String discordAppId = "1516914761626030170";

    private Config() {}

    /** Call once from the loader-specific entry point. */
    public static void load() {
        Path file = PlatformHolder.get().getConfigDir().resolve(FILE_NAME);
        if (Files.isRegularFile(file)) {
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                enabled = getBool(json, "enabled", enabled);
                backendUrl = getString(json, "backendUrl", backendUrl);
                heartbeatSeconds = getInt(json, "heartbeatSeconds", heartbeatSeconds);
                relayEnabled = getBool(json, "relayEnabled", relayEnabled);
                relayDevAddress = getString(json, "relayDevAddress", relayDevAddress);
                relayDevPlaintext = getBool(json, "relayDevPlaintext", relayDevPlaintext);
                skinUrl = getString(json, "skinUrl", skinUrl);
                skinSlim = getBool(json, "skinSlim", skinSlim);
                skinCustomActive = getBool(json, "skinCustomActive", skinCustomActive);
                discordEnabled = getBool(json, "discordEnabled", discordEnabled);
                discordAppId = getString(json, "discordAppId", discordAppId);
            } catch (Exception e) {
                // If the file is malformed, keep the defaults and overwrite on save.
            }
        }
        save();
    }

    /** Persists the current configuration values to disk. */
    public static void save() {
        Path file = PlatformHolder.get().getConfigDir().resolve(FILE_NAME);
        JsonObject json = new JsonObject();
        json.addProperty("enabled", enabled);
        json.addProperty("backendUrl", backendUrl);
        json.addProperty("heartbeatSeconds", heartbeatSeconds);
        json.addProperty("relayEnabled", relayEnabled);
        json.addProperty("relayDevAddress", relayDevAddress);
        json.addProperty("relayDevPlaintext", relayDevPlaintext);
        json.addProperty("skinUrl", skinUrl);
        json.addProperty("skinSlim", skinSlim);
        json.addProperty("skinCustomActive", skinCustomActive);
        json.addProperty("discordEnabled", discordEnabled);
        json.addProperty("discordAppId", discordAppId);

        try {
            Files.createDirectories(file.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            // Best-effort: if we can't write, the game can still run with in-memory defaults.
        }
    }

    public static void setSkin(String url, boolean slim) {
        skinUrl = url == null ? "" : url;
        skinSlim = slim;
        skinCustomActive = true;
        save();
    }

    public static void setSkinCustomActive(boolean active) {
        skinCustomActive = active;
        save();
    }

    private static boolean getBool(JsonObject json, String key, boolean fallback) {
        if (!json.has(key) || json.get(key).isJsonNull()) return fallback;
        try {
            return json.get(key).getAsBoolean();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        if (!json.has(key) || json.get(key).isJsonNull()) return fallback;
        try {
            return json.get(key).getAsInt();
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    private static String getString(JsonObject json, String key, String fallback) {
        if (!json.has(key) || json.get(key).isJsonNull()) return fallback;
        try {
            return json.get(key).getAsString();
        } catch (RuntimeException e) {
            return fallback;
        }
    }
}
