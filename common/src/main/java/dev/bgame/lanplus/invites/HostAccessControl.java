package dev.bgame.lanplus.invites;

import dev.bgame.lanplus.api.HostAccessMode;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local host-side access policy for the world currently opened to LAN through LAN+: which mode is
 * active and the set of allowed player uuids.
 */
public final class HostAccessControl {

    private static volatile HostAccessMode mode = HostAccessMode.EVERYONE;
    private static volatile UUID hostUuid;
    private static volatile boolean active = false;
    private static final Set<UUID> allowed = ConcurrentHashMap.newKeySet();

    private HostAccessControl() {
    }

    public static void set(HostAccessMode newMode, UUID host, Collection<UUID> initialAllowed) {
        mode = newMode == null ? HostAccessMode.EVERYONE : newMode;
        hostUuid = host;
        allowed.clear();
        if (initialAllowed != null) {
            allowed.addAll(initialAllowed);
        }
        active = true;
    }

    public static void invite(UUID uuid) {
        if (uuid != null && mode == HostAccessMode.INVITED) {
            allowed.add(uuid);
        }
    }

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

    public static Set<UUID> allowedSnapshot() {
        return active ? Set.copyOf(allowed) : Set.of();
    }

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