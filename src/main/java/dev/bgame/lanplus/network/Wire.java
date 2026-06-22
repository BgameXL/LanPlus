package dev.bgame.lanplus.network;

import dev.bgame.lanplus.api.Connectivity;
import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.ModpackRef;
import dev.bgame.lanplus.api.Profile;
import dev.bgame.lanplus.api.RelayTicket;
import dev.bgame.lanplus.api.ResolvedUser;
import dev.bgame.lanplus.api.SkinRef;
import dev.bgame.lanplus.api.SkinType;
import dev.bgame.lanplus.api.UserProfile;

import java.util.Map;
import java.util.UUID;

final class Wire {

    private Wire() {}

    record Skin(String type, String id, String hash, String model) {
        static Skin from(SkinRef ref) {
            return ref == null ? null : new Skin(ref.type().name(), ref.id(), ref.hash(), ref.model());
        }

        SkinRef toApi() {
            return new SkinRef(SkinType.valueOf(type), id, hash, model);
        }
    }

    record PresenceRequest(
            String uuid,
            String username,
            String state,
            String worldName,
            String address,
            String joinCode,
            String modpackId,
            Skin skin,
            long timestamp
    ) {}

    record Friend(
            String uuid,
            String username,
            String connectivity,
            String state,
            String worldName,
            String joinCode,
            Skin skin,
            boolean muted,
            boolean blocked
    ) {
        dev.bgame.lanplus.api.Friend toApi() {
            return new dev.bgame.lanplus.api.Friend(
                    UUID.fromString(uuid),
                    username,
                    Connectivity.valueOf(connectivity),
                    state == null ? null : GameplayState.valueOf(state),
                    worldName,
                    joinCode,
                    skin == null ? null : skin.toApi(),
                    muted,
                    blocked);
        }
    }

    record FriendAdd(String uuid, String friendUuid) {}

    record FriendRelation(String uuid, String targetUuid) {}

    record Success(boolean success) {}

    record InviteCreate(String hostUuid, String address, String worldName, boolean gated) {}

    record InviteCreated(String code, String address, int expiresIn) {}

    record InviteResolved(String address, String worldName) {}

    record RelayTicketRequest(String uuid, boolean gated) {}

    record RelayTicketDto(String ticket, String relayHost, int relayPort, String domain, int expiresIn) {
        RelayTicket toApi() {
            return new RelayTicket(ticket, relayHost, relayPort, domain, expiresIn);
        }
    }

    record ResolvedUserDto(String uuid, String username, boolean online) {
        ResolvedUser toApi() {
            return new ResolvedUser(UUID.fromString(uuid), username, online);
        }
    }

    record UserProfileDto(String uuid, String username, String friendCode) {
        UserProfile toApi() {
            return new UserProfile(UUID.fromString(uuid), username, friendCode);
        }
    }

    record ProfileDto(String uuid, String username, String friendCode, String pronouns, String bio,
                      Map<String, String> links, Map<String, String> prompts,
                      boolean online, Long lastSeen, Boolean invisible, ModpackDto currentlyPlaying,
                      ModpackDto favorite, SettingsDto settings) {
        Profile toApi() {
            SettingsDto s = settings == null ? new SettingsDto(true, true, true) : settings;
            return new Profile(UUID.fromString(uuid), username, friendCode, pronouns, bio,
                    links == null ? Map.of() : links,
                    prompts == null ? Map.of() : prompts,
                    online,
                    lastSeen == null ? 0L : lastSeen,
                    Boolean.TRUE.equals(invisible),
                    currentlyPlaying == null ? null : currentlyPlaying.toApi(),
                    favorite == null ? null : favorite.toApi(),
                    !Boolean.FALSE.equals(s.favoriteVisible()),
                    !Boolean.FALSE.equals(s.currentlyPlayingVisible()),
                    !Boolean.FALSE.equals(s.mostPlayedVisible()));
        }
    }

    record ModpackDto(String modpackId, String name, String downloadUrl) {
        ModpackRef toApi() {
            return new ModpackRef(modpackId, name, downloadUrl);
        }
    }

    record SettingsDto(Boolean favoriteVisible, Boolean currentlyPlayingVisible, Boolean mostPlayedVisible) {}

    record ProfileUpdate(String uuid, String bio, String pronouns, Map<String, String> links,
                         Map<String, String> prompts, Boolean invisible,
                         String favoriteModpackId, Boolean favoriteVisible,
                         Boolean currentlyPlayingVisible) {}

    record UpdateResult(boolean success, String error) {}
}
