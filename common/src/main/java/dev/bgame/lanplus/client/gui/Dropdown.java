package dev.bgame.lanplus.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class Dropdown {

    private static final int ITEM_H = 15;

    private int owner = -1;
    private final List<int[]> cells = new ArrayList<>();

    public boolean isOpen() {
        return owner >= 0;
    }
    public int owner() {
        return owner;
    }

    public void open(int owner) {
        this.owner = owner;
    }

    public void toggle(int owner) {
        this.owner = this.owner == owner ? -1 : owner;
    }

    public void close() {
        owner = -1;
    }

    public void render(GuiGraphics g, Font font, int x, int top, int minWidth, int maxWidth, int maxBottom,
                       List<Component> labels, int selectedIndex, int mouseX, int mouseY) {
        cells.clear();
        if (owner < 0 || labels.isEmpty()) {
            return;
        }
        int w = minWidth;
        for (Component c : labels) {
            w = Math.max(w, font.width(c) + 12);
        }
        if (maxWidth > 0) {
            w = Math.min(w, maxWidth);
        }
        int h = labels.size() * ITEM_H;
        if (maxBottom > 0 && top + h > maxBottom) {
            top = Math.max(2, maxBottom - h);
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        LanPlusUI.panel(g, x, top, x + w, top + h);
        for (int i = 0; i < labels.size(); i++) {
            int iy = top + i * ITEM_H;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= iy && mouseY < iy + ITEM_H;
            boolean sel = i == selectedIndex;
            if (hover) {
                g.fill(x + 1, iy, x + w - 1, iy + ITEM_H, LanPlusUI.ACCENT_TINT);
            }
            if (sel) {
                g.fill(x + 1, iy, x + 2, iy + ITEM_H, LanPlusUI.ACCENT);
            }
            g.drawString(font, trim(font, labels.get(i), w - 10), x + 6, iy + 3,
                    sel ? LanPlusUI.ACCENT : (hover ? LanPlusUI.TEXT : LanPlusUI.MUTED), false);
            cells.add(new int[]{x, iy, w, ITEM_H, i});
        }
        g.pose().popPose();
    }

    public int clicked(double mx, double my) {
        for (int[] c : cells) {
            if (mx >= c[0] && mx < c[0] + c[2] && my >= c[1] && my < c[1] + c[3]) {
                return c[4];
            }
        }
        return -1;
    }

    private static Component trim(Font font, Component c, int maxWidth) {
        String s = c.getString();
        if (font.width(s) <= maxWidth) {
            return c;
        }
        return Component.literal(font.plainSubstrByWidth(s, maxWidth - font.width("…")) + "…");
    }
}