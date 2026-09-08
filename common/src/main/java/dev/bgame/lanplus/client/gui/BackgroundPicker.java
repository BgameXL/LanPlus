package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.api.CatalogImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class BackgroundPicker extends LanPlusScreen {

    interface Sink {
        void solid(int color);

        void image(CatalogImage img);

        void none();
    }

    private static final int MARGIN = 20;
    private static final int MAX_W = 460;
    private static final int PAD = 12;
    private static final int SWATCH = 24;
    private static final int SWATCH_GAP = 6;
    private static final int SV_W = 104;
    private static final int SV_H = 104;
    private static final int HUE_W = 14;
    private static final int SIDE_W = 84;
    private static final int IMG_COLS = 3;
    private static final int IMG_GAP = 8;

    private final Screen parent;
    private final int[] palette;
    private final List<CatalogImage> images;
    private final String currentImageId;
    private final Sink sink;

    private int tab;
    private float hue, sat, val;
    private int color;
    private boolean svDrag, hueDrag;
    private int scrollY;
    private EditBox hexBox;

    private int boxX, contentW, panelTop, panelBottom, bodyTop;
    private int svX, svY, hueX, customTop;
    private int imgTop, imgBottom, cellW, cellH, rowH;
    private final List<int[]> swatchCells = new ArrayList<>();
    private final List<ImgCell> imgCells = new ArrayList<>();

    private record ImgCell(int x, int y, int w, int h, CatalogImage image, boolean none) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    BackgroundPicker(Screen parent, int style, int color, int[] palette,
                     String currentImageId, List<CatalogImage> images, Sink sink) {
        super(Component.translatable("gui.lanplus.profile.bg.header"));
        this.parent = parent;
        this.palette = palette;
        this.images = images == null ? List.of() : images;
        this.currentImageId = currentImageId;
        this.sink = sink;
        this.tab = style == 3 ? 1 : 0;
        setColor(color & 0xFFFFFF);
    }

    private void setColor(int rgb) {
        this.color = rgb & 0xFFFFFF;
        float[] hsv = toHSV(this.color);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
    }

    private void syncColor() {
        this.color = hsv(hue, sat, val);
        if (hexBox != null) {
            hexBox.setValue(String.format("%06X", color));
        }
    }

    @Override
    protected void init() {
        contentW = Math.min(this.width - 2 * MARGIN, MAX_W);
        boxX = (this.width - contentW) / 2;
        panelTop = 30;
        panelBottom = this.height - 40;
        bodyTop = panelTop + 46;

        int presetCols = Math.min(8, palette.length);
        int presetRows = (palette.length + presetCols - 1) / presetCols;
        customTop = bodyTop + presetRows * (SWATCH + SWATCH_GAP) + 12;
        svX = boxX + PAD;
        svY = customTop;
        hueX = svX + SV_W + 10;

        imgTop = bodyTop;
        imgBottom = panelBottom - 6;
        cellW = (contentW - 2 * PAD - (IMG_COLS - 1) * IMG_GAP) / IMG_COLS;
        cellH = Math.round(cellW * 9f / 16f);
        rowH = cellH + 12 + IMG_GAP;

        addRenderableWidget(LanplusButton.create(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(boxX + contentW - 90, panelBottom + 8, 90, 20).build());

        if (tab == 0) {
            int rx = hueX + HUE_W + 14;
            hexBox = new EditBox(this.font, rx, customTop + 52, SIDE_W, 18, Component.literal("hex"));
            hexBox.setMaxLength(6);
            hexBox.setValue(String.format("%06X", color));
            hexBox.setResponder(this::onHexTyped);
            addRenderableWidget(hexBox);
            addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.profile.bg.use"),
                            b -> commitSolid())
                    .bounds(rx, customTop + 74, SIDE_W, 18).primary().build());
        } else {
            hexBox = null;
        }
    }

    private void onHexTyped(String s) {
        String t = s.trim();
        if (t.length() == 6) {
            try {
                setColor(Integer.parseInt(t, 16));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private void selectTab(int t) {
        if (t != tab) {
            tab = t;
            scrollY = 0;
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawBackdrop(g);
        LanPlusUI.panel(g, boxX, panelTop, boxX + contentW, panelBottom);
        int wx = LanPlusUI.wordmark(g, this.font, boxX + 10, panelTop + 8);
        g.drawString(this.font, this.title, wx + 6, panelTop + 8, LanPlusUI.MUTED, false);
        g.fill(boxX + 10, panelTop + 20, boxX + contentW - 10, panelTop + 21, LanPlusUI.DIVIDER);

        renderTabs(g, mouseX, mouseY);
        if (tab == 0) {
            renderSolid(g, mouseX, mouseY);
        } else {
            renderImages(g, mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int tabW = (contentW - 20) / 2;
        String[] labels = {"gui.lanplus.profile.bg.tab.solid", "gui.lanplus.profile.bg.tab.image"};
        for (int i = 0; i < 2; i++) {
            int x = boxX + 10 + i * tabW;
            int y = panelTop + 26;
            boolean active = tab == i;
            boolean hover = mouseX >= x && mouseX < x + tabW && mouseY >= y && mouseY < y + 14;
            Component label = Component.translatable(labels[i]);
            g.drawString(this.font, label, x + (tabW - this.font.width(label)) / 2, y + 2,
                    active ? LanPlusUI.TEXT : (hover ? LanPlusUI.MUTED : LanPlusUI.FAINT), false);
            if (active) {
                g.fill(x + 6, y + 13, x + tabW - 6, y + 14, LanPlusUI.ACCENT);
            }
        }
    }

    private void renderSolid(GuiGraphics g, int mouseX, int mouseY) {
        swatchCells.clear();
        int cols = Math.min(8, palette.length);
        int x0 = boxX + PAD;
        for (int i = 0; i < palette.length; i++) {
            int cx = x0 + (i % cols) * (SWATCH + SWATCH_GAP);
            int cy = bodyTop + (i / cols) * (SWATCH + SWATCH_GAP);
            int c = palette[i] & 0xFFFFFF;
            swatchCells.add(new int[]{cx, cy, c});
            g.fill(cx, cy, cx + SWATCH, cy + SWATCH, 0xFF000000 | c);
            if (c == color) {
                LanPlusUI.outline1(g, cx - 2, cy - 2, cx + SWATCH + 2, cy + SWATCH + 2, LanPlusUI.ACCENT);
            } else {
                LanPlusUI.outline1(g, cx, cy, cx + SWATCH, cy + SWATCH, LanPlusUI.EDGE_DARK);
            }
            if (mouseX >= cx && mouseX < cx + SWATCH && mouseY >= cy && mouseY < cy + SWATCH && c != color) {
                g.fill(cx, cy, cx + SWATCH, cy + SWATCH, 0x22FFFFFF);
            }
        }

        int step = 4;
        for (int px = 0; px < SV_W; px += step) {
            float s = px / (float) (SV_W - 1);
            for (int py = 0; py < SV_H; py += step) {
                float v = 1f - py / (float) (SV_H - 1);
                g.fill(svX + px, svY + py, svX + Math.min(px + step, SV_W), svY + Math.min(py + step, SV_H),
                        0xFF000000 | hsv(hue, s, v));
            }
        }

        // sv picker don't forget bgame
        LanPlusUI.outline1(g, svX, svY, svX + SV_W, svY + SV_H, LanPlusUI.EDGE_DARK);
        int hx = Math.clamp(svX + Math.round(sat * (SV_W - 1)), svX + 4, svX + SV_W - 5);
        int hy = Math.clamp(svY + Math.round((1f - val) * (SV_H - 1)), svY + 4, svY + SV_H - 5);
        LanPlusUI.outline1(g, hx - 4, hy - 4, hx + 5, hy + 5, 0xFF000000);
        LanPlusUI.outline1(g, hx - 3, hy - 3, hx + 4, hy + 4, 0xFFFFFFFF);

        for (int py = 0; py < SV_H; py += 3) {
            float h = py / (float) (SV_H - 1) * 360f;
            g.fill(hueX, svY + py, hueX + HUE_W, svY + Math.min(py + 3, SV_H), 0xFF000000 | hsv(h, 1f, 1f));
        }
        LanPlusUI.outline1(g, hueX, svY, hueX + HUE_W, svY + SV_H, LanPlusUI.EDGE_DARK);
        int huey = svY + Math.round(hue / 360f * (SV_H - 1));
        g.fill(hueX - 2, huey - 1, hueX + HUE_W + 2, huey + 1, 0xFFFFFFFF);

        int rx = hueX + HUE_W + 14;
        g.fill(rx, customTop, rx + SIDE_W, customTop + 44, 0xFF000000 | color);
        LanPlusUI.outline1(g, rx, customTop, rx + SIDE_W, customTop + 44, LanPlusUI.EDGE_DARK);
        g.drawString(this.font, "#", rx - 9, customTop + 57, LanPlusUI.FAINT, false);
    }

    private void renderImages(GuiGraphics g, int mouseX, int mouseY) {
        imgCells.clear();
        List<CatalogImage> list = new ArrayList<>();
        list.add(null);
        list.addAll(images);
        g.enableScissor(boxX, imgTop, boxX + contentW, imgBottom);
        int x0 = boxX + PAD;
        for (int i = 0; i < list.size(); i++) {
            int cx = x0 + (i % IMG_COLS) * (cellW + IMG_GAP);
            int cy = imgTop + (i / IMG_COLS) * rowH - scrollY;
            CatalogImage img = list.get(i);
            boolean none = img == null;
            imgCells.add(new ImgCell(cx, cy, cellW, cellH, img, none));
            if (cy + cellH < imgTop || cy > imgBottom) {
                continue;
            }
            boolean selected = none ? currentImageId == null : img.id().equals(currentImageId);
            boolean hover = mouseX >= cx && mouseX < cx + cellW && mouseY >= cy && mouseY < cy + cellH;
            g.fill(cx, cy, cx + cellW, cy + cellH, LanPlusUI.SLOT);
            if (none) {
                g.drawCenteredString(this.font, Component.translatable("gui.lanplus.profile.picker.none"),
                        cx + cellW / 2, cy + cellH / 2 - 4, LanPlusUI.MUTED);
            } else {
                ProfileImages.Tex tex = ProfileImages.get(img);
                if (tex != null) {
                    ProfileImages.blitCover(g, tex, cx, cy, cellW, cellH);
                }
            }
            if (selected) {
                LanPlusUI.outline1(g, cx, cy, cx + cellW, cy + cellH, LanPlusUI.ACCENT);
                LanPlusUI.outline1(g, cx + 1, cy + 1, cx + cellW - 1, cy + cellH - 1, LanPlusUI.ACCENT);
            } else if (hover) {
                LanPlusUI.outline1(g, cx, cy, cx + cellW, cy + cellH, LanPlusUI.LAVENDER);
            }
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int tabW = (contentW - 20) / 2;
            int ty = panelTop + 26;
            if (mouseY >= ty && mouseY < ty + 14) {
                if (mouseX >= boxX + 10 && mouseX < boxX + 10 + tabW) {
                    selectTab(0);
                    return true;
                }
                if (mouseX >= boxX + 10 + tabW && mouseX < boxX + 10 + 2 * tabW) {
                    selectTab(1);
                    return true;
                }
            }
        }
        if (tab == 0 && button == 0) {
            for (int[] c : swatchCells) {
                if (mouseX >= c[0] && mouseX < c[0] + SWATCH && mouseY >= c[1] && mouseY < c[1] + SWATCH) {
                    sink.solid(c[2]);
                    onClose();
                    return true;
                }
            }
            if (mouseX >= svX && mouseX < svX + SV_W && mouseY >= svY && mouseY < svY + SV_H) {
                svDrag = true;
                setSV(mouseX, mouseY);
                return true;
            }
            if (mouseX >= hueX && mouseX < hueX + HUE_W && mouseY >= svY && mouseY < svY + SV_H) {
                hueDrag = true;
                setHUE(mouseY);
                return true;
            }
        }
        if (tab == 1 && button == 0 && mouseY >= imgTop && mouseY < imgBottom) {
            for (ImgCell c : imgCells) {
                if (c.in(mouseX, mouseY)) {
                    if (c.none()) {
                        sink.none();
                    } else {
                        sink.image(c.image());
                    }
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (svDrag) {
            setSV(mouseX, mouseY);
            return true;
        }
        if (hueDrag) {
            setHUE(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        svDrag = false;
        hueDrag = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double delta) {
        if (tab == 1) {
            int rows = (imgCells.size() + IMG_COLS - 1) / IMG_COLS;
            int max = Math.max(0, rows * rowH - (imgBottom - imgTop));
            scrollY = Math.max(0, Math.min(max, scrollY - (int) (delta * 24)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, sx, delta);
    }

    private void setSV(double mx, double my) {
        sat = Math.clamp((float) (mx - svX) / (SV_W - 1), 0f, 1f);
        val = 1f - Math.clamp((float) (my - svY) / (SV_H - 1), 0f, 1f);
        syncColor();
    }

    private void setHUE(double my) {
        hue = Math.clamp((float) (my - svY) / (SV_H - 1), 0f, 1f) * 360f;
        syncColor();
    }

    private void commitSolid() {
        sink.solid(color);
        onClose();
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

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
