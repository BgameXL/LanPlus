package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.api.CatalogImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
    private static final int IMG_COLS = 3;
    private static final int IMG_GAP = 8;

    private final Screen parent;
    private final int[] palette;
    private final List<CatalogImage> images;
    private final String currentImageId;
    private final Sink sink;
    private final int initialColor;

    private int tab;
    private int scrollY;
    private ColorPicker picker;

    private int boxX, contentW, panelTop, panelBottom, bodyTop;
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
        this.initialColor = color & 0xFFFFFF;
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
        int customTop = bodyTop + presetRows * (SWATCH + SWATCH_GAP) + 12;

        imgTop = bodyTop;
        imgBottom = panelBottom - 6;
        cellW = (contentW - 2 * PAD - (IMG_COLS - 1) * IMG_GAP) / IMG_COLS;
        cellH = Math.round(cellW * 9f / 16f);
        rowH = cellH + 12 + IMG_GAP;

        addRenderableWidget(LanplusButton.create(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(boxX + contentW - 90, panelBottom + 8, 90, 20).build());

        if (tab == 0) {
            picker = new ColorPicker(this.font, initialColor);
            picker.layout(boxX + PAD, customTop);
            addRenderableWidget(picker.hexBox());
            var hex = picker.hexBox();
            addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.profile.bg.use"),
                            b -> commitSolid())
                    .bounds(hex.getX(), hex.getY() + 22, hex.getWidth(), 18).primary().build());
        } else {
            picker = null;
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
        int active = picker.color();
        for (int i = 0; i < palette.length; i++) {
            int cx = x0 + (i % cols) * (SWATCH + SWATCH_GAP);
            int cy = bodyTop + (i / cols) * (SWATCH + SWATCH_GAP);
            int c = palette[i] & 0xFFFFFF;
            swatchCells.add(new int[]{cx, cy, c});
            g.fill(cx, cy, cx + SWATCH, cy + SWATCH, 0xFF000000 | c);
            if (c == active) {
                LanPlusUI.outline1(g, cx - 2, cy - 2, cx + SWATCH + 2, cy + SWATCH + 2, LanPlusUI.ACCENT);
            } else {
                LanPlusUI.outline1(g, cx, cy, cx + SWATCH, cy + SWATCH, LanPlusUI.EDGE_DARK);
            }
            if (mouseX >= cx && mouseX < cx + SWATCH && mouseY >= cy && mouseY < cy + SWATCH && c != active) {
                g.fill(cx, cy, cx + SWATCH, cy + SWATCH, 0x22FFFFFF);
            }
        }
        picker.render(g);
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
            if (picker.mouseClicked(mouseX, mouseY, button)) {
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
        if (picker != null && picker.mouseDragged(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (picker != null) {
            picker.mouseReleased();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double delta) {
        if (tab == 1) {
            int rows = (imgCells.size() + IMG_COLS - 1) / IMG_COLS;
            int max = Math.max(0, rows * rowH - (imgBottom - imgTop));
            scrollY = Math.clamp(scrollY - (int) (delta * 24), 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, sx, delta);
    }

    private void commitSolid() {
        sink.solid(picker.color());
        onClose();
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
