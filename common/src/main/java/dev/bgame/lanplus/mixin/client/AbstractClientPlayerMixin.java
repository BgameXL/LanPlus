package dev.bgame.lanplus.mixin.client;

import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.client.SkinTextures;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void lanplus$skin(CallbackInfoReturnable<PlayerSkin> cir) {
        SkinTextures.Resolved resolved = lanplus$resolved();
        if (resolved != null) {
            cir.setReturnValue(new PlayerSkin(
                    resolved.texture(),
                    null,
                    null,
                    null,
                    resolved.slim() ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE,
                    true));
        }
    }

    @Unique
    private SkinTextures.Resolved lanplus$resolved() {
        SkinTextures textures = LanPlusClient.skinTextures();
        if (textures == null) {
            return null;
        }
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        SkinTextures.Resolved resolved = textures.get(player.getUUID());
        if (resolved == null) {
            LanPlusClient.ensureSkin(player.getUUID(), player.getGameProfile().getName());
        }
        return resolved;
    }
}
