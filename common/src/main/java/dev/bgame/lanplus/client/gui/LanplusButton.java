package dev.bgame.lanplus.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class LanplusButton extends Button {

    private final boolean primary;

    private LanplusButton(Builder builder) {
        super(builder.x, builder.y, builder.width, builder.height,
                builder.message, builder.onPress, DEFAULT_NARRATION);
        this.primary = builder.primary;
    }

    public static Builder create(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        int color;
        if (primary) {
            LanPlusUI.primaryButton(g, x, y, x + w, y + h, isHovered(), isActive());
            color = isActive() ? LanPlusUI.TEXT : LanPlusUI.FAINT;
        } else {
            int bg = !isActive() ? LanPlusUI.SURFACE_DISABLED
                    : isHovered() ? LanPlusUI.SURFACE_HOVER : LanPlusUI.SURFACE_RAISED;
            LanPlusUI.button3d(g, x, y, x + w, y + h, bg);
            color = !isActive() ? LanPlusUI.FAINT : isHovered() ? LanPlusUI.TEXT : LanPlusUI.MUTED;
        }
        int tx = x + (w - Minecraft.getInstance().font.width(getMessage())) / 2;
        g.drawString(Minecraft.getInstance().font, getMessage(), tx, y + (h - 8) / 2, color, false);
    }

    public static final class Builder {
        private final Component message;
        private final OnPress onPress;
        private int x;
        private int y;
        private int height = 20;
        private int width = 150;
        private boolean primary;

        private Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder primary() {
            this.primary = true;
            return this;
        }

        public Builder bounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public LanplusButton build() {
            return new LanplusButton(this);
        }
    }
}
