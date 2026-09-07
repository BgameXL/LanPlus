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
    }

    static void background(GuiGraphics g, int width, int height) {
        int base = 0xFF000000 | (SURFACE & 0xFFFFFF);
        g.fillGradient(0, 0, width, height, base, shade(base, 0.32f));
    }

    static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        outline1(g, x0, y0, x1, y1, EDGE_DARK);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, SURFACE);
        outline1(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, shade(SURFACE, 1.6f));
    }

    static void outline(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        outline1(g, x0, y0, x1, y1, color);
    }

    static void outline1(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    static int shade(int argb, float f) {
        int a = argb >>> 24;
        int r = clampByte(Math.round(((argb >> 16) & 0xFF) * f));
        int g = clampByte(Math.round(((argb >> 8) & 0xFF) * f));
        int b = clampByte(Math.round((argb & 0xFF) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clampByte(int v) {
        return Math.clamp(v, 0, 255);
    }

    static void button3d(GuiGraphics g, int x0, int y0, int x1, int y1, int fill) {
        outline1(g, x0, y0, x1, y1, EDGE_DARK);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
        outline1(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, shade(fill, 1.4f));
    }

    static void primaryButton(GuiGraphics g, int x0, int y0, int x1, int y1, boolean hover, boolean active) {
        int fill = !active ? SURFACE_DISABLED : hover ? shade(ACCENT_STRONG, 0.4f) : SURFACE_RAISED;
        outline1(g, x0, y0, x1, y1, EDGE_DARK);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
        int accent = !active ? BORDER : hover ? ACCENT_HOVER : ACCENT;
        outline1(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, accent);
        g.fill(x0 + 2, y0 + 1, x1 - 2, y0 + 2, accent);
        g.fill(x0 + 2, y1 - 2, x1 - 2, y1 - 1, accent);
        if (hover && active) {
            int glow = 0x55000000 | (ACCENT & 0xFFFFFF);
            g.fill(x0 - 1, y0 - 1, x1 + 1, y0, glow);
            g.fill(x0 - 1, y1, x1 + 1, y1 + 1, glow);
            g.fill(x0 - 1, y0, x0, y1, glow);
            g.fill(x1, y0, x1 + 1, y1, glow);
        }
    }

    static void slot(GuiGraphics g, int x0, int y0, int x1, int y1) {
        outline1(g, x0, y0, x1, y1, EDGE_DARK);
        int a = x0 + 1;
        int b = y0 + 1;
        int c = x1 - 1;
        int d = y1 - 1;
        g.fill(a, b, c, d, SLOT);
        g.fill(a, b, c, b + 1, shade(SLOT, 0.4f));
        g.fill(a, b, a + 1, d, shade(SLOT, 0.4f));
        g.fill(a, d - 1, c, d, shade(SLOT, 2.2f));
        g.fill(c - 1, b, c, d, shade(SLOT, 2.2f));
    }

    static void rivets(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0 + 3, y0 + 3, x0 + 5, y0 + 5, color);
        g.fill(x1 - 5, y0 + 3, x1 - 3, y0 + 5, color);
        g.fill(x0 + 3, y1 - 5, x0 + 5, y1 - 3, color);
        g.fill(x1 - 5, y1 - 5, x1 - 3, y1 - 3, color);
    }

    static void sectionHeader(GuiGraphics g, Font font, Component label, int x, int y, int right) {
        g.drawString(font, "+", x, y, LIME, false);
        int labelX = x + font.width("+") + 4;
        g.drawString(font, label, labelX, y, TEXT, false);
        g.fill(x, y + 11, right, y + 12, BORDER);
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
        button3d(g, x, y, x + w, y + h, bg);
        int color = !enabled ? FAINT : selected || hover ? TEXT : MUTED;
        int tx = x + (w - font.width(label)) / 2;
        g.drawString(font, label, tx, y + (h - 8) / 2, color, false);
    }

    static String relativeTime(long epochMillis) {
        long seconds = Math.max(0, (System.currentTimeMillis() - epochMillis) / 1000L);
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m";
        }
        if (seconds < 86400) {
            return (seconds / 3600) + "h";
        }
        return (seconds / 86400) + "d";
    }
}
