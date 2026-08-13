package dev.bgame.lanplus.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The LAN+ screen design language. Shared by every LAN+ screen.
 */
final class LanPlusUi {

    static final int SURFACE = 0xF21A1C22;
    static final int SURFACE_RAISED = 0xFF15171C;
    static final int SURFACE_HOVER = 0xFF262A33;
    static final int SURFACE_DISABLED = 0xFF191B21;
    static final int SLOT = 0xFF101216;
    static final int EDGE_LIGHT = 0xFF3A3F4E;
    static final int EDGE_DARK = 0xFF0A0B0E;
    static final int ACCENT = 0xFF7B8CFF;
    static final int ACCENT_STRONG = 0xFF6A7AE0;
    static final int ACCENT_HOVER = 0xFF92A0F2;
    static final int ACCENT_TINT = 0x407B8CFF;
    static final int ACCENT_LINE = 0xFF7B8CFF;
    static final int LINK = 0xFF9AA6FF;
    static final int ONLINE = 0xFF57C07A;
    static final int AMBER = 0xFFD8A43C;
    static final int RED = 0xFFD05656;
    static final int TEXT = 0xFFECEEF2;
    static final int MUTED = 0xFF8B909A;
    static final int FAINT = 0xFF6A6F78;
    static final int BORDER = 0xFF2A2C33;
    static final int DIVIDER = 0x14FFFFFF;
    static final int BACKDROP = 0xC00A0B0D;

    private LanPlusUi() {
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
