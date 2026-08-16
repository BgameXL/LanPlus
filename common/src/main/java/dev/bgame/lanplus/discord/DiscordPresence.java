package dev.bgame.lanplus.discord;

import dev.bgame.lanplus.api.PresenceSnapshot;

public interface DiscordPresence {

    boolean isAvailable();
    void update(PresenceSnapshot snapshot);
    void setEnabled(boolean enabled);
    void clear();
}
