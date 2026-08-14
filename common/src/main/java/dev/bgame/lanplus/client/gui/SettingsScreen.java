package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class SettingsScreen extends Screen {

    private enum Cat {
        GENERAL("general"), APPEARANCE("appearance"), ADVANCED("advanced");
        final String key;

        Cat(String key) {
            this.key = key;
        }
    }

    private static final int SIDEBAR_W = 118;
    private static final int ROW_GAP = 6;
    private static final int CARD_H = 38;
    private static final int SWATCH = 22;
    private static final int SWATCH_GAP = 6;
    private final Screen parent;
    private Cat selected = Cat.GENERAL;
    private EditBox backendBox;
    private int px, py, pw, ph, headerBottom, sidebarX, contentX, contentW, contentTop;
    private final List<Row> rows = new ArrayList<>();

    private record Row(int x, int y, int w, int h, Runnable action) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public SettingsScreen(Screen parent) {
        super(Component.translatable("gui.lanplus.settings.title"));
        this.parent = parent;
    }

    private void layout() {
        pw = Math.min(this.width - 60, 560);
        ph = Math.min(this.height - 60, 320);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        headerBottom = py + 30;
        sidebarX = px + 10;
        contentX = sidebarX + SIDEBAR_W + 12;
        contentW = px + pw - 12 - contentX;
        contentTop = headerBottom + 10;
    }

    @Override
    protected void init() {
        layout();
        addRenderableWidget(LanPlusButton.create(Component.translatable("gui.lanplus.settings.back"), b -> onClose())
                .bounds(px + 8, py + 6, 54, 18).build());

        if (selected == Cat.ADVANCED) {
            backendBox = new EditBox(this.font, contentX + 8, contentTop + 24, contentW - 16, 20,
                    Component.translatable("gui.lanplus.settings.backend"));
            backendBox.setMaxLength(200);
            backendBox.setValue(Config.backendUrl);
            backendBox.setHint(Component.translatable("gui.lanplus.settings.backend"));
            addRenderableWidget(backendBox);
        } else {
            backendBox = null;
        }
    }

    private void flip(Runnable change) {
        change.run();
        Config.save();
    }

    private void commitBackend() {
        if (backendBox != null) {
            String v = backendBox.getValue().trim();
            if (!v.equals(Config.backendUrl)) {
                Config.backendUrl = v;
                Config.save();
            }
        }
    }

    private void selectCat(Cat c) {
        if (c != selected) {
            commitBackend();
            selected = c;
            rebuildWidgets();
        }
    }

    private void selectTheme(Theme t) {
        LanPlusUI.apply(t);
        Config.setTheme(t.id());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        LanPlusUI.backdrop(g, this.width, this.height);
        layout();
        rows.clear();

        LanPlusUI.panel(g, px, py, px + pw, py + ph);
        g.drawString(this.font, this.title, px + 70, py + 11, LanPlusUI.TEXT);
        g.fill(px + 6, headerBottom, px + pw - 6, headerBottom + 1, LanPlusUI.DIVIDER);
        g.fill(sidebarX + SIDEBAR_W, headerBottom + 6, sidebarX + SIDEBAR_W + 1, py + ph - 8, LanPlusUI.DIVIDER);

        renderSidebar(g, mouseX, mouseY);

        switch (selected) {
            case GENERAL -> renderGeneral(g);
            case APPEARANCE -> renderAppearance(g);
            case ADVANCED -> renderAdvanced(g);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        int y = contentTop;
        for (Cat c : Cat.values()) {
            int h = 18;
            boolean sel = c == selected;
            boolean hover = mouseX >= sidebarX && mouseX < sidebarX + SIDEBAR_W && mouseY >= y && mouseY < y + h;
            if (sel) {
                g.fill(sidebarX, y, sidebarX + SIDEBAR_W, y + h, LanPlusUI.ACCENT_TINT);
                g.fill(sidebarX, y, sidebarX + 2, y + h, LanPlusUI.ACCENT);
            } else if (hover) {
                g.fill(sidebarX, y, sidebarX + SIDEBAR_W, y + h, LanPlusUI.SURFACE_HOVER);
            }
            g.drawString(this.font, Component.translatable("gui.lanplus.settings." + c.key),
                    sidebarX + 8, y + 5, sel ? LanPlusUI.TEXT : LanPlusUI.MUTED, false);
            Cat pick = c;
            rows.add(new Row(sidebarX, y, SIDEBAR_W, h, () -> selectCat(pick)));
            y += h + 2;
        }
    }

    private void renderGeneral(GuiGraphics g) {
        int y = contentTop;
        y = toggleCard(g, y, "enabled", Config.enabled, () -> flip(() -> Config.enabled = !Config.enabled));
        y = toggleCard(g, y, "discord", Config.discordEnabled, () -> flip(() -> Config.discordEnabled = !Config.discordEnabled));
        toggleCard(g, y, "relay", Config.relayEnabled, () -> flip(() -> Config.relayEnabled = !Config.relayEnabled));
    }

    private int toggleCard(GuiGraphics g, int y, String key, boolean on, Runnable act) {
        int x = contentX;
        int w = contentW;
        g.fill(x, y, x + w, y + CARD_H, LanPlusUI.SURFACE_RAISED);
        LanPlusUI.bevelRaised(g, x, y, x + w, y + CARD_H);
        g.drawString(this.font, Component.translatable("gui.lanplus.settings." + key), x + 8, y + 8, LanPlusUI.TEXT, false);
        g.drawString(this.font, Component.translatable("gui.lanplus.settings." + key + ".desc"), x + 8, y + 20, LanPlusUI.MUTED, false);
        toggle(g, x + w - 8 - 26, y + (CARD_H - 12) / 2, on);
        rows.add(new Row(x, y, w, CARD_H, act));
        return y + CARD_H + ROW_GAP;
    }

    private void toggle(GuiGraphics g, int x, int y, boolean on) {
        int w = 26;
        int h = 12;
        g.fill(x, y, x + w, y + h, on ? LanPlusUI.ACCENT : LanPlusUI.SLOT);
        LanPlusUI.bevelInset(g, x, y, x + w, y + h);
        int kx = on ? x + w - 2 - 10 : x + 2;
        g.fill(kx, y + 1, kx + 10, y + h - 1, on ? LanPlusUI.TEXT : LanPlusUI.MUTED);
    }

    private void renderAppearance(GuiGraphics g) {
        int x = contentX;
        int w = contentW;
        int y = contentTop;
        int h = 56;
        g.fill(x, y, x + w, y + h, LanPlusUI.SURFACE_RAISED);
        LanPlusUI.bevelRaised(g, x, y, x + w, y + h);
        g.drawString(this.font, Component.translatable("gui.lanplus.settings.theme"), x + 8, y + 8, LanPlusUI.TEXT, false);
        g.drawString(this.font, Component.translatable("gui.lanplus.settings.theme.desc"), x + 8, y + 20, LanPlusUI.MUTED, false);

        int sx = x + 8;
        int sy = y + 32;
        for (Theme t : Themes.ALL) {
            boolean cur = LanPlusUI.current().id().equals(t.id());
            g.fill(sx, sy, sx + SWATCH, sy + SWATCH, t.surface() | 0xFF000000);
            g.fill(sx + 4, sy + 4, sx + SWATCH - 4, sy + SWATCH - 4, t.accent());
            if (cur) {
                g.fill(sx - 2, sy - 2, sx + SWATCH + 2, sy, LanPlusUI.ACCENT);
                g.fill(sx - 2, sy + SWATCH, sx + SWATCH + 2, sy + SWATCH + 2, LanPlusUI.ACCENT);
                g.fill(sx - 2, sy - 2, sx, sy + SWATCH + 2, LanPlusUI.ACCENT);
                g.fill(sx + SWATCH, sy - 2, sx + SWATCH + 2, sy + SWATCH + 2, LanPlusUI.ACCENT);
            } else {
                LanPlusUI.bevelInset(g, sx, sy, sx + SWATCH, sy + SWATCH);
            }
            Theme pick = t;
            rows.add(new Row(sx, sy, SWATCH, SWATCH, () -> selectTheme(pick)));
            sx += SWATCH + SWATCH_GAP;
        }
        g.drawString(this.font, LanPlusUI.current().name(), sx + 4, sy + (SWATCH - 8) / 2, LanPlusUI.MUTED, false);
    }

    private void renderAdvanced(GuiGraphics g) {
        int x = contentX;
        int w = contentW;
        int y = contentTop;
        int h = 54;
        g.fill(x, y, x + w, y + h, LanPlusUI.SURFACE_RAISED);
        LanPlusUI.bevelRaised(g, x, y, x + w, y + h);
        g.drawString(this.font, Component.translatable("gui.lanplus.settings.backend"), x + 8, y + 6, LanPlusUI.TEXT, false);
        g.drawString(this.font, Component.translatable("gui.lanplus.settings.backend.desc"), x + 8, y + 40, LanPlusUI.FAINT, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Row r : rows) {
                if (r.in(mouseX, mouseY)) {
                    r.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        commitBackend();
        this.minecraft.setScreen(parent);
    }
}
