package dev.bgame.lanplus.network;

import dev.bgame.lanplus.api.CatalogImage;
import dev.bgame.lanplus.api.Friend;
import dev.bgame.lanplus.api.Invite;
import dev.bgame.lanplus.api.PresenceSnapshot;
import dev.bgame.lanplus.api.PresenceUpdate;
import dev.bgame.lanplus.api.Profile;
import dev.bgame.lanplus.api.RelayTicket;
import dev.bgame.lanplus.api.ResolvedUser;
import dev.bgame.lanplus.api.SkinUploadResult;
import dev.bgame.lanplus.api.UserProfile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface LanPlusNetwork {

    CompletableFuture<Void> pushPresence(PresenceSnapshot snapshot);
    CompletableFuture<List<Friend>> getFriends(UUID uuid);
    CompletableFuture<Boolean> addFriend(UUID uuid, UUID friendUuid);
    CompletableFuture<Boolean> removeFriend(UUID uuid, UUID friendUuid);
    CompletableFuture<Boolean> acceptFriend(UUID uuid, UUID friendUuid);
    CompletableFuture<Boolean> declineFriend(UUID uuid, UUID friendUuid);
    CompletableFuture<Boolean> muteFriend(UUID uuid, UUID targetUuid);
    CompletableFuture<Boolean> unmuteFriend(UUID uuid, UUID targetUuid);
    CompletableFuture<Boolean> blockFriend(UUID uuid, UUID targetUuid);
    CompletableFuture<Boolean> unblockFriend(UUID uuid, UUID targetUuid);
    CompletableFuture<List<ResolvedUser>> getFriendRequests(UUID uuid);
    CompletableFuture<ResolvedUser> resolveUser(String query);
    CompletableFuture<UserProfile> fetchProfile(UUID uuid);
    CompletableFuture<Profile> getProfile(UUID uuid, UUID viewer);
    CompletableFuture<String> updateProfile(UUID uuid, String bio, String pronouns, Map<String, String> links,
                                            Map<String, String> prompts, Boolean invisible,
                                            Boolean favoriteVisible, Boolean currentlyPlayingVisible,
                                            Boolean recentlyPlayedVisible);
    CompletableFuture<String> setFavoriteModpack(UUID uuid, String modpackId);
    CompletableFuture<String> setBackground(UUID uuid, String style, int color, int opacity, String imageId);
    CompletableFuture<String> setBanner(UUID uuid, String bannerId);
    CompletableFuture<List<CatalogImage>> getBackgrounds();
    CompletableFuture<List<CatalogImage>> getBanners();
    CompletableFuture<Void> reportAdvancement(UUID uuid, String advancementId);
    CompletableFuture<SkinUploadResult> uploadSkin(byte[] png, String model);
    CompletableFuture<Boolean> deleteSkin();
    CompletableFuture<Invite> createInvite(UUID hostUuid, String address, String worldName, boolean gated);
    CompletableFuture<Invite> resolveInvite(String code);
    CompletableFuture<RelayTicket> requestRelayTicket(boolean gated);

    void connectEvents(UUID uuid, BackendEventListener listener);
    void disconnect();
    boolean isConnected();

    String baseUrl();

    Profile parseProfileJson(String body);

    void setProfileBodySink(java.util.function.BiConsumer<UUID, String> sink);

    UUID sessionUuid();
    interface BackendEventListener {

        void onPresenceUpdate(PresenceUpdate update);

        void onFriendStartedHosting(UUID uuid, String joinCode);

        default void onFriendRequest(UUID fromUuid, String fromUsername) {
        }

        default void onInviteRedeemed(UUID guestUuid) {
        }

        default void onConnected() {
        }
    }
}
