package dev.bgame.lanplus.discord;

import dev.bgame.lanplus.api.PresenceSnapshot;

public interface DiscordPresence {

    /** Whether Discord RPC is currently available/connected. */
    boolean isAvailable();

    /** Update the displayed rich presence from the local snapshot. */
    void update(PresenceSnapshot snapshot);

    /** Clear the rich presence (e.g. on shutdown). */
    void clear();
}
