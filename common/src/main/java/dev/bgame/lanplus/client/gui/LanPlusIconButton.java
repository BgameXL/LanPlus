package dev.bgame.lanplus.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.IntSupplier;

public final class LanPlusIconButton extends Button {

    private final ResourceLocation icon;
    private final IntSupplier badge;

    public LanPlusIconButton(int x, int y, ResourceLocation icon, String tooltipKey, OnPress onPress) {
        this(x, y, icon, tooltipKey, onPress, null);
    }

    public LanPlusIconButton(int x, int y, ResourceLocation icon, String tooltipKey, OnPress onPress,
                             IntSupplier badge) {
        super(x, y, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);
        this.icon = icon;
        this.badge = badge;
        setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
    }

    @Override
    public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int bg = isHovered() ? LanPlusUI.SURFACE_HOVER : LanPlusUI.SURFACE_RAISED;
        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
        LanPlusUI.bevelR(g, getX(), getY(), getX() + getWidth(), getY() + getHeight());
        g.blit(icon, getX() + 2, getY() + 2, 0, 0, 16, 16, 16, 16);
        if (badge == null) {
            return;
        }
        int count = badge.getAsInt();
        if (count <= 0) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        String s = count > 9 ? "9+" : Integer.toString(count);
        float scale = 0.85f;
        int tw = Math.round(font.width(s) * scale);
        int w = tw + 4;
        int bx = getX() + 20 - w;
        int by = getY() - 1;
        g.fill(bx, by, bx + w, by + 9, LanPlusUI.LIME);
        g.pose().pushPose();
        g.pose().translate(bx + (w - tw) / 2.0f, by + 1.5f, 300);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, s, 0, 0, LanPlusUI.EDGE_DARK, false);
        g.pose().popPose();
    }
}