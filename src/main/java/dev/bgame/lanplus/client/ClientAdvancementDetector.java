package dev.bgame.lanplus.client;

import dev.bgame.lanplus.Lanplus;
import dev.bgame.lanplus.profiles.ProfilesService;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = Lanplus.MODID, value = Dist.CLIENT)
public final class ClientAdvancementDetector {

    private ClientAdvancementDetector() {}

    @SubscribeEvent
    public static void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        Advancement advancement = event.getAdvancement();
        if (advancement == null || advancement.getDisplay() == null) {
            return; // recipe/hidden advancements have no display info; ignore them (they would spam XP)
        }
        Player player = event.getEntity();
        UUID local = localUuid();
        if (player == null || local == null || !local.equals(player.getUUID())) {
            return; // only the local player's own advancements award XP, not other players in the world
        }
        ProfilesService profiles = LanPlusClient.profiles();
        if (profiles != null) {
            profiles.reportAdvancement(advancement.getId().toString());
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
