package dev.bgame.lanplus.client;

import dev.bgame.lanplus.client.gui.HostScreen;
import dev.bgame.lanplus.mixin.client.ScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

public final class
PauseMenuButtons {

    private PauseMenuButtons() {}

    public static void tryAddHostButton(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (!(screen instanceof PauseScreen) || !mc.hasSingleplayerServer()) {
            return;
        }
        AbstractWidget share = findOpenLan(screen);
        if (share == null) {
            return;
        }
        Component label = Component.translatable("gui.lanplus.host.pausebutton");
        int w = Math.max(196, mc.font.width(label) + 16);
        Button button = Button.builder(label, b ->
                        mc.setScreen(new HostScreen(screen, true)))
                .bounds(share.getX() - w - 0, share.getY(), w, share.getHeight())
                .build();
        ((ScreenAccessor) screen).lanplus$invokeAddRenderableWidget(button);
    }

    private static AbstractWidget findOpenLan(Screen screen) {
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget w
                    && w.getMessage().getContents() instanceof TranslatableContents tc
                    && "menu.shareToLan".equals(tc.getKey())) {
                return w;
            }
        }
        return null;
    }
}