package dev.bgame.lanplus.friends;

import dev.bgame.lanplus.api.Friend;
import dev.bgame.lanplus.api.ResolvedUser;
import dev.bgame.lanplus.api.UserProfile;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface FriendsService {

    List<Friend> friends();
    List<ResolvedUser> requests();
    UserProfile localProfile();
    CompletableFuture<List<Friend>> refresh();
    CompletableFuture<Boolean> add(UUID friendUuid);
    CompletableFuture<Boolean> addByQuery(String query);
    CompletableFuture<Boolean> remove(UUID friendUuid);
    CompletableFuture<Boolean> accept(UUID requesterUuid);
    CompletableFuture<Boolean> decline(UUID requesterUuid);
    CompletableFuture<Boolean> mute(UUID targetUuid);
    CompletableFuture<Boolean> unmute(UUID targetUuid);
    CompletableFuture<Boolean> block(UUID targetUuid);
    CompletableFuture<Boolean> unblock(UUID targetUuid);

    void connect();
    void disconnect();
    void addListener(FriendsListener listener);
    interface FriendsListener {
        void onFriendsChanged(List<Friend> friends);
        default void onFriendStartedHosting(UUID uuid, String joinCode) {
        }
        default void onFriendRequest(UUID fromUuid, String fromUsername) {
        }
    }
}
