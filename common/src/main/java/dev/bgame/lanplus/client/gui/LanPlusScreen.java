package dev.bgame.lanplus.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class LanPlusScreen extends Screen {

    protected LanPlusScreen(Component title) {
        super(title);
    }

    protected void drawBackdrop(GuiGraphics g) {
        if (this.minecraft != null && this.minecraft.level != null) {
            return;
        }
        LanPlusUI.background(g, this.width, this.height);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }
}