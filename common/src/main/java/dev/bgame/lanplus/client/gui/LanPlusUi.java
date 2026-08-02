package dev.bgame.lanplus.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The LAN+ screen design language (dark surfaces + blurple accents), shared by the profile,
 * friends and host screens so they read as one product instead of assorted vanilla panels.
 */
final class LanPlusUi {

    static final int SURFACE = 0xF21E1F22;
    static final int SURFACE_RAISED = 0xFF2B2D31;
    static final int BLURPLE = 0xFF5865F2;
    static final int BLURPLE_HOVER = 0xFF6B77F5;
    static final int BLURPLE_TINT = 0x334752C4;
    static final int GREEN = 0xFF3BA55D;
    static final int AMBER = 0xFFE8A317;
    static final int RED = 0xFFDA373C;
    static final int BORDER = 0x33FFFFFF;
    static final int DIVIDER = 0x18FFFFFF;
    static final int ACCENT_LINE = 0x804752C4;
    static final int TEXT = 0xFFF2F3F5;
    static final int MUTED = 0xFFB5BAC1;
    static final int FAINT = 0xFF80848E;
    static final int BACKDROP = 0xB80A0C10;

    private LanPlusUi() {}

    static void backdrop(GuiGraphics g, int width, int height) {
        g.fill(0, 0, width, height, BACKDROP);
    }

    static void panel(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, SURFACE);
        border(g, x0, y0, x1, y1);
    }

    static void border(GuiGraphics g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y0 + 1, BORDER);
        g.fill(x0, y1 - 1, x1, y1, BORDER);
        g.fill(x0, y0, x0 + 1, y1, BORDER);
        g.fill(x1 - 1, y0, x1, y1, BORDER);
    }

    static void header(GuiGraphics g, Font font, Component label, int x, int y, int width) {
        g.drawString(font, label, x, y, TEXT, false);
        g.fill(x, y + 11, x + width, y + 12, ACCENT_LINE);
    }

    static void chip(GuiGraphics g, Font font, Component label, int x, int y, int w, int h,
                     boolean selected, boolean enabled, boolean hover) {
        int bg = !enabled ? 0xFF232428 : selected ? BLURPLE : (hover ? 0xFF35373C : SURFACE_RAISED);
        g.fill(x, y, x + w, y + h, bg);
        border(g, x, y, x + w, y + h);
        int color = !enabled ? FAINT : selected || hover ? TEXT : MUTED;
        int tx = x + (w - font.width(label)) / 2;
        g.drawString(font, label, tx, y + (h - 8) / 2, color, false);
    }
}
