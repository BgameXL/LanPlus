package dev.bgame.lanplus.client;

import dev.bgame.lanplus.Lanplus;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Adds two small icon buttons next to the title screen's "Singleplayer" button: a gear that opens
 * {@link HostScreen} ("Host a world") and a people icon that opens {@link FriendsScreen}. The friends
 * overlay is otherwise only reachable in-game via the O keybind; this makes it usable from the menu.
 * UI entry points only — no business logic here.
 */
@Mod.EventBusSubscriber(modid = Lanplus.MODID, value = Dist.CLIENT)
public final class TitleScreenButtons {

    private static final ResourceLocation HOST_ICON = new ResourceLocation(Lanplus.MODID, "textures/gui/host.png");
    private static final ResourceLocation FRIENDS_ICON = new ResourceLocation(Lanplus.MODID, "textures/gui/friends.png");

    private TitleScreenButtons() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen title)) {
            return;
        }
        AbstractWidget singleplayer = findSingleplayer(event);
        if (singleplayer == null) {
            return;
        }
        int x = singleplayer.getX() + singleplayer.getWidth() + 4;
        int y = singleplayer.getY();
        event.addListener(new IconButton(x, y, HOST_ICON, "gui.lanplus.host.tooltip",
                b -> Minecraft.getInstance().setScreen(new HostScreen(title))));
        event.addListener(new IconButton(x + 22, y, FRIENDS_ICON, "gui.lanplus.friends.tooltip",
                b -> Minecraft.getInstance().setScreen(new FriendsScreen(title))));
    }

    private static AbstractWidget findSingleplayer(ScreenEvent.Init event) {
        for (var listener : event.getListenersList()) {
            if (listener instanceof AbstractWidget w
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