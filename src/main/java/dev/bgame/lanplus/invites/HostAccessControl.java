package dev.bgame.lanplus.invites;

import dev.bgame.lanplus.api.HostAccessMode;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local host-side access policy for the world currently opened to LAN through LAN+: which mode is
 * active and the set of allowed player uuids. Side-agnostic state (no client/server imports) so the
 * server-side login gate (a mixin) can read it via {@link #isAllowed(UUID)} while the client UI
 * writes it. The decision logic lives here, not in the event handler.
 *
 * Degrades safely: until a host arms a policy it is inactive and {@link #isAllowed} returns true,
 * so a dedicated server (or a vanilla LAN open) is never gated.
 */
public final class HostAccessControl {

    private static volatile HostAccessMode mode = HostAccessMode.EVERYONE;
    private static volatile UUID hostUuid;
    private static volatile boolean active = false;
    private static final Set<UUID> allowed = ConcurrentHashMap.newKeySet();

    private HostAccessControl() {}

    /** Arm the policy for a freshly hosted world. {@code initialAllowed} seeds FRIENDS/INVITED. */
    public static void set(HostAccessMode newMode, UUID host, Collection<UUID> initialAllowed) {
        mode = newMode == null ? HostAccessMode.EVERYONE : newMode;
        hostUuid = host;
        allowed.clear();
        if (initialAllowed != null) {
            allowed.addAll(initialAllowed);
        }
        active = true;
    }

    /**
     * Admit a player (a pre-picked friend, or a guest who redeemed the invite code). Only takes
     * effect in INVITED mode — in FRIENDS mode the code must not let a non-friend in, and in
     * EVERYONE everyone is already allowed.
     */
    public static void invite(UUID uuid) {
        if (uuid != null && mode == HostAccessMode.INVITED) {
            allowed.add(uuid);
        }
    }

    /** Stop gating (hosting ended). */
    public static void clear() {
        active = false;
        mode = HostAccessMode.EVERYONE;
        hostUuid = null;
        allowed.clear();
    }

    public static boolean isActive() {
        return active;
    }

    public static HostAccessMode mode() {
        return mode;
    }

    /** Whether {@code uuid} may join the currently hosted world. */
    public static boolean isAllowed(UUID uuid) {
        if (!active || mode == HostAccessMode.EVERYONE) {
            return true;
        }
        if (uuid != null && uuid.equals(hostUuid)) {
            return true; // the host themselves
        }
        return uuid != null && allowed.contains(uuid);
    }
}