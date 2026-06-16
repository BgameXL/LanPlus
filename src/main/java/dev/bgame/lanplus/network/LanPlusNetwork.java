package dev.bgame.lanplus.network;

import dev.bgame.lanplus.api.Friend;
import dev.bgame.lanplus.api.Invite;
import dev.bgame.lanplus.api.PresenceSnapshot;
import dev.bgame.lanplus.api.PresenceUpdate;
import dev.bgame.lanplus.api.RelayTicket;
import dev.bgame.lanplus.api.ResolvedUser;
import dev.bgame.lanplus.api.UserProfile;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface LanPlusNetwork {

    CompletableFuture<Void> pushPresence(PresenceSnapshot snapshot);

    CompletableFuture<List<Friend>> getFriends(UUID uuid);

    CompletableFuture<Boolean> addFriend(UUID uuid, UUID friendUuid);

    CompletableFuture<ResolvedUser> resolveUser(String query);

    CompletableFuture<UserProfile> fetchProfile(UUID uuid);

    CompletableFuture<Invite> createInvite(UUID hostUuid, String address, String worldName);

    CompletableFuture<Invite> resolveInvite(String code);

    CompletableFuture<RelayTicket> requestRelayTicket();

    void connectEvents(UUID uuid, BackendEventListener listener);

    void disconnect();

    boolean isConnected();

    interface BackendEventListener {

        void onPresenceUpdate(PresenceUpdate update);

        void onFriendStartedHosting(UUID uuid, String joinCode);

        default void onConnected() {
        }
    }
}
