package dev.bgame.lanplus.mixin.client;

import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.client.SkinTextures;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getSkinTextureLocation", at = @At("HEAD"), cancellable = true)
    private void lanplus$skinTexture(CallbackInfoReturnable<ResourceLocation> cir) {
        SkinTextures.Resolved resolved = lanplus$resolved();
        if (resolved != null) {
            cir.setReturnValue(resolved.texture());
        }
    }

    @Inject(method = "getModelName", at = @At("HEAD"), cancellable = true)
    private void lanplus$modelName(CallbackInfoReturnable<String> cir) {
        SkinTextures.Resolved resolved = lanplus$resolved();
        if (resolved != null) {
            cir.setReturnValue(resolved.slim() ? "slim" : "default");
        }
    }

    private SkinTextures.Resolved lanplus$resolved() {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        LanPlusClient.ensureSkin(player.getUUID(), player.getGameProfile().getName());
        SkinTextures textures = LanPlusClient.skinTextures();
        return textures == null ? null : textures.get(player.getUUID());
    }
}
