package dev.bgame.lanplus.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/*
 * A {@link Button} rendered in the LAN+ style instead of vanilla Minecraft.
 */
public final class LanPlusButton extends Button {

    private LanPlusButton(Builder builder) {
        super(builder.x, builder.y, builder.width, builder.height,
                builder.message, builder.onPress, DEFAULT_NARRATION);
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
        int bg;
        if (!isActive()) {
            bg = 0xFF232428;
        } else if (isHovered()) {
            bg = 0xFF35373C;
        } else {
            bg = LanPlusUi.SURFACE_RAISED;
        }
        g.fill(x, y, x + w, y + h, bg);
        LanPlusUi.border(g, x, y, x + w, y + h);
        int color = !isActive() ? LanPlusUi.FAINT : isHovered() ? LanPlusUi.TEXT : LanPlusUi.MUTED;
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

        private Builder(Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
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

        public LanPlusButton build() {
            return new LanPlusButton(this);
        }
    }
}