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
        TEXT("gui.lanplus.theme.text"),
        MUTED("gui.lanplus.theme.muted");
        final String key;

        ThemeTarget(String key) {
            this.key = key;
        }
    }

    private static final int CATEGORY_W = 94;
    private static final int CH_COLS = 2;
    private static final int CH_ROW_H = 20;
    private static final int CH_ROW_GAP = 6;
    private static final int OPACITY_BLOCK = 20;
    private static final int OPACITY_MIN = 25;
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
    private ColorPicker themePicker;
    private ThemeTarget themeTarget = ThemeTarget.ACCENT;
    private int px, py, pw, ph, headerBottom, sidebarX, dividerX, contentX, contentW, contentTop;
    private int innerX, innerY, innerW, innerH, innerLeftX, leftW, chY, chW, pickerY, opacityY, opacityW;
    private boolean opacityDrag;
    private int dividerThemeX, presetX, presetColW, presetTop, resetY;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("gui.lanplus.settings.title"));
        this.parent = parent;
        this.backendDraft = Config.backendUrl;
        this.relayAddressDraft = Config.relayDevAddress;
        this.voiceHostDraft = Config.voiceHost;
    }

    private void layout() {
        pw = fitWidth(700);
        ph = fitHeight(360);
        px = centerX(pw);
        py = centerY(ph);
        headerBottom = py + 30;
        sidebarX = px + 14;
        dividerX = sidebarX + CATEGORY_W + 12;
        contentX = dividerX + 12;
        contentW = px + pw - 12 - contentX;
        contentTop = headerBottom + 10;
        themeLayout();
    }

    private void themeLayout() {
        int pad = 14;
        int gap = 14;
        presetColW = 88;
        innerW = contentW;
        innerX = contentX;
        int chRows = (ThemeTarget.values().length + CH_COLS - 1) / CH_COLS;
        int channelsH = chRows * CH_ROW_H + (chRows - 1) * CH_ROW_GAP;
        innerH = pad + channelsH + 14 + ColorPicker.preferredHeight() + 12 + OPACITY_BLOCK + pad;
        innerY = contentTop + 2;
        innerLeftX = innerX + pad;
        leftW = innerW - 2 * pad - presetColW - 2 * gap - 1;
        dividerThemeX = innerLeftX + leftW + gap;
        presetX = dividerThemeX + 1 + gap;
        presetTop = innerY + pad;
        chY = innerY + pad;
        int maxLabel = 0;
        for (ThemeTarget t : ThemeTarget.values()) {
            maxLabel = Math.max(maxLabel, this.font.width(Component.translatable(t.key)));
        }
        chW = 18 + maxLabel + 10;
        pickerY = chY + channelsH + 14;
        opacityY = pickerY + ColorPicker.preferredHeight() + 22;
        opacityW = leftW * 2 / 5;
        resetY = innerY + innerH - pad - 18;
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
        LanPlusUI.apply(Themes.custom(liveSeed(ThemeTarget.ACCENT), liveSeed(ThemeTarget.BACKGROUND),
                liveSeed(ThemeTarget.TEXT), liveSeed(ThemeTarget.MUTED)));

        g.fill(dividerThemeX, innerY + 14, dividerThemeX + 1, innerY + innerH - 14, LanPlusUI.DIVIDER);

        renderChannels(g);
        if (themePicker != null) {
            themePicker.render(g);
        }
        renderOpacity(g);
        renderPresets(g);
    }

    private void renderOpacity(GuiGraphics g) {
        int tx = innerLeftX;
        int tw = opacityW;
        int ty = opacityY;
        g.drawString(this.font, Component.translatable("gui.lanplus.theme.opacity", Config.uiOpacity),
                tx, ty - 11, LanPlusUI.MUTED, false);
        g.fill(tx, ty, tx + tw, ty + 6, LanPlusUI.SLOT);
        LanPlusUI.outline1(g, tx, ty, tx + tw, ty + 6, LanPlusUI.EDGE_DARK);
        int fillW = Math.round((Config.uiOpacity - OPACITY_MIN) / (float) (100 - OPACITY_MIN) * tw);
        g.fill(tx, ty, tx + fillW, ty + 6, LanPlusUI.ACCENT);
        int knobX = Math.clamp(tx + fillW - 2, tx, tx + tw - 4);
        g.fill(knobX, ty - 2, knobX + 4, ty + 8, LanPlusUI.TEXT);
    }

    private void renderChannels(GuiGraphics g) {
        ThemeTarget[] all = ThemeTarget.values();
        for (int i = 0; i < all.length; i++) {
            ThemeTarget t = all[i];
            int tx = innerLeftX + (i % CH_COLS) * (chW + 6);
            int ty = chY + (i / CH_COLS) * (CH_ROW_H + CH_ROW_GAP);
            boolean sel = t == themeTarget;
            LanPlusUI.button3d(g, tx, ty, tx + chW, ty + CH_ROW_H, sel ? LanPlusUI.SURFACE_HOVER : LanPlusUI.SURFACE);
            g.fill(tx + 6, ty + 6, tx + 14, ty + 14, 0xFF000000 | (liveSeed(t) & 0xFFFFFF));
            LanPlusUI.outline1(g, tx + 6, ty + 6, tx + 14, ty + 14, LanPlusUI.EDGE_DARK);
            if (sel) {
                LanPlusUI.outline1(g, tx, ty, tx + chW, ty + CH_ROW_H, LanPlusUI.ACCENT);
            }
            g.drawString(this.font, Component.translatable(t.key), tx + 18, ty + 6,
                    sel ? LanPlusUI.TEXT : LanPlusUI.MUTED, false);
        }
    }

    private void renderPresets(GuiGraphics g) {
        g.drawString(this.font, Component.translatable("gui.lanplus.settings.theme"),
                presetX, presetTop, LanPlusUI.FAINT, false);
        int sw = 18;
        for (int i = 0; i < Themes.ALL.size(); i++) {
            Theme t = Themes.ALL.get(i);
            int rowY = presetTop + 14 + i * 24;
            g.fill(presetX, rowY, presetX + sw, rowY + sw, 0xFF000000 | (t.accent() & 0xFFFFFF));
            LanPlusUI.outline1(g, presetX, rowY, presetX + sw, rowY + sw, LanPlusUI.EDGE_DARK);
            g.drawString(this.font, t.name(), presetX + sw + 6, rowY + 5, LanPlusUI.MUTED, false);
        }
    }

    private void addThemeWidgets() {
        if (!"custom".equals(Config.theme)) {
            seedCustomFrom(Themes.resolve(Config.theme));
            Config.setTheme("custom");
            LanPlusUI.apply(Themes.custom(Config.customAccent, Config.customBackground, Config.customText,
                    Config.customMuted));
            Config.save();
        }
        themeTarget = ThemeTarget.ACCENT;
        themePicker = new ColorPicker(this.font, configSeed(ThemeTarget.ACCENT));
        themePicker.layout(innerLeftX, pickerY, leftW);
        addRenderableWidget(themePicker.hexBox());
        addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.theme.reset"), b -> resetTheme())
                .bounds(presetX, resetY, presetColW + 8, 18).build());
    }

    private void selectPreset(int index) {
        seedCustomFrom(Themes.ALL.get(index));
        themePicker.setColor(configSeed(themeTarget));
        Config.save();
    }

    private void seedCustomFrom(Theme t) {
        Config.customAccent = t.accent() & 0xFFFFFF;
        Config.customBackground = t.surface() & 0xFFFFFF;
        Config.customText = t.text() & 0xFFFFFF;
        Config.customMuted = t.muted() & 0xFFFFFF;
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
        seedCustomFrom(Themes.AMETHYST);
        themePicker.setColor(configSeed(themeTarget));
        Config.uiOpacity = 100;
        Config.save();
    }

    private void writeActiveSeed() {
        switch (themeTarget) {
            case ACCENT -> Config.customAccent = themePicker.color();
            case BACKGROUND -> Config.customBackground = themePicker.color();
            case TEXT -> Config.customText = themePicker.color();
            case MUTED -> Config.customMuted = themePicker.color();
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
            case MUTED -> Config.customMuted;
        };
    }

    private int liveSeed(ThemeTarget t) {
        return themePicker != null && t == themeTarget ? themePicker.color() : configSeed(t);
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
        if (selected == Cat.THEME && button == 0 && themePicker != null) {
            int preset = presetAt(mouseX, mouseY);
            if (preset >= 0) {
                selectPreset(preset);
                return true;
            }
            ThemeTarget target = targetAt(mouseX, mouseY);
            if (target != null) {
                selectThemeTarget(target);
                return true;
            }
            if (inOpacity(mouseX, mouseY)) {
                opacityDrag = true;
                setOpacityFromMouse(mouseX);
                return true;
            }
            if (themePicker.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (opacityDrag) {
            setOpacityFromMouse(mouseX);
            return true;
        }
        if (themePicker != null && themePicker.mouseDragged(mouseX, mouseY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        opacityDrag = false;
        if (themePicker != null) {
            themePicker.mouseReleased();
            persistTheme();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int presetAt(double mouseX, double mouseY) {
        if (mouseX < presetX || mouseX > presetX + presetColW) {
            return -1;
        }
        for (int i = 0; i < Themes.ALL.size(); i++) {
            int rowY = presetTop + 14 + i * 24;
            if (mouseY >= rowY && mouseY <= rowY + 18) {
                return i;
            }
        }
        return -1;
    }

    private boolean inOpacity(double mouseX, double mouseY) {
        return mouseX >= innerLeftX && mouseX <= innerLeftX + opacityW
                && mouseY >= opacityY - 4 && mouseY <= opacityY + 10;
    }

    private void setOpacityFromMouse(double mouseX) {
        float f = (float) (mouseX - innerLeftX) / opacityW;
        Config.uiOpacity = Math.clamp(OPACITY_MIN + Math.round(f * (100 - OPACITY_MIN)), OPACITY_MIN, 100);
    }

    private ThemeTarget targetAt(double mouseX, double mouseY) {
        ThemeTarget[] all = ThemeTarget.values();
        for (int i = 0; i < all.length; i++) {
            int tx = innerLeftX + (i % CH_COLS) * (chW + 6);
            int ty = chY + (i / CH_COLS) * (CH_ROW_H + CH_ROW_GAP);
            if (mouseX >= tx && mouseX < tx + chW && mouseY >= ty && mouseY < ty + CH_ROW_H) {
                return all[i];
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
            } else if (highlighted) {
                g.fill(x, y, x + getWidth(), y + getHeight(), LanPlusUI.SURFACE_HOVER);
            }
            g.drawString(SettingsScreen.this.font, getMessage(), x + 9, y + (getHeight() - 8) / 2,
                    selected || highlighted ? LanPlusUI.TEXT : LanPlusUI.MUTED, false);
        }
    }
}
