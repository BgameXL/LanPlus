package dev.bgame.lanplus.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public final class BackgroundPicker extends LanPlusScreen {

    private static final int MARGIN = 20;
    private static final int MAX_W = 460;
    private static final int PAD = 12;
    private static final int SWATCH = 34;
    private static final int SWATCH_GAP = 8;

    private final Screen parent;
    private final int[] palette;
    private final int current;
    private final IntConsumer onPick;

    private int boxX;
    private int contentW;
    private int panelTop;
    private int panelBottom;
    private int swatchTop;
    private final List<int[]> cells = new ArrayList<>();

    BackgroundPicker(Screen parent, int current, int[] palette, IntConsumer onPick) {
        super(Component.translatable("gui.lanplus.profile.bg.pick.color.title"));
        this.parent = parent;
        this.current = current & 0xFFFFFF;
        this.palette = palette;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        contentW = Math.min(this.width - 2 * MARGIN, MAX_W);
        boxX = (this.width - contentW) / 2;
        panelTop = 36;
        panelBottom = this.height - 40;
        swatchTop = panelTop + 30;

        addRenderableWidget(LanplusButton.create(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(boxX + contentW - 90, panelBottom + 8, 90, 20).primary().build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawBackdrop(g);
        LanPlusUI.panel(g, boxX, panelTop, boxX + contentW, panelBottom);

        int wx = LanPlusUI.wordmark(g, this.font, boxX + 10, panelTop + 8);
        g.drawString(this.font, Component.translatable("gui.lanplus.profile.bg.color"),
                wx + 6, panelTop + 8, LanPlusUI.MUTED, false);

        cells.clear();
        int cols = Math.max(1, (contentW - 2 * PAD + SWATCH_GAP) / (SWATCH + SWATCH_GAP));
        int x0 = boxX + PAD;
        for (int i = 0; i < palette.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = x0 + col * (SWATCH + SWATCH_GAP);
            int cy = swatchTop + row * (SWATCH + SWATCH_GAP);
            int swatchColor = palette[i] & 0xFFFFFF;
            cells.add(new int[]{cx, cy, swatchColor});
            renderSwat(g, cx, cy, swatchColor, mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderSwat(GuiGraphics g, int cx, int cy, int swatchColor, int mouseX, int mouseY) {
        boolean selected = swatchColor == current;
        boolean hover = mouseX >= cx && mouseX < cx + SWATCH && mouseY >= cy && mouseY < cy + SWATCH;
        g.fill(cx, cy, cx + SWATCH, cy + SWATCH, 0xFF000000 | swatchColor);
        if (selected) {
            g.fill(cx - 2, cy - 2, cx + SWATCH + 2, cy - 1, LanPlusUI.ACCENT);
            g.fill(cx - 2, cy + SWATCH + 1, cx + SWATCH + 2, cy + SWATCH + 2, LanPlusUI.ACCENT);
            g.fill(cx - 2, cy - 2, cx - 1, cy + SWATCH + 2, LanPlusUI.ACCENT);
            g.fill(cx + SWATCH + 1, cy - 2, cx + SWATCH + 2, cy + SWATCH + 2, LanPlusUI.ACCENT);
        } else {
            LanPlusUI.border(g, cx, cy, cx + SWATCH, cy + SWATCH);
        }
        if (hover && !selected) {
            g.fill(cx, cy, cx + SWATCH, cy + SWATCH, 0x22FFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int[] c : cells) {
                if (mouseX >= c[0] && mouseX < c[0] + SWATCH && mouseY >= c[1] && mouseY < c[1] + SWATCH) {
                    onPick.accept(c[2]);
                    Minecraft.getInstance().setScreen(parent);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
