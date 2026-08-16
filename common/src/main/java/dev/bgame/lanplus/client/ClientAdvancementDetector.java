package dev.bgame.lanplus.client;

import dev.bgame.lanplus.profiles.ProfilesService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import java.util.UUID;

public final class ClientAdvancementDetector {

    private ClientAdvancementDetector() {}

    public static void onAdvancementEarn(String advancementId, boolean hasDisplay) {
        if (!hasDisplay) {
            return;
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
