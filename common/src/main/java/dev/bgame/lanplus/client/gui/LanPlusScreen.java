package dev.bgame.lanplus.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class LanPlusScreen extends Screen {

    protected LanPlusScreen(Component title) {
        super(title);
    }

    protected void drawBackdrop(GuiGraphics g) {
        renderTransparentBackground(g);
        LanPlusUI.backdrop(g, this.width, this.height);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }
}