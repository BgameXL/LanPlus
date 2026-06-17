package dev.bgame.lanplus.client;

import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.Lanplus;
import dev.bgame.lanplus.api.Friend;
import dev.bgame.lanplus.api.HostAccessMode;
import dev.bgame.lanplus.friends.FriendsService;
import dev.bgame.lanplus.invites.HostAccessControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.util.HttpUtil;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Opens the active singleplayer world to LAN once it has loaded and applies the chosen
 * {@link HostAccessMode}. The {@link dev.bgame.lanplus.client.gui.HostScreen}
 * arms a request, then this watcher publishes the integrated server on the server thread when it is up.
 *
 * Detection only (architecture rule #5): the access decision lives in {@link HostAccessControl}.
 * Publishing flips {@code isPublished()}, which {@link ClientPresenceDetector} already turns into
 * HOSTING presence → relay tunnel → join code, so nothing else needs wiring here.
 */
@Mod.EventBusSubscriber(modid = Lanplus.MODID, value = Dist.CLIENT)
public final class HostController {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long REQUEST_TTL_MS = 60_000;

    private static volatile HostAccessMode pendingMode;
    private static volatile Set<UUID> pendingInvites = Set.of();
    private static volatile boolean pendingAllowNonPremium;
    private static volatile long pendingAt;
    private static volatile boolean offlineHosting;

    private HostController() {}

    public static boolean isOfflineHosting() {
        return offlineHosting;
    }

    /**
     * Called right before loading the world: publish to LAN with this mode once up.
     * {@code preInvited} seeds the allow list (friends picked in the invite overlay).
     * {@code allowNonPremium} opens the integrated server in offline-mode so non-premium clients can
     * join (only meaningful with EVERYONE access — the uuid gate is useless once uuids are spoofable).
     */
    public static void requestHost(HostAccessMode mode, Set<UUID> preInvited, boolean allowNonPremium) {
        pendingMode = mode;
        pendingInvites = preInvited == null ? Set.of() : Set.copyOf(preInvited);
        pendingAllowNonPremium = allowNonPremium;
        pendingAt = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();

        if (!mc.hasSingleplayerServer()) {
            if (HostAccessControl.isActive()) {
                HostAccessControl.clear();
            }
            offlineHosting = false;
        }

        HostAccessMode mode = pendingMode;
        if (mode == null) {
            return;
        }
        if (System.currentTimeMillis() - pendingAt > REQUEST_TTL_MS) {
            pendingMode = null;
            return;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        Set<UUID> preInvited = pendingInvites;
        boolean allowNonPremium = pendingAllowNonPremium;
        pendingMode = null;
        pendingInvites = Set.of();
        pendingAllowNonPremium = false;
        publish(server, mode, preInvited, allowNonPremium);
    }

    private static void publish(IntegratedServer server, HostAccessMode mode, Set<UUID> preInvited,
                                boolean allowNonPremium) {
        UUID host = localUuid();
        Set<UUID> initial = allowlistFor(mode, preInvited);
        offlineHosting = allowNonPremium;
        server.execute(() -> {
            HostAccessControl.set(mode, host, initial);
            if (allowNonPremium) {
                server.setUsesAuthentication(false);
            }
            if (!server.isPublished()) {
                boolean ok = server.publishServer(server.getDefaultGameType(), true, HttpUtil.getAvailablePort());
                LOGGER.info("LAN+ opened world to LAN ({}), access={}, nonPremium={}",
                        ok ? "ok" : "failed", mode, allowNonPremium);
            }
        });
    }

    private static Set<UUID> allowlistFor(HostAccessMode mode, Set<UUID> preInvited) {
        Set<UUID> set = new HashSet<>(preInvited);
        if (mode == HostAccessMode.FRIENDS) {
            FriendsService friends = LanPlusClient.friends();
            if (friends != null) {
                for (Friend f : friends.friends()) {
                    set.add(f.uuid());
                }
            }
        }
        return set;
    }

    private static UUID localUuid() {
        try {
            return Minecraft.getInstance().getUser().getProfileId();
        } catch (RuntimeException e) {
            return null;
        }
    }
}