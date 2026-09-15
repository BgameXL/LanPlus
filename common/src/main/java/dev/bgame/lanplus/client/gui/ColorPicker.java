package dev.bgame.lanplus.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

final class ColorPicker {

    private static final int SV = 104;
    private static final int HUE_W = 14;
    private static final int HUE_GAP = 10;
    private static final int PREVIEW_GAP = 14;
    private static final int PREVIEW_W = 84;
    private static final int PREVIEW_H = 44;

    private final Font font;
    private final EditBox hexBox;
    private int x, y;
    private float hue, sat, val;
    private int color;
    private boolean svDrag, hueDrag;

    ColorPicker(Font font, int rgb) {
        this.font = font;
        this.hexBox = new EditBox(font, 0, 0, PREVIEW_W, 18, Component.literal("hex"));
        this.hexBox.setMaxLength(6);
        this.hexBox.setResponder(this::onHexTyped);
        setColor(rgb);
    }

    EditBox hexBox() {
        return hexBox;
    }

    void layout(int x, int y) {
        this.x = x;
        this.y = y;
        int rx = x + SV + HUE_GAP + HUE_W + PREVIEW_GAP;
        hexBox.setPosition(rx, y + 52);
        hexBox.setWidth(PREVIEW_W);
    }

    int color() {
        return color;
    }

    void setColor(int rgb) {
        setColorInternal(rgb);
        hexBox.setValue(String.format("%06X", color));
    }

    private void setColorInternal(int rgb) {
        color = rgb & 0xFFFFFF;
        float[] hsv = toHSV(color);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
    }

    private void onHexTyped(String s) {
        String t = s.trim();
        if (t.length() == 6) {
            try {
                setColorInternal(Integer.parseInt(t, 16));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    void render(GuiGraphics g) {
        int svX = x;
        int svY = y;
        int hueX = x + SV + HUE_GAP;
        int rx = hueX + HUE_W + PREVIEW_GAP;

        int step = 4;
        for (int px = 0; px < SV; px += step) {
            float s = px / (float) (SV - 1);
            for (int py = 0; py < SV; py += step) {
                float v = 1f - py / (float) (SV - 1);
                g.fill(svX + px, svY + py, svX + Math.min(px + step, SV), svY + Math.min(py + step, SV),
                        0xFF000000 | hsv(hue, s, v));
            }
        }
        LanPlusUI.outline1(g, svX, svY, svX + SV, svY + SV, LanPlusUI.EDGE_DARK);
        int hx = Math.clamp(svX + Math.round(sat * (SV - 1)), svX + 4, svX + SV - 5);
        int hy = Math.clamp(svY + Math.round((1f - val) * (SV - 1)), svY + 4, svY + SV - 5);
        LanPlusUI.outline1(g, hx - 4, hy - 4, hx + 5, hy + 5, 0xFF000000);
        LanPlusUI.outline1(g, hx - 3, hy - 3, hx + 4, hy + 4, 0xFFFFFFFF);

        for (int py = 0; py < SV; py += 3) {
            float h = py / (float) (SV - 1) * 360f;
            g.fill(hueX, svY + py, hueX + HUE_W, svY + Math.min(py + 3, SV), 0xFF000000 | hsv(h, 1f, 1f));
        }
        LanPlusUI.outline1(g, hueX, svY, hueX + HUE_W, svY + SV, LanPlusUI.EDGE_DARK);
        int huey = svY + Math.round(hue / 360f * (SV - 1));
        g.fill(hueX - 2, huey - 1, hueX + HUE_W + 2, huey + 1, 0xFFFFFFFF);

        g.fill(rx, svY, rx + PREVIEW_W, svY + PREVIEW_H, 0xFF000000 | color);
        LanPlusUI.outline1(g, rx, svY, rx + PREVIEW_W, svY + PREVIEW_H, LanPlusUI.EDGE_DARK);
        g.drawString(font, "#", rx - 9, svY + 57, LanPlusUI.FAINT, false);
    }

    boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        int hueX = x + SV + HUE_GAP;
        if (mouseX >= x && mouseX < x + SV && mouseY >= y && mouseY < y + SV) {
            svDrag = true;
            setSV(mouseX, mouseY);
            return true;
        }
        if (mouseX >= hueX && mouseX < hueX + HUE_W && mouseY >= y && mouseY < y + SV) {
            hueDrag = true;
            setHue(mouseY);
            return true;
        }
        return false;
    }

    boolean mouseDragged(double mouseX, double mouseY) {
        if (svDrag) {
            setSV(mouseX, mouseY);
            return true;
        }
        if (hueDrag) {
            setHue(mouseY);
            return true;
        }
        return false;
    }

    void mouseReleased() {
        svDrag = false;
        hueDrag = false;
    }

    private void setSV(double mx, double my) {
        sat = Math.clamp((float) (mx - x) / (SV - 1), 0f, 1f);
        val = 1f - Math.clamp((float) (my - y) / (SV - 1), 0f, 1f);
        applyHsv();
    }

    private void setHue(double my) {
        hue = Math.clamp((float) (my - y) / (SV - 1), 0f, 1f) * 360f;
        applyHsv();
    }

    private void applyHsv() {
        color = hsv(hue, sat, val);
        hexBox.setValue(String.format("%06X", color));
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
