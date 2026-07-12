package dev.bgame.lanplus.network;

import dev.bgame.lanplus.api.CatalogImage;
import dev.bgame.lanplus.api.Connectivity;
import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.ModpackRef;
import dev.bgame.lanplus.api.Profile;
import dev.bgame.lanplus.api.ProfileBackground;
import dev.bgame.lanplus.api.RelayTicket;
import dev.bgame.lanplus.api.ResolvedUser;
import dev.bgame.lanplus.api.SkinRef;
import dev.bgame.lanplus.api.SkinType;
import dev.bgame.lanplus.api.UserProfile;

import java.util.List;
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
            String accessMode,
            List<String> allowedUuids,
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
            boolean blocked,
            int tier
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
                    blocked,
                    tier);
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

    record ProfileDto(String uuid, String username, String friendCode, Skin skin, String pronouns, String bio,
                      Map<String, String> links, Map<String, String> prompts,
                      boolean online, Long lastSeen, Boolean invisible, ModpackDto currentlyPlaying,
                      ModpackDto lastPlayed, ModpackDto favorite, ModpackDto recentlyPlayed,
                      BackgroundDto background, CatalogImageDto banner,
                      SettingsDto settings, ProgressionDto progression) {
        Profile toApi(String base) {
            SettingsDto s = settings == null ? new SettingsDto(true, true, true) : settings;
            ProgressionDto p = progression == null ? new ProgressionDto(0, 0, null, null) : progression;
            return new Profile(UUID.fromString(uuid), username, friendCode,
                    skin == null ? null : skin.toApi(), pronouns, bio,
                    links == null ? Map.of() : links,
                    prompts == null ? Map.of() : prompts,
                    online,
                    lastSeen == null ? 0L : lastSeen,
                    Boolean.TRUE.equals(invisible),
                    currentlyPlaying == null ? null : currentlyPlaying.toApi(),
                    lastPlayed == null ? null : lastPlayed.toApi(),
                    favorite == null ? null : favorite.toApi(),
                    recentlyPlayed == null ? null : recentlyPlayed.toApi(),
                    !Boolean.FALSE.equals(s.favoriteVisible()),
                    !Boolean.FALSE.equals(s.currentlyPlayingVisible()),
                    !Boolean.FALSE.equals(s.recentlyPlayedVisible()),
                    p.tier() == null ? 0 : p.tier(),
                    p.advancements() == null ? 0 : p.advancements(),
                    p.xp() == null ? -1 : p.xp(),
                    p.sources() == null ? Map.of() : p.sources(),
                    background == null ? ProfileBackground.DEFAULT : background.toApi(base),
                    banner == null ? null : banner.toApi(base));
        }
    }

    record ProgressionDto(Integer tier, Integer advancements, Integer xp, Map<String, Integer> sources) {}

    record ModpackDto(String modpackId, String name, String downloadUrl) {
        ModpackRef toApi() {
            return new ModpackRef(modpackId, name, downloadUrl);
        }
    }

    record BackgroundDto(String style, Integer color, Integer opacity,
                         String imageId, String image, String imageHash) {
        ProfileBackground toApi(String base) {
            CatalogImage img = imageId == null || image == null ? null
                    : new CatalogImage(imageId, versionedUrl(base, image, imageHash), imageHash);
            return new ProfileBackground(
                    style,
                    color == null ? ProfileBackground.DEFAULT.color() : color,
                    opacity == null ? ProfileBackground.DEFAULT.opacity() : opacity,
                    img);
        }
    }

    record CatalogImageDto(String id, String url, String hash) {
        CatalogImage toApi(String base) {
            return id == null || url == null ? null
                    : new CatalogImage(id, versionedUrl(base, url, hash), hash);
        }
    }

    /**
     * Catalog urls arrive relative to the backend; the per-id url is stable across replacements,
     * so it is versioned by content hash — url-keyed caches (memory and disk) re-download on change.
     */
    static String versionedUrl(String base, String url, String hash) {
        String absolute = url.startsWith("http") ? url : base + url;
        if (hash == null || hash.isEmpty()) {
            return absolute;
        }
        return absolute + "?v=" + hash.substring(0, Math.min(16, hash.length()));
    }

    record BackgroundUpdateDto(String style, Integer color, Integer opacity, String imageId) {}

    record BackgroundUpdate(String uuid, BackgroundUpdateDto background) {}

    record BannerUpdate(String uuid, String bannerId) {}

    record SettingsDto(Boolean favoriteVisible, Boolean currentlyPlayingVisible, Boolean recentlyPlayedVisible) {}

    record ProfileUpdate(String uuid, String bio, String pronouns, Map<String, String> links,
                         Map<String, String> prompts, Boolean invisible,
                         Boolean favoriteVisible,
                         Boolean currentlyPlayingVisible, Boolean recentlyPlayedVisible) {}

    record FavoriteUpdate(String uuid, String favoriteModpackId) {}

    record UpdateResult(boolean success, String error) {}

    record AdvancementReport(String uuid, String advancementId) {}

    record SkinUpload(String png, String model) {}

    record SkinUploadResponse(String error, String url, String hash) {}

    record ChallengeResponse(String serverId) {}

    record AuthResponse(String token, String uuid, boolean verified, long expiresIn) {}
}
