package dev.bgame.lanplus.client;

import dev.bgame.lanplus.LanplusCommon;
import dev.bgame.lanplus.client.gui.Announcements;
import dev.bgame.lanplus.client.gui.FriendsScreen;
import dev.bgame.lanplus.client.gui.HostScreen;
import dev.bgame.lanplus.client.gui.LanPlusIconButton;
import dev.bgame.lanplus.client.gui.ProfileScreen;
import dev.bgame.lanplus.client.gui.SettingsScreen;
import dev.bgame.lanplus.mixin.client.ScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public final class TitleScreenButtons {

    static final ResourceLocation HOST_ICON = ResourceLocation.fromNamespaceAndPath(LanplusCommon.MODID, "textures/gui/host.png");
    private static final ResourceLocation FRIENDS_ICON = ResourceLocation.fromNamespaceAndPath(LanplusCommon.MODID, "textures/gui/friends.png");
    private static final ResourceLocation PROFILE_ICON = ResourceLocation.fromNamespaceAndPath(LanplusCommon.MODID, "textures/gui/profile.png");
    private static final ResourceLocation SETTINGS_ICON = ResourceLocation.fromNamespaceAndPath(LanplusCommon.MODID, "textures/gui/settings.png");
    private static final ResourceLocation ANNOUNCEMENTS_ICON = ResourceLocation.fromNamespaceAndPath(LanplusCommon.MODID, "textures/gui/announcements.png");

    private TitleScreenButtons() {
    }

    public static void tryAddButtons(Screen screen) {
        if (!(screen instanceof TitleScreen title)) {
            return;
        }
        AbstractWidget singleplayer = findSingleplayer(screen);
        if (singleplayer == null) {
            return;
        }
        int x = singleplayer.getX() + singleplayer.getWidth() + 4;
        int y = singleplayer.getY();
        ScreenAccessor accessor = (ScreenAccessor) screen;
        accessor.lanplus$invokeAddRenderableWidget(new LanPlusIconButton(x, y, HOST_ICON, "gui.lanplus.host.tooltip",
                b -> Minecraft.getInstance().setScreen(new HostScreen(title))));
        accessor.lanplus$invokeAddRenderableWidget(new LanPlusIconButton(x + 22, y, FRIENDS_ICON, "gui.lanplus.friends.tooltip",
                b -> Minecraft.getInstance().setScreen(new FriendsScreen(title))));
        accessor.lanplus$invokeAddRenderableWidget(new LanPlusIconButton(x + 44, y, PROFILE_ICON, "gui.lanplus.profile.tooltip",
                b -> {
                    UUID id = LanPlusClient.selfUuid();
                    if (id != null) {
                        Minecraft.getInstance().setScreen(new ProfileScreen(title, id));
                    }
                }));
        accessor.lanplus$invokeAddRenderableWidget(new LanPlusIconButton(x + 66, y, SETTINGS_ICON, "gui.lanplus.settings.tooltip",
                b -> Minecraft.getInstance().setScreen(new SettingsScreen(title))));
        accessor.lanplus$invokeAddRenderableWidget(new LanPlusIconButton(x + 88, y, ANNOUNCEMENTS_ICON, "gui.lanplus.announcements.tooltip",
                b -> Minecraft.getInstance().setScreen(new Announcements(title)),
                () -> LanPlusClient.announcements() == null ? 0 : LanPlusClient.announcements().unseenCount()));
    }

    private static AbstractWidget findSingleplayer(Screen screen) {
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget w
                    && w.getMessage().getContents() instanceof TranslatableContents tc
                    && "menu.singleplayer".equals(tc.getKey())) {
                return w;
            }
        }
        return null;
    }
}