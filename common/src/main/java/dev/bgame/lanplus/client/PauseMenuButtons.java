package dev.bgame.lanplus.client;

import dev.bgame.lanplus.LanplusCommon;
import dev.bgame.lanplus.client.gui.HostScreen;
import dev.bgame.lanplus.client.gui.LanPlusIconButton;
import dev.bgame.lanplus.mixin.client.ScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

public final class
PauseMenuButtons {

    private static final ResourceLocation HOST_ICON =
            ResourceLocation.fromNamespaceAndPath(LanplusCommon.MODID, "textures/gui/host.png");

    private PauseMenuButtons() {
    }

    private static boolean hostedInWorld;

    public static void markHostedInWorld() {
        hostedInWorld = true;
    }

    public static void resetHostedInWorld() {
        hostedInWorld = false;
    }

    public static boolean isHostingInWorld() {
        return hostedInWorld;
    }

    public static void tryAddHostButton(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (!(screen instanceof PauseScreen) || !mc.hasSingleplayerServer() || hostedInWorld) {
            return;
        }
        AbstractWidget share = findOpenLan(screen);
        if (share == null) {
            return;
        }
        int x = share.getX() + share.getWidth() + 4;
        int y = share.getY();
        ((ScreenAccessor) screen).lanplus$invokeAddRenderableWidget(
                new LanPlusIconButton(x, y, HOST_ICON, "gui.lanplus.host.tooltip",
                        b -> mc.setScreen(new HostScreen(screen, true))));
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
