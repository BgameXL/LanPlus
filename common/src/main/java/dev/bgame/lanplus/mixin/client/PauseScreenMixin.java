package dev.bgame.lanplus.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin {

    @Unique
    private boolean lanplus$wasLanPlusServer;

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void lanplus$captureLanPlus(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        ServerData server = mc.getCurrentServer();
        lanplus$wasLanPlusServer = !mc.isLocalServer() && server != null && "LAN+".equals(server.name);
    }

    @Redirect(method = "onDisconnect",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V"))
    private void lanplus$returnToTitle(Minecraft mc, Screen screen) {
        if (lanplus$wasLanPlusServer && screen instanceof JoinMultiplayerScreen) {
            mc.setScreen(new TitleScreen());
        } else {
            mc.setScreen(screen);
        }
    }
}