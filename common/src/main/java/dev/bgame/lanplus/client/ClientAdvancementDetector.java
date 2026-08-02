package dev.bgame.lanplus.client;

import dev.bgame.lanplus.profiles.ProfilesService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.util.UUID;

/**
 * Loader-agnostic detector for local-player advancements. Each loader is responsible for
 * firing {@link #onAdvancementEarn(String, boolean)} when the local player earns an
 * advancement (Fabric typically requires a Mixin; Forge has a native event).
 */
public final class ClientAdvancementDetector {

    private ClientAdvancementDetector() {}

    /**
     * @param advancementId the advancement ID, e.g. "minecraft:story/mine_stone".
     * @param hasDisplay    false for recipe/hidden advancements that should be ignored.
     */
    public static void onAdvancementEarn(String advancementId, boolean hasDisplay) {
        if (!hasDisplay) {
            return; // recipe/hidden advancements have no display info; ignore them (they would spam XP)
        }
        UUID local = localUuid();
        if (local == null) {
            return;
        }
        ProfilesService profiles = LanPlusClient.profiles();
        if (profiles != null) {
            profiles.reportAdvancement(advancementId);
        }
    }

    private static UUID localUuid() {
        User user = Minecraft.getInstance().getUser();
        try {
            return user == null ? null : user.getProfileId();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
