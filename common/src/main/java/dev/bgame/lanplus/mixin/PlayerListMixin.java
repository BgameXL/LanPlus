package dev.bgame.lanplus.mixin;

import com.mojang.authlib.GameProfile;
import dev.bgame.lanplus.invites.HostAccessControl;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
    private void lanplus$gateAccess(SocketAddress address, GameProfile profile,
                                    CallbackInfoReturnable<Component> cir) {
        if (!HostAccessControl.isAllowed(profile.getId())) {
            cir.setReturnValue(Component.translatable("disconnect.lanplus.not_invited"));
        }
    }
}
