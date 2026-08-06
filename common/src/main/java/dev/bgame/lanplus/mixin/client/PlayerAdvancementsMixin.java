package dev.bgame.lanplus.mixin.client;

import dev.bgame.lanplus.client.ClientAdvancementDetector;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.Minecraft;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @Shadow
    private ServerPlayer player;

    @Inject(
            method = "award(Lnet/minecraft/advancements/Advancement;Ljava/lang/String;)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/advancements/AdvancementRewards;grant(Lnet/minecraft/server/level/ServerPlayer;)V")
    )
    private void lanplus$onAdvancementEarned(Advancement advancement, String criterion,
                                             CallbackInfoReturnable<Boolean> cir) {
        UUID local = localUuid();
        if (advancement == null || this.player == null || local == null
                || !this.player.getUUID().equals(local)) {
            return;
        }
        ClientAdvancementDetector.onAdvancementEarn(
                advancement.getId().toString(), advancement.getDisplay() != null);
    }

    private static UUID localUuid() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc == null || mc.getUser() == null ? null : mc.getUser().getProfileId();
        } catch (RuntimeException e) {
            return null;
        }
    }
}