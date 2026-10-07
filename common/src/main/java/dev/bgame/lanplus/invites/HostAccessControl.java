package dev.bgame.lanplus.invites;

import dev.bgame.lanplus.api.HostAccessMode;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class HostAccessControl {

    public static final int DEFAULT_MAX_PLAYERS = 8;

    private static volatile HostAccessMode mode = HostAccessMode.EVERYONE;
    private static volatile UUID hostUuid;
    private static volatile boolean active = false;
    private static volatile int maxPlayers = DEFAULT_MAX_PLAYERS;
    private static volatile boolean allowVanillaJoin = false;
    private static final Set<UUID> allowed = ConcurrentHashMap.newKeySet();

    private HostAccessControl() {
    }

    public static void set(HostAccessMode newMode, UUID host, Collection<UUID> initialAllowed, int slots,
                           boolean vanillaJoin) {
        mode = newMode == null ? HostAccessMode.EVERYONE : newMode;
        hostUuid = host;
        maxPlayers = slots;
        allowVanillaJoin = vanillaJoin;
        allowed.clear();
        if (initialAllowed != null) {
            allowed.addAll(initialAllowed);
        }
        active = true;
    }

    public static void invite(UUID uuid) {
        if (uuid != null && (mode == HostAccessMode.INVITED || mode == HostAccessMode.FRIENDS_OF_FRIENDS)) {
            allowed.add(uuid);
        }
    }

    public static void clear() {
        active = false;
        mode = HostAccessMode.EVERYONE;
        hostUuid = null;
        maxPlayers = DEFAULT_MAX_PLAYERS;
        allowVanillaJoin = false;
        allowed.clear();
    }

    public static boolean gated(boolean offlineHosting) {
        if (!offlineHosting) {
            return false;
        }
        return !allowVanillaJoin;
    }

    public static boolean isActive() {
        return active;
    }

    public static int maxPlayers() {
        return maxPlayers;
    }

    public static HostAccessMode mode() {
        return mode;
    }

    public static boolean allowVanillaJoin() {
        return allowVanillaJoin;
    }

    public static Set<UUID> allowedSnapshot() {
        return active ? Set.copyOf(allowed) : Set.of();
    }

    public static boolean isAllowed(UUID uuid) {
        if (!active || mode == HostAccessMode.EVERYONE) {
            return true;
        }
        if (uuid != null && uuid.equals(hostUuid)) {
            return true;
        }
        return uuid != null && allowed.contains(uuid);
    }
}
