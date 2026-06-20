package dev.bgame.lanplus.client;

import dev.bgame.lanplus.Config;
import dev.bgame.lanplus.Lanplus;
import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.SkinRef;
import dev.bgame.lanplus.api.SkinType;
import dev.bgame.lanplus.presence.PresenceManager;
import dev.bgame.lanplus.skins.SkinService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Objects;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Lanplus.MODID, value = Dist.CLIENT)
public final class ClientPresenceDetector {

    private static int tickCounter = 0;
    private static GameplayState lastState = null;
    private static SkinRef lastSkin = null;

    private ClientPresenceDetector() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        PresenceManager presence = LanPlusClient.presence();
        if (presence == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        GameplayState state = detectState(mc);
        if (state != lastState) {
            lastState = state;
            publishSkinIfChanged(mc, presence);
            presence.updateState(state, detectWorldName(mc, state), detectAddress(mc, state));
        }

        int intervalTicks = Math.max(5, Config.heartbeatSeconds) * 20;
        if (++tickCounter >= intervalTicks) {
            tickCounter = 0;
            publishSkinIfChanged(mc, presence);
            presence.heartbeat();
        }
    }

    private static void publishSkinIfChanged(Minecraft mc, PresenceManager presence) {
        SkinRef ref = detectSkin(mc);
        if (Objects.equals(ref, lastSkin)) {
            return;
        }
        lastSkin = ref;
        SkinService skins = LanPlusClient.skins();
        if (skins != null) {
            skins.setLocalSkin(ref);
            // Resolve our own skin into SkinTextures so the UI avatars (ProfileScreen / FriendsScreen)
            // can show it. SkinTextures is otherwise only populated from friends' presence, so without
            // this our own profile head falls back to the default skin. In-world self rendering stays
            // vanilla (the AbstractClientPlayerMixin skips the local player).
            User user = mc.getUser();
            UUID self = user == null ? null : user.getProfileId();
            if (self != null && ref != null) {
                skins.resolve(self, ref);
            }
        }
        presence.updateSkin(ref);
    }

    private static SkinRef detectSkin(Minecraft mc) {
        if (!Config.skinUrl.isBlank()) {
            return new SkinRef(SkinType.CUSTOM, Config.skinUrl, null, Config.skinSlim ? "slim" : null);
        }
        User user = mc.getUser();
        if (user == null) {
            return null;
        }
        try {
            return new SkinRef(SkinType.MOJANG, user.getProfileId().toString(), null, null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PresenceManager presence = LanPlusClient.presence();
        if (presence == null) {
            return;
        }
        lastState = GameplayState.MENU;
        presence.updateState(GameplayState.MENU, null, null);
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
