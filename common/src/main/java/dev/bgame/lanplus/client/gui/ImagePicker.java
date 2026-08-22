package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.api.CatalogImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ImagePicker extends Screen {

    private static final int MARGIN = 20;
    private static final int MAX_W = 460;
    private static final int GRID_PAD = 10;
    private static final int CELL_GAP = 8;
    private static final int LABEL_H = 11;
    private final Screen parent;
    private final Component heading;
    private final List<CatalogImage> items;
    private final String currentId;
    private final boolean allowNone;
    private final Consumer<CatalogImage> onPick;
    private final int columns;
    private final float aspect;
    private int boxX;
    private int contentW;
    private int gridTop;
    private int gridBottom;
    private int cellW;
    private int cellH;
    private int rowH;
    private int scrollY;
    private final List<Cell> cells = new ArrayList<>();

    private record Cell(int x, int y, int w, int h, CatalogImage image, boolean none) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public ImagePicker(Screen parent, Component heading, List<CatalogImage> items, String currentId,
                       boolean allowNone, int columns, float aspect, Consumer<CatalogImage> onPick) {
        super(heading);
        this.parent = parent;
        this.heading = heading;
        this.items = items == null ? List.of() : items;
        this.currentId = currentId;
        this.allowNone = allowNone;
        this.onPick = onPick;
        this.columns = Math.max(1, columns);
        this.aspect = aspect <= 0 ? 1f : aspect;
    }

    @Override
    protected void init() {
        contentW = Math.min(this.width - 2 * MARGIN, MAX_W);
        boxX = (this.width - contentW) / 2;
        int panelTop = 36;
        int panelBottom = this.height - 40;
        gridTop = panelTop + 26;
        gridBottom = panelBottom - 6;
        cellW = (contentW - 2 * GRID_PAD - (columns - 1) * CELL_GAP) / columns;
        cellH = Math.round(cellW / aspect);
        rowH = cellH + LABEL_H + CELL_GAP;

        addRenderableWidget(LanplusButton.create(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(boxX + contentW - 90, panelBottom + 8, 90, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        LanPlusUI.backdrop(g, this.width, this.height);
        int panelTop = 36;
        int panelBottom = this.height - 40;
        LanPlusUI.panel(g, boxX, panelTop, boxX + contentW, panelBottom);

        int wx = LanPlusUI.wordmark(g, this.font, boxX + 10, panelTop + 8);
        g.drawString(this.font, heading, wx + 6, panelTop + 8, LanPlusUI.MUTED, false);

        cells.clear();
        if (items.isEmpty() && !allowNone) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.profile.picker.empty"),
                    boxX + contentW / 2, (gridTop + gridBottom) / 2, LanPlusUI.FAINT);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        List<CatalogImage> list = new ArrayList<>();
        if (allowNone) {
            list.add(null);
        }
        list.addAll(items);

        g.enableScissor(boxX, gridTop, boxX + contentW, gridBottom);
        int colX0 = boxX + GRID_PAD;
        for (int i = 0; i < list.size(); i++) {
            int col = i % columns;
            int row = i / columns;
            int cx = colX0 + col * (cellW + CELL_GAP);
            int cy = gridTop + row * rowH - scrollY;
            CatalogImage img = list.get(i);
            boolean none = img == null;
            cells.add(new Cell(cx, cy, cellW, cellH, img, none));
            if (cy + cellH < gridTop || cy > gridBottom) {
                continue;
            }
            renderCell(g, cx, cy, img, none, mouseX, mouseY);
        }
        g.disableScissor();

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderCell(GuiGraphics g, int cx, int cy, CatalogImage img, boolean none, int mouseX, int mouseY) {
        boolean selected = none ? (currentId == null) : img.id().equals(currentId);
        boolean hover = mouseX >= cx && mouseX < cx + cellW && mouseY >= cy && mouseY < cy + cellH;
        g.fill(cx, cy, cx + cellW, cy + cellH, LanPlusUI.SLOT);
        if (none) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.profile.picker.none"),
                    cx + cellW / 2, cy + cellH / 2 - 4, LanPlusUI.MUTED);
        } else {
            ProfileImages.Tex tex = ProfileImages.get(img);
            if (tex != null) {
                ProfileImages.blitCover(g, tex, cx, cy, cellW, cellH);
            } else {
                g.drawCenteredString(this.font, Component.literal("..."),
                        cx + cellW / 2, cy + cellH / 2 - 4, LanPlusUI.FAINT);
            }
        }
        if (selected) {
            int a = LanPlusUI.ACCENT;
            g.fill(cx, cy, cx + cellW, cy + 2, a);
            g.fill(cx, cy + cellH - 2, cx + cellW, cy + cellH, a);
            g.fill(cx, cy, cx + 2, cy + cellH, a);
            g.fill(cx + cellW - 2, cy, cx + cellW, cy + cellH, a);
        } else if (hover) {
            LanPlusUI.border(g, cx - 1, cy - 1, cx + cellW + 1, cy + cellH + 1);
            g.fill(cx, cy, cx + cellW, cy + 2, LanPlusUI.ACCENT_TINT);
        }
        String label = none ? "" : img.id();
        if (!label.isEmpty()) {
            g.drawString(this.font, ellipsize(label, cellW),
                    cx, cy + cellH + 2, selected ? LanPlusUI.TEXT : LanPlusUI.MUTED, false);
        }
    }

    private String ellipsize(String s, int maxW) {
        if (this.font.width(s) <= maxW) {
            return s;
        }
        while (s.length() > 1 && this.font.width(s + "…") > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= gridTop && mouseY < gridBottom) {
            for (Cell c : cells) {
                if (c.in(mouseX, mouseY)) {
                    pick(c.image());
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int rows = (cells.size() + columns - 1) / columns;
        int total = rows * rowH;
        int viewport = gridBottom - gridTop;
        int maxScroll = Math.max(0, total - viewport);
        scrollY = Math.max(0, Math.min(maxScroll, scrollY - (int) (delta * 24)));
        return true;
    }

    private void pick(CatalogImage image) {
        if (onPick != null) {
            onPick.accept(image);
        }
        this.minecraft.setScreen(parent);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
