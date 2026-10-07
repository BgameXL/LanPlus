package dev.bgame.lanplus.client;

import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.Config;
import dev.bgame.lanplus.announcements.AnnouncementsService;
import dev.bgame.lanplus.announcements.DefaultAnnouncementsService;
import dev.bgame.lanplus.api.Announcement;
import dev.bgame.lanplus.api.CosmeticShop;
import dev.bgame.lanplus.api.Friend;
import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.PlayerIdentity;
import dev.bgame.lanplus.api.RelayTicket;
import dev.bgame.lanplus.api.SkinRef;
import dev.bgame.lanplus.client.gui.FriendsScreen;
import dev.bgame.lanplus.client.gui.LanPlusNotifications;
import dev.bgame.lanplus.client.gui.UpdateScreen;
import dev.bgame.lanplus.core.AssetCache;
import dev.bgame.lanplus.core.ProfileCache;
import dev.bgame.lanplus.cosmetics.CosmeticMeta;
import dev.bgame.lanplus.cosmetics.CosmeticSlot;
import dev.bgame.lanplus.discord.DiscordPresence;
import dev.bgame.lanplus.discord.DiscordRichPresence;
import dev.bgame.lanplus.friends.DefaultFriendsService;
import dev.bgame.lanplus.friends.FriendsService;
import dev.bgame.lanplus.invites.DefaultInviteService;
import dev.bgame.lanplus.invites.InviteService;
import dev.bgame.lanplus.network.HttpLanPlusNetwork;
import dev.bgame.lanplus.network.LanPlusNetwork;
import dev.bgame.lanplus.network.RelayHostingCoordinator;
import dev.bgame.lanplus.network.RelayTunnel;
import dev.bgame.lanplus.network.SvcBridge;
import dev.bgame.lanplus.network.TcpRelayTunnel;
import dev.bgame.lanplus.presence.DefaultPresenceManager;
import dev.bgame.lanplus.presence.PresenceManager;
import dev.bgame.lanplus.profiles.DefaultProfilesService;
import dev.bgame.lanplus.profiles.ProfilesService;
import dev.bgame.lanplus.skins.DefaultSkinService;
import dev.bgame.lanplus.skins.SkinService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.server.IntegratedServer;
import dev.bgame.lanplus.platform.PlatformHolder;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public final class LanPlusClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static LanPlusNetwork network;
    private static PresenceManager presence;
    private static FriendsService friends;
    private static ProfilesService profiles;
    private static InviteService invites;
    private static RelayTunnel relayTunnel;
    private static SkinService skins;
    private static SkinTextures skinTextures;
    private static CosmeticModels cosmetics;
    private static CosmeticAssetLoader cosmeticAssets;
    private static DiscordPresence discord;
    private static AnnouncementsService announcements;
    private static final Map<UUID, SkinRef> resolvedSkinRefs = new ConcurrentHashMap<>();
    private static final Set<UUID> requestedLoadouts = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> requestedSkins = ConcurrentHashMap.newKeySet();
    private static volatile String pendingUpdateVersion;
    private static volatile String pendingUpdateUrl;

    private LanPlusClient() {
    }

    public static void init() {
        if (presence != null) {
            return;
        }
        network = new HttpLanPlusNetwork(LanPlusClient::backendUrl, LanPlusClient::localIdentity, new ClientMinecraftAuth());
        AssetCache assetCache = new AssetCache(PlatformHolder.get().getGameDir().resolve("lanplus/cache/assets"));
        ProfileCache profileCache = new ProfileCache(PlatformHolder.get().getGameDir().resolve("lanplus/cache/profiles"));
        presence = new DefaultPresenceManager(network);
        friends = new DefaultFriendsService(network, LanPlusClient::localIdentity);
        profiles = new DefaultProfilesService(network, LanPlusClient::localIdentity, assetCache, profileCache);

        skinTextures = new SkinTextures();
        skins = new DefaultSkinService(skinTextures, network, assetCache);
        cosmetics = new CosmeticModels();
        cosmeticAssets = new CosmeticAssetLoader(network, cosmetics, assetCache);
        loadDevCosmetics();
        friends.addListener(LanPlusClient::resolveFriendSkins);
        friends.addListener(new SocialToastListener());

        discord = new DiscordRichPresence(Config.discordAppId, Config.discordEnabled, LanPlusClient::integratedPartySize, LanPlusClient::joinByInviteCode);
        presence.addListener(discord::update);
        discord.update(presence.current());

        boolean relayDev = !Config.relayDevAddress.isBlank();
        relayTunnel = new TcpRelayTunnel(Config.relayDevPlaintext);
        new RelayHostingCoordinator(presence, relayTunnel, network, () -> Config.relayEnabled, HostController::isOfflineHosting, relayDev ? LanPlusClient::devRelayTicket : null);

        if (Config.voiceEnabled) {
            SvcBridge.applyVoiceHost(Config.voiceHost);
        }

        invites = new DefaultInviteService(network, presence, LanPlusClient::localIdentity, () -> Config.relayEnabled, HostController::isOfflineHosting);

        announcements = new DefaultAnnouncementsService(network, LanPlusClient::localIdentity);
        announcements.addListener(new AnnouncementsService.AnnouncementsListener() {
            @Override
            public void onAnnouncementsChanged(List<Announcement> list) {
            }

            @Override
            public void onNewAnnouncement(Announcement announcement) {
                LanPlusNotifications.announcement(announcement);
            }
        });

        network.addEventListener(new LanPlusNetwork.BackendEventListener() {
            @Override
            public void onTestNotification(String title, String body) {
                LanPlusNotifications.test(title, body);
            }

            @Override
            public void onVersionInfo(String latest, String url) {
                if (!isOutdated(PlatformHolder.get().modVersion(), latest)
                        || latest.equals(Config.seenUpdateVersion)) {
                    return;
                }
                pendingUpdateVersion = latest;
                pendingUpdateUrl = url;
            }
        });

        String url = backendUrl();
        LOGGER.info("LAN+ client initialised (backend: {})", url.isBlank() ? "local-only" : url);

        friends.connect();
        announcements.connect();
        ensureCosmeticLoadout(selfUuid());
        cosmeticAssets.refreshCatalog();
    }

    private static boolean isOutdated(String current, String latest) {
        if (current == null || current.isBlank() || latest == null || latest.isBlank()) {
            return false;
        }
        String[] pa = current.split("[.\\-+]");
        String[] pb = latest.split("[.\\-+]");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = i < pa.length ? leadingInt(pa[i]) : 0;
            int vb = i < pb.length ? leadingInt(pb[i]) : 0;
            if (va != vb) {
                return va < vb;
            }
        }
        return false;
    }

    private static int leadingInt(String s) {
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
        }
        return i == 0 ? 0 : Integer.parseInt(s.substring(0, i));
    }

    public static void onClientTick() {
        String version = pendingUpdateVersion;
        if (version == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean atMenu = mc.level == null && mc.screen != null;
        boolean inWorld = mc.level != null;
        if (!atMenu && !inWorld) {
            return;
        }
        String url = pendingUpdateUrl;
        pendingUpdateVersion = null;
        pendingUpdateUrl = null;
        Config.seenUpdateVersion = version;
        Config.save();
        if (atMenu) {
            mc.setScreen(new UpdateScreen(mc.screen, version, url));
        } else {
            LanPlusNotifications.updateAvailable(version, url);
        }
    }

    public static PresenceManager presence() {
        return presence;
    }

    public static FriendsService friends() {
        return friends;
    }

    public static ProfilesService profiles() {
        return profiles;
    }

    public static InviteService invites() {
        return invites;
    }

    public static boolean isHostingViaRelay() {
        return relayTunnel != null && relayTunnel.isOpen();
    }

    private static volatile java.util.UUID joinedHost;

    public static void setJoinedHost(java.util.UUID uuid) {
        joinedHost = uuid;
    }

    public static java.util.UUID joinedHost() {
        return joinedHost;
    }

    public static SkinService skins() {
        return skins;
    }

    public static SkinTextures skinTextures() {
        return skinTextures;
    }

    public static CosmeticModels cosmetics() {
        return cosmetics;
    }

    public static void ensureCosmeticModel(String cosmeticId) {
        if (cosmeticAssets != null) {
            cosmeticAssets.ensureModel(cosmeticId);
        }
    }

    public static void ensureCosmeticLoadout(UUID player) {
        if (player == null || cosmetics == null || network == null || !requestedLoadouts.add(player)) {
            return;
        }
        network.getCosmeticLoadout(player).whenComplete((map, err) -> {
            if (err != null || map == null) {
                return;
            }
            Map<CosmeticSlot, String> parsed = new EnumMap<>(CosmeticSlot.class);
            for (Map.Entry<String, String> e : map.entrySet()) {
                try {
                    parsed.put(CosmeticSlot.valueOf(e.getKey()), e.getValue());
                } catch (IllegalArgumentException ignored) {
                }
            }
            cosmetics.applyLoadout(player, parsed);
        });
    }

    public static void ensureCosmeticShop() {
        if (cosmetics == null || network == null) {
            return;
        }
        network.getCosmeticShop().whenComplete((shop, err) -> {
            if (err == null && shop != null) {
                applyShop(shop);
            }
        });
    }

    public static void refreshCosmetics() {
        if (cosmeticAssets != null) {
            cosmeticAssets.refreshCatalog();
        }
        ensureCosmeticShop();
        UUID self = selfUuid();
        if (self != null) {
            requestedLoadouts.remove(self);
            ensureCosmeticLoadout(self);
        }
    }

    public static void completePurchase(UUID player, String id) {
        if (cosmetics == null || network == null || id == null) {
            return;
        }
        CosmeticSlot slot = cosmetics.slotOf(id);
        network.purchaseCosmetic(id).whenComplete((shop, err) -> {
            if (shop != null) {
                applyShop(shop);
                network.equipCosmetic(slot.name(), id);
                return;
            }
            network.getCosmeticShop().whenComplete((auth, e2) -> {
                if (auth == null) {
                    return;
                }
                applyShop(auth);
                if (player != null && !auth.owned().contains(id)) {
                    cosmetics.unequip(player, slot);
                }
            });
        });
    }

    private static void applyShop(CosmeticShop shop) {
        cosmetics.setWallet(shop.balance());
        cosmetics.setOwned(new HashSet<>(shop.owned()));
    }

    private static void loadDevCosmetics() {
        try {
            Path dir = PlatformHolder.get().getConfigDir().resolve("lanplus-cosmetics");
            if (!Files.isDirectory(dir)) {
                return;
            }
            List<Path> geoFiles;
            try (Stream<Path> files = Files.list(dir)) {
                geoFiles = files.filter(p -> p.getFileName().toString().endsWith(".geo.json")).toList();
            }
            UUID self = selfUuid();
            for (Path geo : geoFiles) {
                String id = geo.getFileName().toString().replace(".geo.json", "");
                Path anim = dir.resolve(id + ".animation.json");
                Path png = dir.resolve(id + ".png");
                Path metaFile = dir.resolve(id + ".cosmetic.json");
                cosmetics.register(id, Files.readAllBytes(geo), Files.isRegularFile(anim) ? Files.readAllBytes(anim) : null, Files.isRegularFile(png) ? Files.readAllBytes(png) : null);
                CosmeticMeta meta = CosmeticMeta.parse(id, Files.isRegularFile(metaFile) ? Files.readString(metaFile) : null);
                cosmetics.putMeta(meta);
                if (meta.price() <= 0) {
                    cosmetics.markOwned(id);
                    if (self != null) {
                        cosmetics.equip(self, meta.slot(), id);
                    }
                }
            }
            cosmetics.setWallet(5000);
        } catch (Exception e) {
            LOGGER.warn("cosmetic failed", e);
        }
    }

    public static DiscordPresence discord() {
        return discord;
    }

    public static AnnouncementsService announcements() {
        return announcements;
    }

    public static void setDiscordEnabled(boolean on) {
        if (discord != null) {
            discord.setEnabled(on);
        }
    }

    public static void setEnabled(boolean enabled) {
        Config.enabled = enabled;
        if (network == null) {
            return;
        }
        if (!enabled) {
            network.disconnect();
            if (relayTunnel != null) {
                relayTunnel.close();
            }
            return;
        }
        if (friends != null) {
            friends.connect();
        }
        if (announcements != null) {
            announcements.connect();
        }
    }

    public static void resolveFriendSkins(List<Friend> list) {
        if (skins == null) {
            return;
        }
        for (Friend f : list) {
            SkinRef ref = f.skin();
            if (ref != null && !ref.equals(resolvedSkinRefs.put(f.uuid(), ref))) {
                skins.resolve(f.uuid(), ref);
                skins.resolve(HostController.offlineUuid(f.username()), ref);
            }
        }
    }

    public static void ensureSkin(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank() || skins == null || network == null
                || skinTextures == null || skinTextures.get(uuid) != null || !requestedSkins.add(uuid)) {
            return;
        }
        network.getSkinByName(name).whenComplete((ref, err) -> {
            if (err == null && ref != null) {
                skins.resolve(uuid, ref);
            } else {
                skins.resolveByName(uuid, name);
            }
        });
    }

    public static LanPlusNetwork network() {
        return network;
    }

    private static String backendUrl() {
        if (!Config.enabled || Config.backendUrl == null) {
            return "";
        }
        return Config.backendUrl;
    }

    private static RelayTicket devRelayTicket() {
        String addr = Config.relayDevAddress;
        int colon = addr.lastIndexOf(':');
        if (colon < 0) {
            return null;
        }
        try {
            String host = addr.substring(0, colon).trim();
            int port = Integer.parseInt(addr.substring(colon + 1).trim());
            return new RelayTicket("dev", host, port, null, 0);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static UUID selfUuid() {
        if (network != null) {
            UUID session = network.sessionUuid();
            if (session != null) {
                return session;
            }
        }
        try {
            User user = Minecraft.getInstance().getUser();
            return user == null ? null : user.getProfileId();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static PlayerIdentity localIdentity() {
        User user = Minecraft.getInstance().getUser();
        if (user == null) {
            return null;
        }
        try {
            UUID uuid = selfUuid();
            return uuid == null ? null : new PlayerIdentity(uuid, user.getName());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int[] integratedPartySize() {
        try {
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null || !server.isPublished()) {
                return null;
            }
            return new int[]{server.getPlayerCount(), server.getMaxPlayers()};
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final class SocialToastListener implements FriendsService.FriendsListener {
        private static final Set<UUID> lastJoinable = ConcurrentHashMap.newKeySet();
        private static boolean joinablePrimed;

        @Override
        public void onFriendsChanged(List<Friend> list) {
            Set<UUID> nowJoinable = new HashSet<>();
            for (Friend f : list) {
                if (f.state() == GameplayState.HOSTING && f.joinCode() != null) {
                    nowJoinable.add(f.uuid());
                    if (joinablePrimed && !lastJoinable.contains(f.uuid())) {
                        FriendNotifications.invited(f.uuid());
                    }
                }
            }
            lastJoinable.clear();
            lastJoinable.addAll(nowJoinable);
            joinablePrimed = true;
            FriendNotifications.retain(nowJoinable);
        }

        @Override
        public void onFriendStartedHosting(UUID uuid, String joinCode) {
            if (joinCode != null && !joinCode.isBlank()) {
                FriendNotifications.invited(uuid);
            }
            LanPlusNotifications.friendHosting(uuid, usernameOf(uuid), joinCode);
        }

        @Override
        public void onFriendRequest(UUID fromUuid, String fromUsername) {
            String name = fromUsername != null && !fromUsername.isBlank() ? fromUsername : usernameOf(fromUuid);
            LanPlusNotifications.friendRequest(fromUuid, name);
        }

        private static String usernameOf(UUID uuid) {
            if (friends != null && uuid != null) {
                for (Friend f : friends.friends()) {
                    if (uuid.equals(f.uuid())) {
                        return f.username();
                    }
                }
            }
            return "?";
        }
    }

    public static void openFriendsFocused(UUID uuid) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new FriendsScreen(mc.screen, uuid)));
    }

    public static void joinByInviteCode(String inviteCode) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            InviteService inviteService = invites;
            if (inviteService == null || inviteCode == null || inviteCode.isBlank()) {
                return;
            }
            inviteService.resolve(inviteCode).whenComplete((invite, err) -> mc.execute(() -> {
                if (err == null && invite != null) {
                    JoinHelper.connect(mc, invite.address());
                }
            }));
        });
    }
}
