package dev.bgame.lanplus.network;

import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.PresenceSnapshot;
import dev.bgame.lanplus.api.RelayTicket;
import dev.bgame.lanplus.invites.HostAccessControl;
import dev.bgame.lanplus.presence.PresenceManager;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class RelayHostingCoordinator implements PresenceManager.PresenceListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final PresenceManager presence;
    private final RelayTunnel tunnel;
    private final LanPlusNetwork network;
    private final BooleanSupplier relayEnabled;
    private final BooleanSupplier offlineHosting;
    private final Supplier<RelayTicket> devTicket;
    private long generation;

    private boolean tunneling;
    private boolean active;

    public RelayHostingCoordinator(PresenceManager presence, RelayTunnel tunnel, LanPlusNetwork network,
                                   BooleanSupplier relayEnabled, BooleanSupplier offlineHosting,
                                   Supplier<RelayTicket> devTicket) {
        this.presence = presence;
        this.tunnel = tunnel;
        this.network = network;
        this.relayEnabled = relayEnabled;
        this.offlineHosting = offlineHosting;
        this.devTicket = devTicket;
        presence.addListener(this);
    }

    @Override
    public void onPresenceChanged(PresenceSnapshot snapshot) {
        synchronized (presence) {
            if (snapshot.state() == GameplayState.HOSTING) {
                onHosting(snapshot);
            } else {
                stop();
            }
        }
    }

    private void onHosting(PresenceSnapshot snapshot) {
        if (!relayEnabled.getAsBoolean() || tunneling) {
            return;
        }
        int localPort = loopbackPort(snapshot.address());
        if (localPort < 0) {
            return;
        }
        tunneling = true;
        active = true;
        long attempt = ++generation;
        String worldName = snapshot.worldName();
        ticket().whenComplete((ticket, err) -> {
            synchronized (presence) {
                if (!isCurrent(attempt)) {
                    return;
                }
                if (ticket == null) {
                    tunneling = false;
                    LOGGER.info("LAN+ relay unavailable - hosting LAN-only");
                    return;
                }
                tunnel.open(localPort, ticket).whenComplete((domain, e) -> {
                    synchronized (presence) {
                        if (!isCurrent(attempt)) {
                            return;
                        }
                        if (domain == null) {
                            tunneling = false;
                            LOGGER.warn("LAN+ relay tunnel failed - hosting LAN-only");
                            return;
                        }
                        if (presence.current().state() == GameplayState.HOSTING) {
                            presence.updateState(GameplayState.HOSTING, worldName, domain);
                            LOGGER.info("LAN+ hosting via relay: {}", domain);
                        } else {
                            tunnel.close();
                            tunneling = false;
                        }
                    }
                });
            }
        });
    }

    private void stop() {
        if (active || tunneling || tunnel.isOpen()) {
            generation++;
            active = false;
            tunneling = false;
            tunnel.close();
        }
    }

    private boolean isCurrent(long attempt) {
        return active && generation == attempt;
    }

    private CompletableFuture<RelayTicket> ticket() {
        return devTicket != null
                ? CompletableFuture.completedFuture(devTicket.get())
                : network.requestRelayTicket(HostAccessControl.gated(offlineHosting.getAsBoolean()),
                        HostAccessControl.mode().name());
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
