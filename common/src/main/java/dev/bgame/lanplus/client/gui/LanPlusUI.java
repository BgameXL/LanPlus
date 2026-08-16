package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.Config;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class LanPlusUI {

    static final int LAVENDER = 0xFFA070F0;
    static final int LIME = 0xFFBEE85A;
    static int SURFACE;
    static int SURFACE_RAISED;
    static int SURFACE_HOVER;
    static int SURFACE_DISABLED;
    static int SLOT;
    static int EDGE_LIGHT;
    static int EDGE_DARK;
    static int ACCENT;
    static int ACCENT_STRONG;
    static int ACCENT_HOVER;
    static int ACCENT_TINT;
    static int ACCENT_LINE;
    static int LINK;
    static int ONLINE;
    static int AMBER;
    static int RED;
    static int TEXT;
    static int MUTED;
    static int FAINT;
    static int BORDER;
    static int DIVIDER;
    static int BACKDROP;

    private static Theme current;

    static {
        apply(Themes.byId(Config.theme));
    }

    private LanPlusUI() {
    }

    static Theme current() {
        return current;
    }

    static void apply(Theme t) {
        current = t;
        SURFACE = t.surface();
        SURFACE_RAISED = t.surfaceRaised();
        SURFACE_HOVER = t.surfaceHover();
        SURFACE_DISABLED = t.surfaceDisabled();
        SLOT = t.slot();
        EDGE_LIGHT = t.edgeLight();
        EDGE_DARK = t.edgeDark();
        ACCENT = t.accent();
        ACCENT_STRONG = t.accentStrong();
        ACCENT_HOVER = t.accentHover();
        ACCENT_TINT = t.accentTint();
        ACCENT_LINE = t.accentLine();
        LINK = t.link();
        ONLINE = t.online();
        AMBER = t.amber();
        RED = t.red();
        TEXT = t.text();
        MUTED = t.muted();
        FAINT = t.faint();
        BORDER = t.border();
        DIVIDER = t.divider();
        BACKDROP = t.backdrop();
    }

    static void backdrop(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, BACKDROP);
    }

    static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, SURFACE);
        bevelRaised(g, x0, y0, x1, y1);
        g.fill(x0, y0, x1, y0 + 1, ACCENT_LINE);
    }

    static void border(GuiGraphics g, int x0, int y0, int x1, int y1) {
        bevelRaised(g, x0, y0, x1, y1);
    }

    static void bevelRaised(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y0 + 1, EDGE_LIGHT);
        g.fill(x0, y0, x0 + 1, y1, EDGE_LIGHT);
        g.fill(x0, y1 - 1, x1, y1, EDGE_DARK);
        g.fill(x1 - 1, y0, x1, y1, EDGE_DARK);
    }

    static void bevelInset(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y0 + 1, EDGE_DARK);
        g.fill(x0, y0, x0 + 1, y1, EDGE_DARK);
        g.fill(x0, y1 - 1, x1, y1, EDGE_LIGHT);
        g.fill(x1 - 1, y0, x1, y1, EDGE_LIGHT);
    }

    static void header(GuiGraphics g, Font font, Component label, int x, int y, int width) {
        g.drawString(font, label, x, y, TEXT, false);
        g.fill(x, y + 11, x + width, y + 12, ACCENT_LINE);
    }

    static int wordmark(GuiGraphics g, Font font, int x, int y) {
        g.drawString(font, "LAN", x, y, LAVENDER, false);
        x += font.width("LAN");
        g.drawString(font, "+", x, y, LIME, false);
        return x + font.width("+");
    }

    static void chip(GuiGraphics g, Font font, Component label, int x, int y, int w, int h,
                     boolean selected, boolean enabled, boolean hover) {
        int bg = !enabled ? SURFACE_DISABLED : selected ? ACCENT_STRONG : (hover ? SURFACE_HOVER : SURFACE_RAISED);
        g.fill(x, y, x + w, y + h, bg);
        bevelRaised(g, x, y, x + w, y + h);
        int color = !enabled ? FAINT : selected || hover ? TEXT : MUTED;
        int tx = x + (w - font.width(label)) / 2;
        g.drawString(font, label, tx, y + (h - 8) / 2, color, false);
    }
}
