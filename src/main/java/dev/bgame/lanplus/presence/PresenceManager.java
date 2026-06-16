package dev.bgame.lanplus.presence;

import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.PresenceSnapshot;
import dev.bgame.lanplus.api.SkinRef;

public interface PresenceManager {

    PresenceSnapshot current();

    void updateState(GameplayState state, String worldName, String address);

    void setJoinCode(String joinCode);

    void updateSkin(SkinRef skin);

    void heartbeat();

    void addListener(PresenceListener listener);

    void removeListener(PresenceListener listener);

    interface PresenceListener {
        void onPresenceChanged(PresenceSnapshot snapshot);
    }
}
