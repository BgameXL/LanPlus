package dev.bgame.lanplus;

import dev.bgame.lanplus.client.ClientPresenceDetector;
import dev.bgame.lanplus.client.HostController;
import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.client.LanPlusKeybinds;
import dev.bgame.lanplus.client.PauseMenuButtons;
import dev.bgame.lanplus.client.TitleScreenButtons;
import dev.bgame.lanplus.client.gui.LanPlusNotifications;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.gui.GuiGraphics;

public class LanplusFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        LanPlusClient.init();

        KeyBindingHelper.registerKeyBinding(LanPlusKeybinds.OPEN_FRIENDS);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            LanPlusKeybinds.onClientTick();
            ClientPresenceDetector.onClientTick();
            HostController.onClientTick();
        });

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            LanPlusNotifications.onRenderGui(context);
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            TitleScreenButtons.tryAddButtons(screen);
            PauseMenuButtons.tryAddHostButton(screen);
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.afterRender(screen).register((screenInstance, graphics, mouseX, mouseY, tickDelta) -> {
                LanPlusNotifications.onScreenRender(graphics, mouseX, mouseY);
            });
            ScreenMouseEvents.allowMouseClick(screen).register((screenInstance, mouseX, mouseY, button) -> {
                return !LanPlusNotifications.onMouseClick(mouseX, mouseY, button);
            });
        });

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> ClientPresenceDetector.onLogout());

    }
}
