package dev.bgame.lanplus.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class LanPlusIconButton extends Button {

    private final ResourceLocation icon;

    public LanPlusIconButton(int x, int y, ResourceLocation icon, String tooltipKey, OnPress onPress) {
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