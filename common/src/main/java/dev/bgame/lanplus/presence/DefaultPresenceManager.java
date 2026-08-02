package dev.bgame.lanplus.presence;

import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.HostAccessMode;
import dev.bgame.lanplus.api.PresenceSnapshot;
import dev.bgame.lanplus.api.SkinRef;
import dev.bgame.lanplus.network.LanPlusNetwork;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default {@link PresenceManager}: the hub of {@code Minecraft → PresenceManager → LanPlusNetwork →
 * Backend}. Side-agnostic (no Minecraft types) — detection feeds it from the client.
 *
 * <p>Assembles the local {@link PresenceSnapshot} from parts contributed by different modules
 * (state/world from detection, join code from invites, skin from skins) and pushes it on every
 * change as well as on each {@link #heartbeat()}. All pushes are fire-and-forget and fail soft.
 */
public final class DefaultPresenceManager implements PresenceManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final LanPlusNetwork network;
    private final List<PresenceListener> listeners = new CopyOnWriteArrayList<>();

    private volatile PresenceSnapshot snapshot =
            new PresenceSnapshot(GameplayState.MENU, null, null, null, null, null, null, Set.of());

    public DefaultPresenceManager(LanPlusNetwork network) {
        this.network = network;
    }

    @Override
    public PresenceSnapshot current() {
        return snapshot;
    }

    @Override
    public synchronized void updateState(GameplayState state, String worldName, String address) {
        Objects.requireNonNull(state, "state");
        apply(new PresenceSnapshot(state, worldName, address, snapshot.joinCode(), snapshot.skin(),
                snapshot.modpackId(), snapshot.accessMode(), snapshot.allowedUuids()));
    }

    @Override
    public synchronized void setJoinCode(String joinCode, HostAccessMode accessMode, Set<UUID> allowedUuids) {
        apply(new PresenceSnapshot(snapshot.state(), snapshot.worldName(), snapshot.address(), joinCode,
                snapshot.skin(), snapshot.modpackId(), accessMode, allowedUuids));
    }

    @Override
    public synchronized void updateSkin(SkinRef skin) {
        apply(new PresenceSnapshot(snapshot.state(), snapshot.worldName(), snapshot.address(), snapshot.joinCode(),
                skin, snapshot.modpackId(), snapshot.accessMode(), snapshot.allowedUuids()));
    }

    @Override
    public synchronized void updateModpack(String modpackId) {
        apply(new PresenceSnapshot(snapshot.state(), snapshot.worldName(), snapshot.address(), snapshot.joinCode(),
                snapshot.skin(), modpackId, snapshot.accessMode(), snapshot.allowedUuids()));
    }

    @Override
    public void heartbeat() {
        push(snapshot);
    }

    @Override
    public void addListener(PresenceListener listener) {
        listeners.add(listener);
    }

    private void apply(PresenceSnapshot next) {
        if (next.equals(snapshot)) {
            return;
        }
        snapshot = next;
        push(next);
        notifyListeners(next);
    }

    private void push(PresenceSnapshot s) {
        network.pushPresence(s).exceptionally(err -> {
            LOGGER.debug("LAN+ presence push failed (local-only): {}", err.toString());
            return null;
        });
    }

    private void notifyListeners(PresenceSnapshot s) {
        for (PresenceListener listener : listeners) {
            try {
                listener.onPresenceChanged(s);
            } catch (RuntimeException e) {
                LOGGER.warn("LAN+ presence listener error", e);
            }
        }
    }
}
