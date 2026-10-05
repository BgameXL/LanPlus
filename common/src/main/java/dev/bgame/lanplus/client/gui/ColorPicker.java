package dev.bgame.lanplus.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class ColorPicker {

    private static final int SV_SIZE = 104;
    private static final int SV_HUE_GAP = 8;
    private static final int HUE_H = 12;
    private static final int HUE_GAP = 10;
    private static final int HEX_H = 16;
    private static final int HEX_BOX_W = 52;
    private static final int RGB_GAP = 4;
    private static final int RGB_H = 9;
    private static final int SWATCH = 12;
    private static final int STEP = 2;

    private static final int DRAG_NONE = -1;
    private static final int DRAG_SV = 0;
    private static final int DRAG_HUE = 1;

    private final Font font;
    private final EditBox hexBox;
    private int x, y, w;
    private float hue, sat, val;
    private int color;
    private int drag = DRAG_NONE;
    private boolean syncing;

    ColorPicker(Font font, int rgb) {
        this.font = font;
        this.hexBox = new EditBox(font, 0, 0, HEX_BOX_W, HEX_H, Component.literal("hex"));
        this.hexBox.setMaxLength(6);
        this.hexBox.setResponder(this::onHexTyped);
        setColor(rgb);
    }

    EditBox hexBox() {
        return hexBox;
    }

    void layout(int x, int y, int w) {
        this.x = x;
        this.y = y;
        this.w = w;
        int hashW = font.width("#");
        int lineW = SWATCH + 6 + hashW + 3 + HEX_BOX_W;
        int lineX = x + (w - lineW) / 2;
        hexBox.setPosition(lineX + SWATCH + 6 + hashW + 3, hexTop());
        hexBox.setWidth(HEX_BOX_W);
    }

    static int preferredHeight() {
        return SV_SIZE + SV_HUE_GAP + HUE_H + HUE_GAP + HEX_H + RGB_GAP + RGB_H;
    }

    int height() {
        return preferredHeight();
    }

    int color() {
        return color;
    }

    void setColor(int rgb) {
        setColorInternal(rgb);
        syncHexBox();
    }

    private void syncHexBox() {
        syncing = true;
        hexBox.setValue(String.format("%06X", color));
        syncing = false;
    }

    private void setColorInternal(int rgb) {
        color = rgb & 0xFFFFFF;
        float[] hsv = toHSV(color);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
    }

    private void onHexTyped(String s) {
        if (syncing) {
            return;
        }
        String t = s.trim();
        if (t.length() == 6) {
            try {
                setColorInternal(Integer.parseInt(t, 16));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    void render(GuiGraphics g) {
        renderSvField(g);
        renderHueBar(g);

        int hashW = font.width("#");
        int lineW = SWATCH + 6 + hashW + 3 + HEX_BOX_W;
        int lineX = x + (w - lineW) / 2;
        int chipY = hexTop() + (HEX_H - SWATCH) / 2;
        g.fill(lineX, chipY, lineX + SWATCH, chipY + SWATCH, 0xFF000000 | color);
        LanPlusUI.outline1(g, lineX, chipY, lineX + SWATCH, chipY + SWATCH, LanPlusUI.EDGE_DARK);
        g.drawString(font, "#", lineX + SWATCH + 6, hexTop() + (HEX_H - 8) / 2, LanPlusUI.FAINT, false);

        int r = (color >> 16) & 0xFF;
        int gc = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        String rgbLabel = "RGB ";
        String rgbVal = r + " " + gc + " " + b;
        int rgbX = x + (w - font.width(rgbLabel) - font.width(rgbVal)) / 2;
        g.drawString(font, rgbLabel, rgbX, rgbTop(), LanPlusUI.MUTED, false);
        g.drawString(font, rgbVal, rgbX + font.width(rgbLabel), rgbTop(), LanPlusUI.TEXT, false);
    }

    private void renderSvField(GuiGraphics g) {
        int fx = svX();
        int fy = svY();
        int pure = hsv(hue, 1f, 1f);
        for (int px = 0; px < SV_SIZE; px += STEP) {
            float s = px / (float) (SV_SIZE - 1);
            int col = lerp(0xFFFFFF, pure, s);
            g.fill(fx + px, fy, fx + Math.min(px + STEP, SV_SIZE), fy + SV_SIZE, 0xFF000000 | col);
        }
        g.fillGradient(fx, fy, fx + SV_SIZE, fy + SV_SIZE, 0x00000000, 0xFF000000);
        LanPlusUI.outline1(g, fx, fy, fx + SV_SIZE, fy + SV_SIZE, LanPlusUI.EDGE_DARK);

        int mx = Math.clamp(fx + Math.round(sat * (SV_SIZE - 1)), fx, fx + SV_SIZE - 1);
        int my = Math.clamp(fy + Math.round((1f - val) * (SV_SIZE - 1)), fy, fy + SV_SIZE - 1);
        LanPlusUI.outline1(g, mx - 3, my - 3, mx + 4, my + 4, 0xFF000000);
        LanPlusUI.outline1(g, mx - 2, my - 2, mx + 3, my + 3, 0xFFFFFFFF);
    }

    private void renderHueBar(GuiGraphics g) {
        int left = hueLeft();
        int top = hueY();
        int tw = w;
        for (int px = 0; px < tw; px += STEP) {
            float t = px / (float) (tw - 1);
            g.fill(left + px, top, left + Math.min(px + STEP, tw), top + HUE_H, 0xFF000000 | hsv(t * 360f, 1f, 1f));
        }
        LanPlusUI.outline1(g, left, top, left + tw, top + HUE_H, LanPlusUI.EDGE_DARK);

        int hx = Math.clamp(left + Math.round(hue / 360f * (tw - 1)), left, left + tw - 1);
        LanPlusUI.outline1(g, hx - 2, top - 2, hx + 3, top + HUE_H + 2, 0xFF000000);
        g.fill(hx - 1, top - 1, hx + 2, top + HUE_H + 1, 0xFFFFFFFF);
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (inSvField(mouseX, mouseY)) {
            drag = DRAG_SV;
            setFromSv(mouseX, mouseY);
            return true;
        }
        if (inHueBar(mouseX, mouseY)) {
            drag = DRAG_HUE;
            setFromHue(mouseX);
            return true;
        }
        return false;
    }

    boolean mouseDragged(double mouseX, double mouseY) {
        if (drag == DRAG_SV) {
            setFromSv(mouseX, mouseY);
            return true;
        }
        if (drag == DRAG_HUE) {
            setFromHue(mouseX);
            return true;
        }
        return false;
    }

    void mouseReleased() {
        drag = DRAG_NONE;
    }

    private boolean inSvField(double mouseX, double mouseY) {
        return mouseX >= svX() && mouseX <= svX() + SV_SIZE && mouseY >= svY() && mouseY <= svY() + SV_SIZE;
    }

    private boolean inHueBar(double mouseX, double mouseY) {
        return mouseX >= hueLeft() - 4 && mouseX <= hueLeft() + w + 4
                && mouseY >= hueY() - 2 && mouseY <= hueY() + HUE_H + 2;
    }

    private void setFromSv(double mouseX, double mouseY) {
        sat = Math.clamp((float) (mouseX - svX()) / (SV_SIZE - 1), 0f, 1f);
        val = Math.clamp(1f - (float) (mouseY - svY()) / (SV_SIZE - 1), 0f, 1f);
        applyHsv();
    }

    private void setFromHue(double mouseX) {
        hue = Math.clamp((float) (mouseX - hueLeft()) / (w - 1), 0f, 1f) * 360f;
        applyHsv();
    }

    private int svX() {
        return x + (w - SV_SIZE) / 2;
    }

    private int svY() {
        return y;
    }

    private int hueLeft() {
        return x;
    }

    private int hueY() {
        return y + SV_SIZE + SV_HUE_GAP;
    }

    private int hexTop() {
        return hueY() + HUE_H + HUE_GAP;
    }

    private int rgbTop() {
        return hexTop() + HEX_H + RGB_GAP;
    }

    private void applyHsv() {
        color = hsv(hue, sat, val);
        syncHexBox();
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int gc = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return (r << 16) | (gc << 8) | bl;
    }

    private static int hsv(float h, float s, float v) {
        float c = v * s;
        float hp = ((h % 360f) + 360f) % 360f / 60f;
        float x = c * (1f - Math.abs(hp % 2f - 1f));
        float r = 0, g = 0, b = 0;
        if (hp < 1) {
            r = c;
            g = x;
        } else if (hp < 2) {
            r = x;
            g = c;
        } else if (hp < 3) {
            g = c;
            b = x;
        } else if (hp < 4) {
            g = x;
            b = c;
        } else if (hp < 5) {
            r = x;
            b = c;
        } else {
            r = c;
            b = x;
        }
        float m = v - c;
        int ri = Math.round((r + m) * 255f);
        int gi = Math.round((g + m) * 255f);
        int bi = Math.round((b + m) * 255f);
        return (ri << 16) | (gi << 8) | bi;
    }

    private static float[] toHSV(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h = 0f;
        if (d != 0f) {
            if (max == r) {
                h = ((g - b) / d) % 6f;
            } else if (max == g) {
                h = (b - r) / d + 2f;
            } else {
                h = (r - g) / d + 4f;
            }
            h *= 60f;
            if (h < 0) {
                h += 360f;
            }
        }
        return new float[]{h, max == 0f ? 0f : d / max, max};
    }
}
