package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.Config;
import dev.bgame.lanplus.client.LanPlusClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class SettingsScreen extends Screen {

    private enum Cat {
        GENERAL("general"), THEME("Theme"), ADVANCED("advanced");
        final String key;

        Cat(String key) {
            this.key = key;
        }
    }

    private static final int SIDEBAR_W = 118;
    private static final int ROW_GAP = 6;
    private static final int CARD_H = 38;
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
                .bounds(px + 8, py + 7, 54, 18).build());

        if (selected == Cat.ADVANCED) {
            backendBox = new EditBox(this.font, contentX, contentTop + 32, contentW, 20,
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

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        LanPlusUI.backdrop(g, this.width, this.height);
        layout();
        rows.clear();

        g.fill(px, py, px + pw, py + ph, LanPlusUI.SURFACE);
        LanPlusUI.bevelRaised(g, px, py, px + pw, py + ph);
        g.fill(px, py, px + pw, py + 2, LanPlusUI.LIME);

        int wx = LanPlusUI.wordmark(g, this.font, px + 70, py + 12);
        g.drawString(this.font, Component.translatable("gui.lanplus.settings.word"), wx + 6, py + 12, LanPlusUI.MUTED, false);
        g.fill(px + 6, headerBottom, px + pw - 6, headerBottom + 1, LanPlusUI.DIVIDER);
        g.fill(sidebarX + SIDEBAR_W, headerBottom + 6, sidebarX + SIDEBAR_W + 1, py + ph - 8, LanPlusUI.DIVIDER);

        renderSidebar(g, mouseX, mouseY);

        switch (selected) {
            case GENERAL -> renderGeneral(g);
            case THEME -> renderTheme(g);
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
                g.drawString(this.font, "+", sidebarX + 4, y + 5, LanPlusUI.LIME, false);
            }
            g.drawString(this.font, Component.translatable("gui.lanplus.settings." + c.key),
                    sidebarX + 14, y + 5, sel || hover ? LanPlusUI.TEXT : LanPlusUI.MUTED, false);
            Cat pick = c;
            rows.add(new Row(sidebarX, y, SIDEBAR_W, h, () -> selectCat(pick)));
            y += h + 2;
        }
    }

    private void renderGeneral(GuiGraphics g) {
        int y = contentTop + 2;
        y = toggleRow(g, y, "enabled", Config.enabled, () -> flip(() -> Config.enabled = !Config.enabled));
        y = toggleRow(g, y, "discord", Config.discordEnabled, () -> flip(() -> {
            Config.discordEnabled = !Config.discordEnabled;
            LanPlusClient.setDiscordEnabled(Config.discordEnabled);
        }));
        toggleRow(g, y, "relay", Config.relayEnabled, () -> flip(() -> Config.relayEnabled = !Config.relayEnabled));
    }

    private int toggleRow(GuiGraphics g, int y, String key, boolean on, Runnable act) {
        int x = contentX;
        int w = contentW;
        int rh = 40;

        g.drawString(this.font, Component.translatable("gui.lanplus.settings." + key), x, y + 5, LanPlusUI.TEXT, false);
        g.drawString(this.font, Component.translatable("gui.lanplus.settings." + key + ".desc"), x, y + 16, LanPlusUI.MUTED, false);
        toggle(g, x + w - 28, y + 8, on);
        g.fill(x, y + rh, x + w, y + rh + 1, LanPlusUI.DIVIDER);
        rows.add(new Row(x, y, w, rh, act));
        return y + rh + 8;
    }

    private void toggle(GuiGraphics g, int x, int y, boolean on) {
        int w = 28;
        int h = 14;
        g.fill(x, y, x + w, y + h, on ? LanPlusUI.LIME : LanPlusUI.SLOT);
        LanPlusUI.bevelInset(g, x, y, x + w, y + h);
        int kx = on ? x + w - 2 - 10 : x + 2;
        g.fill(kx, y + 2, kx + 10, y + h - 2, on ? LanPlusUI.SURFACE : LanPlusUI.MUTED);
    }

    private void renderTheme(GuiGraphics g) {
        int cx = contentX + contentW / 2;
        int cy = (contentTop + py + ph - 8) / 2;
        g.drawCenteredString(this.font, Component.translatable("gui.lanplus.settings.theme.soon"), cx, cy + 2, LanPlusUI.FAINT);
    }

    private void renderAdvanced(GuiGraphics g) {
        int x = contentX;
        int y = contentTop + 2;
        g.drawString(this.font, Component.translatable("gui.lanplus.settings.backend"), x, y + 6, LanPlusUI.TEXT, false);
        g.drawString(this.font, Component.translatable("gui.lanplus.settings.backend.desc"), x, y + 18, LanPlusUI.MUTED, false);
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
