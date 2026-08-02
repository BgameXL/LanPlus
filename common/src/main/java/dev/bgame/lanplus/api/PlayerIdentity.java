package dev.bgame.lanplus.api;

import java.util.Objects;
import java.util.UUID;

/**
 * The local player's identity, supplied to the network layer at send time so that
 * {@link PresenceSnapshot} can stay free of identity/transport concerns.
 *
 * Resolved from the Minecraft session on the client (CLAUDE.md #4); this type itself is
 * side-agnostic.
 *
 * @param uuid     player UUID
 * @param username player name
 */
public record PlayerIdentity(UUID uuid, String username) {
    public PlayerIdentity {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(username, "username");
    }
}
