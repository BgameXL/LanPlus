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

    private enum ThemeTarget {
        ACCENT("gui.lanplus.theme.accent"),
        BACKGROUND("gui.lanplus.theme.background"),
        TEXT("gui.lanplus.theme.text");
        final String key;

        ThemeTarget(String key) {
            this.key = key;
        }
    }

    private static final int CATEGORY_W = 94;
    private static final int TOGGLE_ROW_STEP = 48;
    private static final int MAX_URL_LENGTH = 2048;
    private static final int MAX_ADDRESS_LENGTH = 260;
    private static final int CARD_GAP = 8;
    private static final int CARD_H = 66;
    private final Screen parent;
    private Cat selected = Cat.GENERAL;
    private EditBox backendBox;
    private EditBox relayAddrBox;
    private EditBox voiceHostBox;
    private String backendDraft;
    private String relayAddressDraft;
    private String voiceHostDraft;
    private Component status;
    private ColorPicker themePicker;
    private ThemeTarget themeTarget = ThemeTarget.ACCENT;
    private int px, py, pw, ph, headerBottom, sidebarX, dividerX, contentX, contentW, contentTop;
    private int cardW, cardsTop, editorTop;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("gui.lanplus.settings.title"));
        this.parent = parent;
        this.backendDraft = Config.backendUrl;
        this.relayAddressDraft = Config.relayDevAddress;
        this.voiceHostDraft = Config.voiceHost;
    }

    private void layout() {
        pw = Math.min(this.width - 60, 700);
        ph = Math.min(this.height - 60, 360);
        px = (this.width - pw) / 2;
        py = (this.height - ph) / 2;
        headerBottom = py + 30;
        sidebarX = px + 14;
        dividerX = sidebarX + CATEGORY_W + 12;
        contentX = dividerX + 12;
        contentW = px + pw - 12 - contentX;
        contentTop = headerBottom + 10;
        cardW = (contentW - 3 * CARD_GAP) / 4;
        cardsTop = contentTop + 16;
        editorTop = cardsTop + CARD_H + 10;
    }

    @Override
    protected void init() {
        layout();
        addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.settings.back"), b -> onClose())
                .bounds(px + 8, py + 7, 54, 18).build());

        int categoryY = contentTop;
        for (Cat category : Cat.values()) {
            addRenderableWidget(new CategoryButton(sidebarX, categoryY, category));
            categoryY += 20;
        }

        backendBox = null;
        relayAddrBox = null;
        voiceHostBox = null;
        switch (selected) {
            case GENERAL -> addGeneralToggles();
            case THEME -> addThemeWidgets();
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
            if (themePicker != null) {
                persistTheme();
            }
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
        g.fill(dividerX, headerBottom + 6, dividerX + 1, py + ph - 8, LanPlusUI.DIVIDER);

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
        Theme customPreview = Themes.custom(liveSeed(ThemeTarget.ACCENT),
                liveSeed(ThemeTarget.BACKGROUND), liveSeed(ThemeTarget.TEXT));
        if ("custom".equals(Config.theme)) {
            LanPlusUI.apply(customPreview);
        }

        g.drawString(this.font, Component.translatable("gui.lanplus.settings.theme.desc"),
                contentX, contentTop + 2, LanPlusUI.MUTED, false);

        for (int i = 0; i < 4; i++) {
            Theme card = i < Themes.ALL.size() ? Themes.ALL.get(i) : customPreview;
            renderCard(g, i, card);
        }

        if (themePicker != null) {
            renderCustomEditor(g);
        }
    }

    private void renderCard(GuiGraphics g, int index, Theme theme) {
        String id = index < Themes.ALL.size() ? theme.id() : "custom";
        boolean selected = id.equals(Config.theme);
        int x = cardX(index);
        int y = cardsTop;
        LanPlusUI.button3d(g, x, y, x + cardW, y + CARD_H, LanPlusUI.SURFACE_RAISED);
        if (selected) {
            LanPlusUI.outline1(g, x, y, x + cardW, y + CARD_H, LanPlusUI.ACCENT);
        }

        Component name = index < Themes.ALL.size()
                ? theme.name() : Component.translatable("gui.lanplus.theme.custom");
        g.drawString(this.font, name, x + 8, y + 8, selected ? LanPlusUI.ACCENT : LanPlusUI.TEXT, false);
        g.drawString(this.font, "+", x + cardW - 8 - this.font.width("+"), y + 8,
                selected ? LanPlusUI.LIME : LanPlusUI.MUTED, false);

        int[] ramp = {theme.accentStrong(), theme.accent(), theme.accentHover(), theme.link()};
        int sw = (cardW - 16) / ramp.length;
        for (int i = 0; i < ramp.length; i++) {
            g.fill(x + 8 + i * sw, y + 22, x + 8 + (i + 1) * sw, y + 34, 0xFF000000 | (ramp[i] & 0xFFFFFF));
        }
        LanPlusUI.outline1(g, x + 8, y + 22, x + 8 + ramp.length * sw, y + 34, LanPlusUI.EDGE_DARK);

        int dy = y + 38;
        List<FormattedCharSequence> desc = this.font.split(
                Component.translatable("gui.lanplus.theme." + id + ".desc"), cardW - 16);
        for (int i = 0; i < desc.size() && i < 2; i++) {
            g.drawString(this.font, desc.get(i), x + 8, dy, LanPlusUI.MUTED, false);
            dy += 10;
        }
    }

    private void renderCustomEditor(GuiGraphics g) {
        int y = editorTop;
        g.drawString(this.font, Component.translatable("gui.lanplus.theme.custom"), contentX, y, LanPlusUI.ACCENT, false);
        g.drawString(this.font, Component.translatable("gui.lanplus.theme.tune"), contentX, y + 11, LanPlusUI.MUTED, false);
        g.fill(contentX, y + 24, contentX + contentW, y + 25, LanPlusUI.DIVIDER);

        int ty = y + 32;
        int tw = (contentW - 2 * 6) / 3;
        for (ThemeTarget t : ThemeTarget.values()) {
            int tx = contentX + t.ordinal() * (tw + 6);
            boolean sel = t == themeTarget;
            LanPlusUI.button3d(g, tx, ty, tx + tw, ty + 20, sel ? LanPlusUI.SURFACE_HOVER : LanPlusUI.SURFACE_RAISED);
            g.fill(tx + 5, ty + 6, tx + 15, ty + 15, 0xFF000000 | (liveSeed(t) & 0xFFFFFF));
            LanPlusUI.outline1(g, tx + 5, ty + 6, tx + 15, ty + 15, LanPlusUI.EDGE_DARK);
            if (sel) {
                LanPlusUI.outline1(g, tx, ty, tx + tw, ty + 20, LanPlusUI.ACCENT);
            }
            g.drawString(this.font, Component.translatable(t.key), tx + 19, ty + 6,
                    sel ? LanPlusUI.TEXT : LanPlusUI.MUTED, false);
        }
        themePicker.render(g);
    }

    private void addThemeWidgets() {
        if (!"custom".equals(Config.theme)) {
            themePicker = null;
            return;
        }
        themeTarget = ThemeTarget.ACCENT;
        themePicker = new ColorPicker(this.font, configSeed(ThemeTarget.ACCENT));
        themePicker.layout(contentX, editorTop + 62);
        addRenderableWidget(themePicker.hexBox());
        addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.theme.reset"), b -> resetTheme())
                .bounds(contentX + contentW - 110, editorTop, 110, 18).build());
    }

    private void selectThemeCard(int index) {
        if (index < Themes.ALL.size()) {
            Theme preset = Themes.ALL.get(index);
            if (themePicker != null) {
                persistTheme();
            }
            LanPlusUI.apply(preset);
            Config.setTheme(preset.id());
            rebuildWidgets();
        } else if (!"custom".equals(Config.theme)) {
            Config.setCustomTheme(configSeed(ThemeTarget.ACCENT), configSeed(ThemeTarget.BACKGROUND),
                    configSeed(ThemeTarget.TEXT));
            LanPlusUI.apply(Themes.custom(configSeed(ThemeTarget.ACCENT), configSeed(ThemeTarget.BACKGROUND),
                    configSeed(ThemeTarget.TEXT)));
            rebuildWidgets();
        }
    }

    private void selectThemeTarget(ThemeTarget t) {
        if (t == themeTarget) {
            return;
        }
        writeActiveSeed();
        themeTarget = t;
        themePicker.setColor(configSeed(t));
    }

    private void resetTheme() {
        Config.customAccent = Themes.AMETHYST.accent() & 0xFFFFFF;
        Config.customBackground = Themes.AMETHYST.surface() & 0xFFFFFF;
        Config.customText = Themes.AMETHYST.text() & 0xFFFFFF;
        themePicker.setColor(configSeed(themeTarget));
        Config.save();
    }

    private void writeActiveSeed() {
        switch (themeTarget) {
            case ACCENT -> Config.customAccent = themePicker.color();
            case BACKGROUND -> Config.customBackground = themePicker.color();
            case TEXT -> Config.customText = themePicker.color();
        }
    }

    private void persistTheme() {
        writeActiveSeed();
        Config.save();
    }

    private int configSeed(ThemeTarget t) {
        return switch (t) {
            case ACCENT -> Config.customAccent;
            case BACKGROUND -> Config.customBackground;
            case TEXT -> Config.customText;
        };
    }

    private int liveSeed(ThemeTarget t) {
        return themePicker != null && t == themeTarget ? themePicker.color() : configSeed(t);
    }

    private int cardX(int index) {
        return contentX + index * (cardW + CARD_GAP);
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selected == Cat.THEME && button == 0) {
            int card = cardIndexAt(mouseX, mouseY);
            if (card >= 0) {
                selectThemeCard(card);
                return true;
            }
            if (themePicker != null) {
                ThemeTarget target = targetAt(mouseX, mouseY);
                if (target != null) {
                    selectThemeTarget(target);
                    return true;
                }
                if (themePicker.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (themePicker != null && themePicker.mouseDragged(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (themePicker != null) {
            themePicker.mouseReleased();
            persistTheme();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int cardIndexAt(double mouseX, double mouseY) {
        if (mouseY < cardsTop || mouseY >= cardsTop + CARD_H) {
            return -1;
        }
        for (int i = 0; i < 4; i++) {
            int x = cardX(i);
            if (mouseX >= x && mouseX < x + cardW) {
                return i;
            }
        }
        return -1;
    }

    private ThemeTarget targetAt(double mouseX, double mouseY) {
        int ty = editorTop + 32;
        if (mouseY < ty || mouseY >= ty + 20) {
            return null;
        }
        int tw = (contentW - 2 * 6) / 3;
        for (ThemeTarget t : ThemeTarget.values()) {
            int tx = contentX + t.ordinal() * (tw + 6);
            if (mouseX >= tx && mouseX < tx + tw) {
                return t;
            }
        }
        return null;
    }

    @Override
    public void onClose() {
        if (commitBoxes()) {
            if (themePicker != null) {
                persistTheme();
            }
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

    private final class CategoryButton extends Button {
        private final Cat category;

        private CategoryButton(int x, int y, Cat category) {
            super(x, y, CATEGORY_W, 18,
                    Component.translatable("gui.lanplus.settings." + category.key),
                    button -> selectCat(category), DEFAULT_NARRATION);
            this.category = category;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            boolean selected = category == SettingsScreen.this.selected;
            boolean highlighted = isHoveredOrFocused();
            if (selected) {
                LanPlusUI.button3d(g, x, y, x + getWidth(), y + getHeight(), LanPlusUI.SURFACE_RAISED);
                g.fill(x + 2, y + 2, x + 4, y + getHeight() - 2, LanPlusUI.LIME);
            } else if (highlighted) {
                g.fill(x, y, x + getWidth(), y + getHeight(), LanPlusUI.SURFACE_HOVER);
            }
            g.drawString(SettingsScreen.this.font, getMessage(), x + 9, y + (getHeight() - 8) / 2,
                    selected || highlighted ? LanPlusUI.TEXT : LanPlusUI.MUTED, false);
        }
    }
}
