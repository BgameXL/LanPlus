package dev.bgame.lanplus.client;

import dev.bgame.lanplus.LanplusCommon;
import dev.bgame.lanplus.client.gui.FriendsScreen;
import dev.bgame.lanplus.client.gui.HostScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

/**
 * Adds two small icon buttons next to the title screen's "Singleplayer" button: a gear that opens
 * {@link HostScreen} ("Host a world") and a people icon that opens {@link FriendsScreen}. The friends
 * overlay is otherwise only reachable in-game via the O keybind; this makes it usable from the menu.
 *
 * Loader-agnostic: each loader calls {@link #tryAddButtons(Screen)} from its screen-init hook.
 */
public final class TitleScreenButtons {

    private static final ResourceLocation HOST_ICON = new ResourceLocation(LanplusCommon.MODID, "textures/gui/host.png");
    private static final ResourceLocation FRIENDS_ICON = new ResourceLocation(LanplusCommon.MODID, "textures/gui/friends.png");

    private TitleScreenButtons() {}

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
        screen.addRenderableWidget(new IconButton(x, y, HOST_ICON, "gui.lanplus.host.tooltip",
                b -> Minecraft.getInstance().setScreen(new HostScreen(title))));
        screen.addRenderableWidget(new IconButton(x + 22, y, FRIENDS_ICON, "gui.lanplus.friends.tooltip",
                b -> Minecraft.getInstance().setScreen(new FriendsScreen(title))));
    }

    private static AbstractWidget findSingleplayer(Screen screen) {
        // TitleScreen stores its buttons in the renderables list; the "Singleplayer" button
        // is identified by its translation key.
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget w
                    && w.getMessage().getContents() instanceof TranslatableContents tc
                    && "menu.singleplayer".equals(tc.getKey())) {
                return w;
            }
        }
        return null;
    }

    private static final class IconButton extends Button {

        private final ResourceLocation icon;

        private IconButton(int x, int y, ResourceLocation icon, String tooltipKey, OnPress onPress) {
            super(x, y, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);
            this.icon = icon;
            setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(g, mouseX, mouseY, partialTick);
            g.blit(icon, getX() + 2, getY() + 2, 0, 0, 16, 16, 16, 16);
        }
    }
}
