package dev.bgame.lanplus.client;

import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.api.Friend;
import dev.bgame.lanplus.api.HostAccessMode;
import dev.bgame.lanplus.friends.FriendsService;
import dev.bgame.lanplus.invites.HostAccessControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Opens the active singleplayer world to LAN once it has loaded and applies the chosen
 * {@link HostAccessMode}. The {@link dev.bgame.lanplus.client.gui.HostScreen}
 * arms a request, then this watcher publishes the integrated server on the server thread when it is up.
 *
 * Detection only: the access decision lives in {@link HostAccessControl}.
 * Publishing flips {@code isPublished()}, which {@link ClientPresenceDetector} already turns into
 * HOSTING presence → relay tunnel → join code, so nothing else needs wiring here.
 */
public final class HostController {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long REQUEST_TTL_MS = 60_000;

    private static volatile HostSettings pending;
    private static volatile long pendingAt;
    private static volatile boolean offlineHosting;

    private HostController() {}

    public record HostSettings(HostAccessMode mode, Set<UUID> preInvited, boolean allowNonPremium,
                               GameType gameType, Difficulty difficulty, boolean allowCommands) {

        public static HostSettings defaults(HostAccessMode mode, Set<UUID> preInvited, boolean allowNonPremium) {
            return new HostSettings(mode, preInvited, allowNonPremium, null, null, true);
        }
    }

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
        requestHost(HostSettings.defaults(mode, preInvited, allowNonPremium));
    }

    public static void requestHost(HostSettings settings) {
        pending = settings;
        pendingAt = System.currentTimeMillis();
    }

    /** Called from each loader's client tick event at the END phase. */
    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();

        if (!mc.hasSingleplayerServer()) {
            if (HostAccessControl.isActive()) {
                HostAccessControl.clear();
            }
            offlineHosting = false;
        }

        HostSettings settings = pending;
        if (settings == null) {
            return;
        }
        if (System.currentTimeMillis() - pendingAt > REQUEST_TTL_MS) {
            pending = null;
            return;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        pending = null;
        publish(server, settings);
    }

    private static void publish(IntegratedServer server, HostSettings s) {
        UUID host = localUuid();
        Set<UUID> initial = allowlistFor(s.mode(), s.preInvited());
        offlineHosting = s.allowNonPremium();
        server.execute(() -> {
            HostAccessControl.set(s.mode(), host, initial);
            if (s.allowNonPremium()) {
                server.setUsesAuthentication(false);
            }
            if (s.gameType() != null) {
                server.setDefaultGameType(s.gameType());
            }
            if (s.difficulty() != null) {
                server.setDifficulty(s.difficulty(), false);
            }
            if (!server.isPublished()) {
                boolean ok = server.publishServer(
                        s.gameType() != null ? s.gameType() : server.getDefaultGameType(),
                        s.allowCommands(), HttpUtil.getAvailablePort());
                LOGGER.info("LAN+ opened world to LAN ({}), access={}, nonPremium={}, gameType={}, cheats={}, difficulty={}",
                        ok ? "ok" : "failed", s.mode(), s.allowNonPremium(), s.gameType(), s.allowCommands(), s.difficulty());
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
