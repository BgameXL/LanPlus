package dev.bgame.lanplus.presence;

import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.HostAccessMode;
import dev.bgame.lanplus.api.PresenceSnapshot;
import dev.bgame.lanplus.api.SkinRef;

import java.util.Set;
import java.util.UUID;

public interface PresenceManager {

    PresenceSnapshot current();

    void updateState(GameplayState state, String worldName, String address);

    /**
     * Sets the host join code plus the access policy so presence can carry it to the backend. The
     * backend uses {@code accessMode}+{@code allowedUuids} to mask the join code to non-invited
     * friends in INVITED mode. Pass {@code (null, null, Set.of())} to clear when hosting stops.
     */
    void setJoinCode(String joinCode, HostAccessMode accessMode, Set<UUID> allowedUuids);

    void updateSkin(SkinRef skin);

    void updateModpack(String modpackId);

    void heartbeat();

    void addListener(PresenceListener listener);

    interface PresenceListener {
        void onPresenceChanged(PresenceSnapshot snapshot);
    }
}
