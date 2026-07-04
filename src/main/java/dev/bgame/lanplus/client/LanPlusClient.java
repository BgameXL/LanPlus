package dev.bgame.lanplus.client;

import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.Config;
import dev.bgame.lanplus.api.Friend;
import dev.bgame.lanplus.api.PlayerIdentity;
import dev.bgame.lanplus.api.RelayTicket;
import dev.bgame.lanplus.api.SkinRef;
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
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private static DiscordPresence discord;
    private static final Map<UUID, SkinRef> resolvedSkinRefs = new ConcurrentHashMap<>();

    private LanPlusClient() {}

    public static void init() {
        if (presence != null) {
            return;
        }
        network = new HttpLanPlusNetwork(LanPlusClient::backendUrl, LanPlusClient::localIdentity,
                new ClientMinecraftAuth());
        presence = new DefaultPresenceManager(network);
        friends = new DefaultFriendsService(network, LanPlusClient::localIdentity);
        profiles = new DefaultProfilesService(network, LanPlusClient::localIdentity);

        skinTextures = new SkinTextures();
        skins = new DefaultSkinService(skinTextures, network);
        friends.addListener(LanPlusClient::resolveFriendSkins);

        discord = new DiscordRichPresence(Config.discordEnabled ? Config.discordAppId : "",
                LanPlusClient::integratedPartySize, LanPlusClient::joinFromDiscord);
        presence.addListener(discord::update);
        discord.update(presence.current());

        boolean relayDev = !Config.relayDevAddress.isBlank();
        relayTunnel = new TcpRelayTunnel(Config.relayDevPlaintext);
        new RelayHostingCoordinator(presence, relayTunnel, network,
                () -> Config.relayEnabled, HostController::isOfflineHosting,
                relayDev ? LanPlusClient::devRelayTicket : null);

        invites = new DefaultInviteService(network, presence, LanPlusClient::localIdentity,
                () -> Config.relayEnabled, HostController::isOfflineHosting);

        String url = backendUrl();
        LOGGER.info("LAN+ client initialised (backend: {})", url.isBlank() ? "local-only" : url);

        friends.connect();
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

    public static SkinService skins() {
        return skins;
    }

    public static SkinTextures skinTextures() {
        return skinTextures;
    }

    public static DiscordPresence discord() {
        return discord;
    }

    private static void resolveFriendSkins(List<Friend> list) {
        if (skins == null) {
            return;
        }
        for (Friend f : list) {
            SkinRef ref = f.skin();
            if (ref != null && !ref.equals(resolvedSkinRefs.put(f.uuid(), ref))) {
                skins.resolve(f.uuid(), ref);
            }
        }
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

    private static void joinFromDiscord(String inviteCode) {
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
