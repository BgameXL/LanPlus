package dev.bgame.lanplus.client;

import dev.bgame.lanplus.Config;
import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.SkinRef;
import dev.bgame.lanplus.api.SkinType;
import dev.bgame.lanplus.platform.PlatformHolder;
import dev.bgame.lanplus.presence.PresenceManager;
import dev.bgame.lanplus.skins.SkinService;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.util.GsonHelper;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

public final class ClientPresenceDetector {

    private static final String MODPACK_CONFIG_FILE = "lanplus-modpack.json";
    private static int tickCounter = 0;
    private static GameplayState lastState = null;
    private static SkinRef lastSkin = null;
    private static String lastModpack = null;
    private static boolean modpackCached = false;
    private static String cachedModpack = null;
    private static String lastGameMode = null;
    private static String lastDifficulty = null;
    private static boolean lastAllowCommands = false;

    private ClientPresenceDetector() {
    }

    public static void onClientTick() {
        PresenceManager presence = LanPlusClient.presence();
        if (presence == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        GameplayState state = detectState(mc);
        if (state != lastState) {
            lastState = state;
            publishSkinIfChanged(mc, presence);
            publishModpackIfChanged(mc, presence);
            publishWorldIfChanged(mc, presence, state);
            presence.updateState(state, detectWorldName(mc, state), detectAddress(mc, state));
        }

        int intervalTicks = Math.max(5, Config.heartbeatSeconds) * 20;
        if (++tickCounter >= intervalTicks) {
            tickCounter = 0;
            publishSkinIfChanged(mc, presence);
            publishModpackIfChanged(mc, presence);
            publishWorldIfChanged(mc, presence, state);
            presence.heartbeat();
        }
    }

    public static void onLogout() {
        PresenceManager presence = LanPlusClient.presence();
        if (presence == null) {
            return;
        }
        lastState = GameplayState.MENU;
        lastModpack = null;
        modpackCached = false;
        cachedModpack = null;
        lastGameMode = null;
        lastDifficulty = null;
        lastAllowCommands = false;
        presence.updateModpack(null);
        presence.updateWorld(null, null, false);
        presence.updateState(GameplayState.MENU, null, null);
    }

    private static void publishModpackIfChanged(Minecraft mc, PresenceManager presence) {
        String id = detectModpackId(mc);
        if (Objects.equals(id, lastModpack)) {
            return;
        }
        lastModpack = id;
        presence.updateModpack(id);
    }

    private static String detectModpackId(Minecraft mc) {
        if (mc.level == null || !mc.hasSingleplayerServer()) {
            modpackCached = false;
            cachedModpack = null;
            return null;
        }
        if (modpackCached) {
            return cachedModpack;
        }
        cachedModpack = readModpackId();
        modpackCached = true;
        return cachedModpack;
    }

    private static String readModpackId() {
        Path file = PlatformHolder.get().getConfigDir().resolve(MODPACK_CONFIG_FILE);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject obj = GsonHelper.parse(reader);
            String id = GsonHelper.getAsString(obj, "modpackId", null);
            return id == null || id.isBlank() ? null : id.trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static void publishWorldIfChanged(Minecraft mc, PresenceManager presence, GameplayState state) {
        String gameMode = detectGameMode(mc, state);
        String difficulty = detectDifficulty(mc, state);
        boolean allowCommands = detectAllowCommands(mc, state);
        if (Objects.equals(gameMode, lastGameMode) && Objects.equals(difficulty, lastDifficulty)
                && allowCommands == lastAllowCommands) {
            return;
        }
        lastGameMode = gameMode;
        lastDifficulty = difficulty;
        lastAllowCommands = allowCommands;
        presence.updateWorld(gameMode, difficulty, allowCommands);
    }

    private static String detectGameMode(Minecraft mc, GameplayState state) {
        if (state != GameplayState.HOSTING) {
            return null;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        return server == null ? null : server.getDefaultGameType().name();
    }

    private static String detectDifficulty(Minecraft mc, GameplayState state) {
        if (state != GameplayState.HOSTING) {
            return null;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        return server == null ? null : server.getWorldData().getDifficulty().name();
    }

    private static boolean detectAllowCommands(Minecraft mc, GameplayState state) {
        if (state != GameplayState.HOSTING) {
            return false;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        return server != null && server.getWorldData().isAllowCommands();
    }

    private static void publishSkinIfChanged(Minecraft mc, PresenceManager presence) {
        SkinRef ref = detectSkin();
        if (Objects.equals(ref, lastSkin)) {
            return;
        }
        lastSkin = ref;
        SkinService skins = LanPlusClient.skins();
        if (skins != null) {
            UUID self = LanPlusClient.selfUuid();
            if (self != null && ref != null) {
                skins.resolve(self, ref);
                User user = mc.getUser();
                UUID launcher = user == null ? null : user.getProfileId();
                if (launcher != null && !launcher.equals(self)) {
                    skins.resolve(launcher, ref);
                }
            }
        }
        presence.updateSkin(ref);
    }

    private static SkinRef detectSkin() {
        if (Config.skinCustomActive && !Config.skinUrl.isBlank()) {
            return new SkinRef(SkinType.CUSTOM, Config.skinUrl, null, Config.skinSlim ? "slim" : null);
        }
        UUID self = LanPlusClient.selfUuid();
        return self == null ? null : new SkinRef(SkinType.MOJANG, self.toString(), null, null);
    }

    private static GameplayState detectState(Minecraft mc) {
        if (mc.level == null) {
            return GameplayState.MENU;
        }
        if (mc.hasSingleplayerServer()) {
            IntegratedServer server = mc.getSingleplayerServer();
            return server != null && server.isPublished() ? GameplayState.HOSTING : GameplayState.SINGLEPLAYER;
        }
        return GameplayState.MULTIPLAYER;
    }

    private static String detectWorldName(Minecraft mc, GameplayState state) {
        return switch (state) {
            case SINGLEPLAYER, HOSTING -> {
                IntegratedServer server = mc.getSingleplayerServer();
                yield server == null ? null : server.getWorldData().getLevelName();
            }
            case MULTIPLAYER -> {
                ServerData data = mc.getCurrentServer();
                yield data == null ? null : data.name;
            }
            default -> null;
        };
    }

    private static String detectAddress(Minecraft mc, GameplayState state) {
        return switch (state) {
            case HOSTING -> {
                IntegratedServer server = mc.getSingleplayerServer();
                yield server == null ? null : "localhost:" + server.getPort();
            }
            case MULTIPLAYER -> {
                ServerData data = mc.getCurrentServer();
                yield data == null ? null : data.ip;
            }
            default -> null;
        };
    }
}
