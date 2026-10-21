package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.Config;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

final class LanPlusUI {

    static final int LAVENDER = 0xFFA070F0;
    static final int LIME = 0xFFBEE85A;
    static final int LIME_DARK = 0xFF5E7D14;
    static final int ON_ACCENT = 0xFFFFFFFF;

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
        apply(Themes.resolve(Config.theme));
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

        int op = Config.uiOpacity;
        SURFACE = withOpacity(SURFACE, op);
        SURFACE_RAISED = withOpacity(SURFACE_RAISED, op);
        SURFACE_HOVER = withOpacity(SURFACE_HOVER, op);
        SURFACE_DISABLED = withOpacity(SURFACE_DISABLED, op);
        SLOT = withOpacity(SLOT, op);
    }

    private static int withOpacity(int argb, int pct) {
        int a = 255 * pct / 100;
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static boolean isLight(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 140;
    }

    static int contrastLime(int bg) {
        return isLight(bg) ? LIME_DARK : LIME;
    }

    static void background(GuiGraphics g, int width, int height) {
        int rgb = 0xFF000000 | (SURFACE & 0xFFFFFF);
        g.fillGradient(0, 0, width, height, shade(rgb, 0.22f), shade(rgb, 0.10f));
    }

    static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        int base = 0xFF000000 | (SURFACE & 0xFFFFFF);
        int frameDark = shade(base, 0.5f);
        int frameLight = shade(base, 1.25f);
        g.fill(x0, y0, x1, y1, SURFACE);
        outline1(g, x0, y0, x1, y1, frameDark);
        outline1(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, frameLight);
        g.fill(x0 + 2, y0 + 2, x1 - 2, y0 + 3, ACCENT_LINE);
    }

    static void popup(GuiGraphics g, int x0, int y0, int x1, int y1) {
        int base = 0xFF000000 | (SURFACE & 0xFFFFFF);
        g.fill(x0, y0, x1, y1, SURFACE);
        outline1(g, x0, y0, x1, y1, shade(base, 0.5f));
        outline1(g, x0 + 1, y0 + 1, x1 - 1, y1 - 1, shade(base, 1.25f));
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

    static void button3D(GuiGraphics g, int x0, int y0, int x1, int y1, int fill) {
        outline1(g, x0, y0, x1, y1, shade(fill, 0.55f));
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, shade(fill, 1.18f));
        g.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, shade(fill, 0.82f));
    }

    static void primaryButton(GuiGraphics g, int x0, int y0, int x1, int y1, boolean hover, boolean active) {
        int fill = !active ? SURFACE_DISABLED : hover ? ACCENT_HOVER : ACCENT_STRONG;
        outline1(g, x0, y0, x1, y1, shade(fill, 0.6f));
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, fill);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, shade(fill, 1.2f));
        g.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, shade(fill, 0.75f));
    }

    static void caret(GuiGraphics g, int cx, int cy, boolean up, int color) {
        if (up) {
            g.fill(cx, cy, cx + 1, cy + 1, color);
            g.fill(cx - 1, cy + 1, cx + 2, cy + 2, color);
            g.fill(cx - 2, cy + 2, cx + 3, cy + 3, color);
            g.fill(cx - 3, cy + 3, cx + 4, cy + 4, color);
        } else {
            g.fill(cx - 3, cy, cx + 4, cy + 1, color);
            g.fill(cx - 2, cy + 1, cx + 3, cy + 2, color);
            g.fill(cx - 1, cy + 2, cx + 2, cy + 3, color);
            g.fill(cx, cy + 3, cx + 1, cy + 4, color);
        }
    }

    static void slot(GuiGraphics g, int x0, int y0, int x1, int y1) {
        outline1(g, x0, y0, x1, y1, EDGE_DARK);
        int a = x0 + 1;
        int b = y0 + 1;
        int c = x1 - 1;
        int d = y1 - 1;
        g.fill(a, b, c, d, SLOT);
        g.fill(a, b, c, b + 1, shade(SLOT, 0.82f));
        g.fill(a, b, a + 1, d, shade(SLOT, 0.82f));
        g.fill(a, d - 1, c, d, shade(SLOT, 1.12f));
        g.fill(c - 1, b, c, d, shade(SLOT, 1.12f));
    }

    static void sectionHeader(GuiGraphics g, Font font, Component label, int x, int y, int right) {
        g.drawString(font, label, x, y, TEXT, false);
        g.fill(x, y + 11, right, y + 12, BORDER);
    }

    static int wordmark(GuiGraphics g, Font font, int x, int y) {
        g.drawString(font, "LAN", x, y, LAVENDER, false);
        x += font.width("LAN");
        g.drawString(font, "+", x, y, contrastLime(SURFACE), false);
        return x + font.width("+");
    }

    static void textCentered(GuiGraphics g, Font font, Component label, int x, int y, int w, int color) {
        g.drawString(font, label, x + (w - font.width(label)) / 2, y, color, false);
    }

    static void scrollingText(GuiGraphics g, Font font, Component label, int x, int y, int maxWidth, int color) {
        int tw = font.width(label);
        if (tw <= maxWidth) {
            g.drawString(font, label, x, y, color, false);
            return;
        }
        int diff = tw - maxWidth;
        double period = Math.max(diff * 0.5, 3.0);
        double seconds = System.currentTimeMillis() / 1000.0;
        double phase = Math.sin((Math.PI / 2) * Math.cos((Math.PI * 2) * seconds / period)) / 2.0 + 0.5;
        g.enableScissor(x, y - 1, x + maxWidth, y + 9);
        g.pose().pushPose();
        g.pose().translate(-phase * diff, 0, 0);
        g.drawString(font, label, x, y, color, false);
        g.pose().popPose();
        g.disableScissor();
    }

    static void chip(GuiGraphics g, Font font, Component label, int x, int y, int w, int h,
                     boolean selected, boolean enabled, boolean hover) {
        int bg = !enabled ? SURFACE_DISABLED : selected ? ACCENT_STRONG : (hover ? SURFACE_HOVER : SURFACE_RAISED);
        button3D(g, x, y, x + w, y + h, bg);
        int color = !enabled ? FAINT : selected ? ON_ACCENT : hover ? MUTED : TEXT;
        textCentered(g, font, label, x, y + (h - 8) / 2, w, color);
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
