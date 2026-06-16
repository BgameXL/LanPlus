package dev.bgame.lanplus.friends;

import dev.bgame.lanplus.api.Friend;
import dev.bgame.lanplus.api.UserProfile;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface FriendsService {

    List<Friend> friends();

    UserProfile localProfile();

    CompletableFuture<List<Friend>> refresh();

    CompletableFuture<Boolean> add(UUID friendUuid);

    CompletableFuture<Boolean> addByQuery(String query);

    void connect();

    void disconnect();

    void addListener(FriendsListener listener);

    void removeListener(FriendsListener listener);

    interface FriendsListener {

        void onFriendsChanged(List<Friend> friends);

        default void onFriendStartedHosting(UUID uuid, String joinCode) {
        }
    }
}
