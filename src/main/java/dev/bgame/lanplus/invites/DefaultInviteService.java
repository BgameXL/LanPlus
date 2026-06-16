package dev.bgame.lanplus.invites;

import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.Invite;
import dev.bgame.lanplus.api.PlayerIdentity;
import dev.bgame.lanplus.api.PresenceSnapshot;
import dev.bgame.lanplus.network.LanPlusNetwork;
import dev.bgame.lanplus.presence.PresenceManager;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class DefaultInviteService implements InviteService, PresenceManager.PresenceListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final LanPlusNetwork network;
    private final PresenceManager presence;
    private final Supplier<PlayerIdentity> identity;
    private final BooleanSupplier relayEnabled;

    private final AtomicBoolean minting = new AtomicBoolean(false);
    private volatile String activeCode;

    public DefaultInviteService(LanPlusNetwork network, PresenceManager presence,
                                Supplier<PlayerIdentity> identity, BooleanSupplier relayEnabled) {
        this.network = network;
        this.presence = presence;
        this.identity = identity;
        this.relayEnabled = relayEnabled;
        presence.addListener(this);
    }

    @Override
    public CompletableFuture<Invite> create(String address, String worldName) {
        PlayerIdentity id = identity.get();
        if (id == null || address == null) {
            return CompletableFuture.completedFuture(null);
        }
        return network.createInvite(id.uuid(), address, worldName);
    }

    @Override
    public CompletableFuture<Invite> resolve(String code) {
        if (code == null || code.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return network.resolveInvite(code);
    }

    @Override
    public void onPresenceChanged(PresenceSnapshot snapshot) {
        if (snapshot.state() == GameplayState.HOSTING) {
            mintIfNeeded(snapshot);
        } else if (activeCode != null) {
            activeCode = null;
            presence.setJoinCode(null);
        }
    }

    private void mintIfNeeded(PresenceSnapshot snapshot) {
        if (activeCode != null || snapshot.address() == null) {
            return;
        }
        // With the relay enabled, the detector first reports the unreachable loopback address; wait
        // for the coordinator to open the tunnel and republish the public domain before minting.
        if (relayEnabled.getAsBoolean() && isLoopback(snapshot.address())) {
            return;
        }
        if (!minting.compareAndSet(false, true)) {
            return;
        }
        create(snapshot.address(), snapshot.worldName()).whenComplete((invite, err) -> {
            minting.set(false);
            // Only publish if we are still hosting (the player may have stopped while we waited).
            if (invite != null && presence.current().state() == GameplayState.HOSTING) {
                activeCode = invite.code();
                presence.setJoinCode(invite.code());
                LOGGER.info("LAN+ invite created: {}", invite.code());
            }
        });
    }

    private static boolean isLoopback(String address) {
        int colon = address.lastIndexOf(':');
        String host = colon < 0 ? address : address.substring(0, colon);
        return host.equals("localhost") || host.equals("127.0.0.1");
    }
}
