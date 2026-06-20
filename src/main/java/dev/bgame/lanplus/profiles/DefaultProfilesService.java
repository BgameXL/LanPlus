package dev.bgame.lanplus.profiles;

import dev.bgame.lanplus.api.PlayerIdentity;
import dev.bgame.lanplus.api.Profile;
import dev.bgame.lanplus.network.LanPlusNetwork;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class DefaultProfilesService implements ProfilesService {

    private final LanPlusNetwork network;
    private final Supplier<PlayerIdentity> identity;

    public DefaultProfilesService(LanPlusNetwork network, Supplier<PlayerIdentity> identity) {
        this.network = network;
        this.identity = identity;
    }

    @Override
    public CompletableFuture<Profile> get(UUID uuid) {
        if (uuid == null) {
            return CompletableFuture.completedFuture(null);
        }
        PlayerIdentity id = identity.get();
        UUID viewer = id == null ? null : id.uuid();
        return network.getProfile(uuid, viewer);
    }

    @Override
    public CompletableFuture<String> save(String bio, String pronouns, Map<String, String> links, boolean invisible) {
        PlayerIdentity id = identity.get();
        if (id == null) {
            return CompletableFuture.completedFuture("offline");
        }
        return network.updateProfile(id.uuid(), bio, pronouns, links, invisible);
    }
}