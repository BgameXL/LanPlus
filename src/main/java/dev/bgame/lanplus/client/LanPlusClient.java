package dev.bgame.lanplus.client;

import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.Config;
import dev.bgame.lanplus.api.PlayerIdentity;
import dev.bgame.lanplus.api.RelayTicket;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.slf4j.Logger;

public final class LanPlusClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static LanPlusNetwork network;
    private static PresenceManager presence;
    private static FriendsService friends;
    private static InviteService invites;
    private static RelayTunnel relayTunnel;

    private LanPlusClient() {}

    public static void init() {
        if (presence != null) {
            return;
        }
        network = new HttpLanPlusNetwork(LanPlusClient::backendUrl, LanPlusClient::localIdentity);
        presence = new DefaultPresenceManager(network);
        friends = new DefaultFriendsService(network, LanPlusClient::localIdentity);

        // Relay tunnel: when hosting, opens an internet-reachable tunnel and republishes the public
        // address. Registered before invites so the public domain is set before a join code is minted.
        boolean relayDev = !Config.relayDevAddress.isBlank();
        relayTunnel = new TcpRelayTunnel(relayDev && Config.relayDevPlaintext);
        new RelayHostingCoordinator(presence, relayTunnel, network,
                () -> Config.relayEnabled,
                relayDev ? LanPlusClient::devRelayTicket : null);

        // Subscribes to presence to auto-mint a join code while hosting.
        invites = new DefaultInviteService(network, presence, LanPlusClient::localIdentity, () -> Config.relayEnabled);

        String url = backendUrl();
        LOGGER.info("LAN+ client initialised (backend: {})", url.isBlank() ? "local-only" : url);

        // Prime the friend list and open the WebSocket (no-op in local-only mode).
        friends.connect();
    }

    public static PresenceManager presence() {
        return presence;
    }

    public static FriendsService friends() {
        return friends;
    }

    public static InviteService invites() {
        return invites;
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

    /** Dev-only ticket synthesized from {@code relayDevAddress} (paired with a NO_AUTH relay). */
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

    private static PlayerIdentity localIdentity() {
        User user = Minecraft.getInstance().getUser();
        if (user == null) {
            return null;
        }
        try {
            return new PlayerIdentity(user.getProfileId(), user.getName());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
