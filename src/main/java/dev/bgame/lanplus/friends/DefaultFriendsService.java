package dev.bgame.lanplus.friends;

import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.api.Friend;
import dev.bgame.lanplus.api.PlayerIdentity;
import dev.bgame.lanplus.api.PresenceUpdate;
import dev.bgame.lanplus.api.ResolvedUser;
import dev.bgame.lanplus.api.UserProfile;
import dev.bgame.lanplus.invites.HostAccessControl;
import dev.bgame.lanplus.network.LanPlusNetwork;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public final class DefaultFriendsService implements FriendsService, LanPlusNetwork.BackendEventListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final LanPlusNetwork network;
    private final Supplier<PlayerIdentity> identity;
    private final Map<UUID, Friend> cache = new ConcurrentHashMap<>();
    private final List<FriendsListener> listeners = new CopyOnWriteArrayList<>();
    private volatile UserProfile localProfile;
    private volatile List<ResolvedUser> requestCache = List.of();

    public DefaultFriendsService(LanPlusNetwork network, Supplier<PlayerIdentity> identity) {
        this.network = network;
        this.identity = identity;
    }

    @Override
    public List<Friend> friends() {
        return List.copyOf(cache.values());
    }

    @Override
    public List<ResolvedUser> requests() {
        return requestCache;
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
        refreshRequests();
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
    public CompletableFuture<Boolean> remove(UUID friendUuid) {
        UUID uuid = localUuid();
        if (uuid == null) {
            return CompletableFuture.completedFuture(false);
        }
        return network.removeFriend(uuid, friendUuid).thenCompose(ok ->
                ok ? refresh().thenApply(ignored -> true) : CompletableFuture.completedFuture(false));
    }

    @Override
    public CompletableFuture<Boolean> accept(UUID requesterUuid) {
        UUID uuid = localUuid();
        if (uuid == null) {
            return CompletableFuture.completedFuture(false);
        }
        return network.acceptFriend(uuid, requesterUuid).thenCompose(ok -> {
            if (ok) {
                refreshRequests();
                return refresh().thenApply(ignored -> true);
            }
            return CompletableFuture.completedFuture(false);
        });
    }

    @Override
    public CompletableFuture<Boolean> decline(UUID requesterUuid) {
        UUID uuid = localUuid();
        if (uuid == null) {
            return CompletableFuture.completedFuture(false);
        }
        return network.declineFriend(uuid, requesterUuid).thenApply(ok -> {
            refreshRequests();
            return ok;
        });
    }

    @Override
    public CompletableFuture<Boolean> mute(UUID targetUuid) {
        return relation(network::muteFriend, targetUuid);
    }

    @Override
    public CompletableFuture<Boolean> unmute(UUID targetUuid) {
        return relation(network::unmuteFriend, targetUuid);
    }

    @Override
    public CompletableFuture<Boolean> block(UUID targetUuid) {
        return relation(network::blockFriend, targetUuid);
    }

    @Override
    public CompletableFuture<Boolean> unblock(UUID targetUuid) {
        return relation(network::unblockFriend, targetUuid);
    }

    private CompletableFuture<Boolean> relation(
            BiFunction<UUID, UUID, CompletableFuture<Boolean>> op, UUID targetUuid) {
        UUID uuid = localUuid();
        if (uuid == null) {
            return CompletableFuture.completedFuture(false);
        }
        return op.apply(uuid, targetUuid).thenCompose(ok ->
                ok ? refresh().thenApply(ignored -> true) : CompletableFuture.completedFuture(false));
    }

    @Override
    public void connect() {
        UUID uuid = localUuid();
        if (uuid == null) {
            return;
        }
        refresh();
        fetchProfile();
        refreshRequests();
        network.connectEvents(uuid, this);
        LOGGER.info("LAN+ friends realtime channel requested");
    }

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

    private void refreshRequests() {
        UUID uuid = localUuid();
        if (uuid == null) {
            return;
        }
        network.getFriendRequests(uuid).thenAccept(list -> {
            requestCache = list;
            notifyChanged();
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

    @Override
    public void onConnected() {
        refresh();
        fetchProfile();
        refreshRequests();
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
                friend.skin(),
                friend.muted(),
                friend.blocked()));
        if (patched != null) {
            notifyChanged();
        }
    }

    @Override
    public void onFriendRequest(UUID fromUuid, String fromUsername) {
        refreshRequests();
    }

    @Override
    public void onInviteRedeemed(UUID guestUuid) {
        HostAccessControl.invite(guestUuid);
        LOGGER.info("LAN+ invite redeemed by {} — admitted to hosted world", guestUuid);
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
