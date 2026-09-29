package dev.bgame.lanplus.client.gui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class LanPlusScreen extends Screen {

    protected static final int SIDE = 24;
    protected static final int EDGE = 20;
    private static final int MIN_LOGICAL_W = 700;
    private static final int MIN_LOGICAL_H = 450;

    private double originalScale = -1;

    protected LanPlusScreen(Component title) {
        super(title);
    }

    @Override
    protected void rebuildWidgets() {
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        if (originalScale < 0) {
            originalScale = window.getGuiScale();
        }
        double fit = Math.min(originalScale, Math.min(window.getWidth() / (double) MIN_LOGICAL_W,
                window.getHeight() / (double) MIN_LOGICAL_H));
        window.setGuiScale(Math.max(1.0, fit));
        this.width = window.getGuiScaledWidth();
        this.height = window.getGuiScaledHeight();
        super.rebuildWidgets();
    }

    @Override
    public void removed() {
        if (originalScale > 0) {
            Minecraft.getInstance().getWindow().setGuiScale(originalScale);
            originalScale = -1;
        }
        super.removed();
    }

    protected int fitWidth(int maxW) {
        return Math.min(this.width - 2 * SIDE, maxW);
    }

    protected int fitHeight(int maxH) {
        return Math.min(this.height - 2 * EDGE, maxH);
    }

    protected int centerX(int w) {
        return (this.width - w) / 2;
    }

    protected int centerY(int h) {
        return Math.max(EDGE, (this.height - h) / 2);
    }

    protected void drawBackdrop(GuiGraphics g) {
        if (this.minecraft != null && this.minecraft.level != null) {
            return;
        }
        LanPlusUI.background(g, this.width, this.height);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
    }
}
