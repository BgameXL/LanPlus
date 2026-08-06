package dev.bgame.lanplus.client;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FriendNotifications {

    private static final Set<UUID> unreadInvites = ConcurrentHashMap.newKeySet();

    private FriendNotifications() {}

    public static void invited(UUID uuid) {
        if (uuid != null) {
            unreadInvites.add(uuid);
        }
    }

    public static boolean isUnread(UUID uuid) {
        return uuid != null && unreadInvites.contains(uuid);
    }

    public static void markSeen(UUID uuid) {
        unreadInvites.remove(uuid);
    }

    public static int unreadCount() {
        return unreadInvites.size();
    }

    public static void retain(Collection<UUID> stillJoinable) {
        unreadInvites.retainAll(stillJoinable);
    }
}