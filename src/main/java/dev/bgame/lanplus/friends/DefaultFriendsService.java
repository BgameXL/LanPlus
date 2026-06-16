package dev.bgame.lanplus.friends;

import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.api.Friend;
import dev.bgame.lanplus.api.PlayerIdentity;
import dev.bgame.lanplus.api.PresenceUpdate;
import dev.bgame.lanplus.api.UserProfile;
import dev.bgame.lanplus.network.LanPlusNetwork;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public final class DefaultFriendsService implements FriendsService, LanPlusNetwork.BackendEventListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final LanPlusNetwork network;
    private final Supplier<PlayerIdentity> identity;
    private final Map<UUID, Friend> cache = new ConcurrentHashMap<>();
    private final List<FriendsListener> listeners = new CopyOnWriteArrayList<>();
    private volatile UserProfile localProfile;

    public DefaultFriendsService(LanPlusNetwork network, Supplier<PlayerIdentity> identity) {
        this.network = network;
        this.identity = identity;
    }

    @Override
    public List<Friend> friends() {
        return List.copyOf(cache.values());
    }

    @Override
    public UserProfile localProfile() {
        return localProfile;
    }

    @Override
    public CompletableFuture<List<Friend>> refresh() {
        UUID uuid = localUuid();
        if (uuid == null) {
            return CompletableFuture.completedFuture(friends());
        }
        return network.getFriends(uuid).thenApply(list -> {
            cache.keySet().retainAll(list.stream().map(Friend::uuid).toList());
            for (Friend friend : list) {
                cache.put(friend.uuid(), friend);
            }
            notifyChanged();
            return friends();
        });
    }

    @Override
    public CompletableFuture<Boolean> add(UUID friendUuid) {
        UUID uuid = localUuid();
        if (uuid == null) {
            return CompletableFuture.completedFuture(false);
        }
        return network.addFriend(uuid, friendUuid).thenCompose(ok ->
                ok ? refresh().thenApply(ignored -> true) : CompletableFuture.completedFuture(false));
    }

    @Override
    public CompletableFuture<Boolean> addByQuery(String query) {
        if (localUuid() == null || query == null || query.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        return network.resolveUser(query).thenCompose(user ->
                user == null ? CompletableFuture.completedFuture(false) : add(user.uuid()));
    }

    @Override
    public void connect() {
        UUID uuid = localUuid();
        if (uuid == null) {
            return;
        }
        refresh();
        fetchProfile();
        network.connectEvents(uuid, this);
        LOGGER.info("LAN+ friends realtime channel requested");
    }

    /** Fetch the local player's own profile (friend code) and cache it. */
    private void fetchProfile() {
        UUID uuid = localUuid();
        if (uuid == null) {
            return;
        }
        network.fetchProfile(uuid).thenAccept(profile -> {
            if (profile != null) {
                localProfile = profile;
                notifyChanged();
            }
        });
    }

    @Override
    public void disconnect() {
        network.disconnect();
    }

    @Override
    public void addListener(FriendsListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(FriendsListener listener) {
        listeners.remove(listener);
    }

    // --- LanPlusNetwork.BackendEventListener (WebSocket frames, off the main thread) --------------

    @Override
    public void onConnected() {
        // Realtime channel (re)connected: pull a fresh list (and profile) to catch up on missed updates.
        refresh();
        fetchProfile();
    }

    @Override
    public void onPresenceUpdate(PresenceUpdate update) {
        Friend patched = cache.computeIfPresent(update.uuid(), (id, friend) -> new Friend(
                friend.uuid(),
                friend.username(),
                update.connectivity(),
                update.state(),
                update.worldName(),
                update.joinCode(),
                friend.skin()));
        if (patched != null) {
            notifyChanged();
        }
    }

    @Override
    public void onFriendStartedHosting(UUID uuid, String joinCode) {
        for (FriendsListener listener : listeners) {
            try {
                listener.onFriendStartedHosting(uuid, joinCode);
            } catch (RuntimeException e) {
                LOGGER.warn("LAN+ friends listener error", e);
            }
        }
    }

    private void notifyChanged() {
        List<Friend> snapshot = friends();
        for (FriendsListener listener : listeners) {
            try {
                listener.onFriendsChanged(snapshot);
            } catch (RuntimeException e) {
                LOGGER.warn("LAN+ friends listener error", e);
            }
        }
    }

    private UUID localUuid() {
        PlayerIdentity id = identity.get();
        return id == null ? null : id.uuid();
    }
}
