package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.Config;
import dev.bgame.lanplus.client.LanPlusClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.net.URI;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class SettingsScreen extends LanPlusScreen {

    private enum Cat {
        GENERAL("general"), THEME("theme"), ADVANCED("advanced");
        final String key;

        Cat(String key) {
            this.key = key;
        }
    }

    private static final int SIDEBAR_W = 118;
    private static final int CATEGORY_W = SIDEBAR_W * 4 / 5;
    private static final int TOGGLE_ROW_STEP = 48;
    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_ADDRESS_LENGTH = 260;
    private final Screen parent;
    private Cat selected = Cat.GENERAL;
    private EditBox backendBox;
    private EditBox relayAddrBox;
    private EditBox voiceHostBox;
    private String backendDraft;
    private String relayAddressDraft;
    private String voiceHostDraft;
    private Component status;
    private int px, py, pw, ph, headerBottom, sidebarX, contentX, contentW, contentTop;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("gui.lanplus.settings.title"));
        this.parent = parent;
        this.backendDraft = Config.backendUrl;
        this.relayAddressDraft = Config.relayDevAddress;
        this.voiceHostDraft = Config.voiceHost;
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
        addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.settings.back"), b -> onClose())
                .bounds(px + 8, py + 7, 54, 18).build());

        int categoryY = contentTop;
        for (Cat category : Cat.values()) {
            LanplusButton.Builder builder = LanplusButton.create(
                            Component.translatable("gui.lanplus.settings." + category.key),
                            button -> selectCat(category))
                    .bounds(sidebarX - 4, categoryY, CATEGORY_W, 18);
            if (category == selected) {
                builder.primary();
            }
            addRenderableWidget(builder.build());
            categoryY += 20;
        }

        backendBox = null;
        relayAddrBox = null;
        voiceHostBox = null;
        switch (selected) {
            case GENERAL -> addGeneralToggles();
            case THEME -> addThemeButtons();
            case ADVANCED -> addAdvancedWidgets();
        }
    }

    private EditBox advancedBox(String value, int maxLength, String labelKey, String hintKey,
                                Consumer<String> responder) {
        EditBox box = new EditBox(this.font, contentX, contentTop, contentW, 20,
                Component.translatable(labelKey));
        box.setMaxLength(maxLength);
        box.setValue(value);
        box.setHint(Component.translatable(hintKey));
        box.setResponder(responder);
        addRenderableWidget(box);
        return box;
    }

    private void flip(Runnable change) {
        change.run();
        status = Config.save() ? null : Component.translatable("gui.lanplus.settings.save_failed");
        rebuildWidgets();
    }

    private boolean commitBoxes() {
        String backend = backendDraft.trim();
        String relayAddress = relayAddressDraft.trim();
        String voiceHost = voiceHostDraft.trim();
        boolean backendChanged = !backend.equals(Config.backendUrl);
        boolean relayAddressChanged = !relayAddress.equals(Config.relayDevAddress);
        boolean voiceHostChanged = !voiceHost.equals(Config.voiceHost);
        if (backendChanged && !validBackendUrl(backend)) {
            status = Component.translatable("gui.lanplus.settings.invalid_backend");
            return false;
        }
        if ((relayAddressChanged && invalidAddress(relayAddress))
                || (voiceHostChanged && invalidAddress(voiceHost))) {
            status = Component.translatable("gui.lanplus.settings.invalid_address");
            return false;
        }

        backendDraft = backend;
        relayAddressDraft = relayAddress;
        voiceHostDraft = voiceHost;
        if (!backendChanged && !relayAddressChanged && !voiceHostChanged) {
            return true;
        }
        Config.backendUrl = backend;
        Config.relayDevAddress = relayAddress;
        Config.voiceHost = voiceHost;
        status = Config.save() ? null : Component.translatable("gui.lanplus.settings.save_failed");
        return true;
    }

    private void selectCat(Cat c) {
        if (c == selected) {
            return;
        }
        if (commitBoxes()) {
            selected = c;
            rebuildWidgets();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawBackdrop(g);

        LanPlusUI.panel(g, px, py, px + pw, py + ph);
        LanPlusUI.rivets(g, px, py, px + pw, py + ph, LanPlusUI.FAINT);

        int wx = LanPlusUI.wordmark(g, this.font, px + 70, py + 12);
        g.drawString(this.font, Component.translatable("gui.lanplus.settings.word"), wx + 6, py + 12, LanPlusUI.MUTED, false);
        g.fill(px + 6, headerBottom, px + pw - 6, headerBottom + 1, LanPlusUI.DIVIDER);
        g.fill(sidebarX + SIDEBAR_W, headerBottom + 6, sidebarX + SIDEBAR_W + 1, py + ph - 8, LanPlusUI.DIVIDER);

        switch (selected) {
            case GENERAL -> renderGeneral(g);
            case THEME -> renderTheme(g);
            case ADVANCED -> renderAdvanced(g);
        }

        if (status != null) {
            g.drawString(this.font, status, contentX, py + ph - 17, LanPlusUI.RED, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderGeneral(GuiGraphics g) {
        int y = contentTop + 2;
        y = renderToggleRow(g, y, "enabled");
        y = renderToggleRow(g, y, "discord");
        y = renderToggleRow(g, y, "relay");
        renderToggleRow(g, y, "voice");
    }

    private int renderToggleRow(GuiGraphics g, int y, String key) {
        int x = contentX;
        int w = contentW;
        int rh = 40;
        int textW = w - 34;

        g.drawString(this.font, Component.translatable("gui.lanplus.settings." + key), x, y + 5, LanPlusUI.TEXT, false);
        List<FormattedCharSequence> desc = this.font.split(
                Component.translatable("gui.lanplus.settings." + key + ".desc"), textW);
        int dy = y + 16;
        for (int i = 0; i < desc.size() && i < 2; i++) {
            g.drawString(this.font, desc.get(i), x, dy, LanPlusUI.MUTED, false);
            dy += 10;
        }
        g.fill(x, y + rh, x + w, y + rh + 1, LanPlusUI.DIVIDER);
        return y + TOGGLE_ROW_STEP;
    }

    private void addGeneralToggles() {
        int y = contentTop + 2;
        addToggle(y, "enabled", () -> Config.enabled,
                () -> LanPlusClient.setEnabled(!Config.enabled));
        y += TOGGLE_ROW_STEP;
        addToggle(y, "discord", () -> Config.discordEnabled, () -> {
            Config.discordEnabled = !Config.discordEnabled;
            LanPlusClient.setDiscordEnabled(Config.discordEnabled);
        });
        y += TOGGLE_ROW_STEP;
        addToggle(y, "relay", () -> Config.relayEnabled,
                () -> Config.relayEnabled = !Config.relayEnabled);
        y += TOGGLE_ROW_STEP;
        addToggle(y, "voice", () -> Config.voiceEnabled,
                () -> Config.voiceEnabled = !Config.voiceEnabled);
    }

    private void addToggle(int y, String key, BooleanSupplier enabled, Runnable change) {
        addRenderableWidget(new ToggleButton(contentX + contentW - 28, y + 8,
                Component.translatable("gui.lanplus.settings." + key), enabled, () -> flip(change)));
    }

    private void renderTheme(GuiGraphics g) {
        int y = contentTop + 4;
        for (FormattedCharSequence line : this.font.split(
                Component.translatable("gui.lanplus.settings.theme.desc"), contentW)) {
            g.drawString(this.font, line, contentX, y, LanPlusUI.MUTED, false);
            y += 10;
        }
    }

    private void addThemeButtons() {
        int y = contentTop + 30;
        for (Theme theme : Themes.ALL) {
            LanplusButton.Builder builder = LanplusButton.create(theme.name(), button -> selectTheme(theme))
                    .bounds(contentX, y, contentW, 20);
            if (theme.id().equals(Config.theme)) {
                builder.primary();
            }
            addRenderableWidget(builder.build());
            y += 26;
        }
    }

    private void selectTheme(Theme theme) {
        LanPlusUI.apply(theme);
        status = Config.setTheme(theme.id())
                ? null : Component.translatable("gui.lanplus.settings.save_failed");
        rebuildWidgets();
    }

    private void renderAdvanced(GuiGraphics g) {
        int x = contentX;
        int y = renderToggleRow(g, contentTop + 2, "relay_plain");
        y = fieldRow(g, x, y, "backend", backendBox);
        y = fieldRow(g, x, y, "relay_address", relayAddrBox);
        fieldRow(g, x, y, "voice_host", voiceHostBox);
    }

    private void addAdvancedWidgets() {
        addToggle(contentTop + 2, "relay_plain", () -> Config.relayDevPlaintext,
                () -> Config.relayDevPlaintext = !Config.relayDevPlaintext);
        backendBox = advancedBox(backendDraft, MAX_URL_LENGTH,
                "gui.lanplus.settings.backend", "gui.lanplus.settings.backend", value -> {
                    backendDraft = value;
                    status = null;
                });
        relayAddrBox = advancedBox(relayAddressDraft, MAX_ADDRESS_LENGTH,
                "gui.lanplus.settings.relay_address", "gui.lanplus.settings.relay_address.hint",
                value -> {
                    relayAddressDraft = value;
                    status = null;
                });
        voiceHostBox = advancedBox(voiceHostDraft, MAX_ADDRESS_LENGTH,
                "gui.lanplus.settings.voice_host", "gui.lanplus.settings.voice_host.hint",
                value -> {
                    voiceHostDraft = value;
                    status = null;
                });
    }

    private int fieldRow(GuiGraphics g, int x, int y, String key, EditBox box) {
        g.drawString(this.font, Component.translatable("gui.lanplus.settings." + key), x, y, LanPlusUI.TEXT, false);
        y += 11;
        List<FormattedCharSequence> desc = this.font.split(
                Component.translatable("gui.lanplus.settings." + key + ".desc"), contentW);
        for (int i = 0; i < desc.size() && i < 2; i++) {
            g.drawString(this.font, desc.get(i), x, y, LanPlusUI.MUTED, false);
            y += 10;
        }
        y += 2;
        if (box != null) {
            box.setPosition(x, y);
            box.setWidth(contentW);
        }
        return y + 28;
    }

    private static boolean validBackendUrl(String value) {
        if (value.isEmpty()) {
            return true;
        }
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean invalidAddress(String value) {
        if (value.isEmpty()) {
            return false;
        }
        int separator = value.lastIndexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            return true;
        }
        try {
            int port = Integer.parseInt(value.substring(separator + 1));
            return value.substring(0, separator).isBlank() || port <= 0 || port > 65535;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    @Override
    public void onClose() {
        if (commitBoxes()) {
            if (status != null) {
                LanPlusNotifications.info(Component.translatable("gui.lanplus.settings.title"), status);
            }
            Minecraft.getInstance().setScreen(parent);
        }
    }

    private static final class ToggleButton extends Button {
        private static final int WIDTH = 28;
        private static final int HEIGHT = 14;

        private final BooleanSupplier enabled;

        private ToggleButton(int x, int y, Component label, BooleanSupplier enabled, Runnable change) {
            super(x, y, WIDTH, HEIGHT, CommonComponents.optionStatus(enabled.getAsBoolean()),
                    button -> change.run(),
                    ignored -> CommonComponents.optionStatus(label, enabled.getAsBoolean()));
            this.enabled = enabled;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean on = enabled.getAsBoolean();
            int x = getX();
            int y = getY();
            g.fill(x, y, x + WIDTH, y + HEIGHT, on ? LanPlusUI.LIME : LanPlusUI.SLOT);
            LanPlusUI.outline1(g, x, y, x + WIDTH, y + HEIGHT,
                    isHoveredOrFocused() ? LanPlusUI.ACCENT_HOVER : LanPlusUI.EDGE_DARK);
            int knobX = on ? x + WIDTH - 12 : x + 2;
            g.fill(knobX, y + 2, knobX + 10, y + HEIGHT - 2, on ? LanPlusUI.SURFACE : LanPlusUI.MUTED);
        }
    }
}
