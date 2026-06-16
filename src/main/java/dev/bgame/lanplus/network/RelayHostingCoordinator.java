package dev.bgame.lanplus.network;

import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.PresenceSnapshot;
import dev.bgame.lanplus.api.RelayTicket;
import dev.bgame.lanplus.presence.PresenceManager;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Drives the relay {@link RelayTunnel} from presence state.
 * When the local player starts HOSTING, it requests a ticket, opens the tunnel, and republishes the assigned
 * public domain as the presence address — so {@code invites/} mints a join code that points at the
 * relay instead of the unreachable {@code localhost} address. When hosting stops, it closes the tunnel.
 *
 * Detection stays dumb: this only reacts to the gameplay state the detector
 * reports. Fail-soft : if no ticket/tunnel is available, the loopback address is left
 * untouched and hosting is LAN-only.
 */
public final class RelayHostingCoordinator implements PresenceManager.PresenceListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final PresenceManager presence;
    private final RelayTunnel tunnel;
    private final LanPlusNetwork network;
    private final BooleanSupplier relayEnabled;
    private final Supplier<RelayTicket> devTicket;

    private volatile boolean tunneling;
    private volatile boolean active;

    public RelayHostingCoordinator(PresenceManager presence, RelayTunnel tunnel, LanPlusNetwork network,
                                   BooleanSupplier relayEnabled, Supplier<RelayTicket> devTicket) {
        this.presence = presence;
        this.tunnel = tunnel;
        this.network = network;
        this.relayEnabled = relayEnabled;
        this.devTicket = devTicket;
        presence.addListener(this);
    }

    @Override
    public void onPresenceChanged(PresenceSnapshot snapshot) {
        if (snapshot.state() == GameplayState.HOSTING) {
            onHosting(snapshot);
        } else {
            stop();
        }
    }

    private void onHosting(PresenceSnapshot snapshot) {
        if (!relayEnabled.getAsBoolean() || tunneling) {
            return;
        }
        int localPort = loopbackPort(snapshot.address());
        if (localPort < 0) {
            return; // address is already the public domain we republished, or not a local host
        }
        tunneling = true;
        active = true;
        String worldName = snapshot.worldName();
        ticket().whenComplete((ticket, err) -> {
            if (!active || ticket == null) {
                tunneling = false;
                if (active) {
                    LOGGER.info("LAN+ relay unavailable — hosting LAN-only");
                }
                return;
            }
            tunnel.open(localPort, ticket).whenComplete((domain, e) -> {
                if (!active || domain == null) {
                    tunneling = false;
                    if (active) {
                        LOGGER.warn("LAN+ relay tunnel failed — hosting LAN-only");
                    } else {
                        tunnel.close();
                    }
                    return;
                }
                // Republish only if still hosting (the player may have stopped while we waited).
                if (presence.current().state() == GameplayState.HOSTING) {
                    presence.updateState(GameplayState.HOSTING, worldName, domain);
                    LOGGER.info("LAN+ hosting via relay: {}", domain);
                } else {
                    tunnel.close();
                    tunneling = false;
                }
            });
        });
    }

    private void stop() {
        if (active || tunneling || tunnel.isOpen()) {
            active = false;
            tunneling = false;
            tunnel.close();
        }
    }

    private CompletableFuture<RelayTicket> ticket() {
        return devTicket != null
                ? CompletableFuture.completedFuture(devTicket.get())
                : network.requestRelayTicket();
    }

    private static int loopbackPort(String address) {
        if (address == null) {
            return -1;
        }
        int colon = address.lastIndexOf(':');
        if (colon < 0) {
            return -1;
        }
        String host = address.substring(0, colon);
        if (!host.equals("localhost") && !host.equals("127.0.0.1")) {
            return -1;
        }
        try {
            return Integer.parseInt(address.substring(colon + 1).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
