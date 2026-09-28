package dev.bgame.lanplus.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.api.HostAccessMode;
import dev.bgame.lanplus.client.HostController;
import dev.bgame.lanplus.client.PauseMenuButtons;
import dev.bgame.lanplus.invites.HostAccessControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

public final class HostScreen extends LanPlusScreen {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CARD_W = 336;
    private static final int ROW_H = 24;
    private static final int PAD = 10;
    private static final int ICON = ROW_H - 4;
    private static final int DROPDOWN_H = 20;
    private static final int ROW_GAP = 26;
    private static final int HEADER_H = 24;
    private static final int SECTION_H = 16;
    private static final int LABEL_PAD = 12;
    private static final int CTRL_W = 110;
    private static final GameType[] GAME_TYPES = GameType.values();
    private static final Difficulty[] DIFFICULTIES = Difficulty.values();
    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 20;
    private static final int STEP_HIT_W = 16;
    private final Screen parent;
    private final boolean inWorld;
    private final Map<String, FaviconTexture> icons = new HashMap<>();
    private List<LevelSummary> worlds = List.of();
    private boolean loading = true;
    private boolean loadStarted;
    private boolean loadFailed;
    private boolean iconsLoaded;
    private int selected = -1;
    private int listScroll;
    private HostAccessMode accessMode = HostAccessMode.FRIENDS;
    private boolean allowNonPremium;
    private boolean allowVanillaJoin;
    private GameType gameType = GameType.SURVIVAL;
    private Difficulty difficulty = Difficulty.NORMAL;
    private boolean allowCheats;
    private int maxPlayers = HostAccessControl.DEFAULT_MAX_PLAYERS;
    private final Dropdown gameTypeDd = new Dropdown();
    private final Dropdown difficultyDd = new Dropdown();
    private int cardX, cardY, cardW, cardH;
    private int worldHeaderY, listTop, listBottom;
    private int settingsHeaderY;
    private int gameRowY, cmdRowY, diffRowY, playersRowY;
    private int accessLabelY, accessRowY, premiumRowY;
    private int ctrlX;
    private int buttonsY;
    private LanplusButton hostButton;
    private LanplusButton cancelButton;

    public HostScreen(Screen parent) {
        this(parent, false);
    }

    public HostScreen(Screen parent, boolean inWorld) {
        super(Component.translatable(inWorld ? "gui.lanplus.host.titleingame" : "gui.lanplus.host.title"));
        this.parent = parent;
        this.inWorld = inWorld;
    }

    private void layout() {
        cardW = Math.min(this.width - 40, CARD_W);
        int y = PAD + HEADER_H;

        if (!inWorld) {
            worldHeaderY = y;
            y += SECTION_H;
            int fitRows = (this.height - 286) / ROW_H;
            int wantedRows = loading || worlds.isEmpty() ? 4 : Math.min(worlds.size(), 6);
            int listRows = Math.min(wantedRows, fitRows);
            if (listRows < 2) {
                listRows = 2;
            }
            int listH = listRows * ROW_H + 4;
            listTop = y;
            listBottom = listTop + listH;
            y = listBottom + 10;
        }

        settingsHeaderY = y;
        y += SECTION_H;
        gameRowY = y;
        y += ROW_GAP;
        cmdRowY = y;
        y += ROW_GAP;
        diffRowY = y;
        y += ROW_GAP;
        playersRowY = y;
        y += 30;
        accessLabelY = y;
        y += 12;
        accessRowY = y;
        y += DROPDOWN_H;
        y += 8;
        premiumRowY = y;
        y += DROPDOWN_H;
        y += 10;
        buttonsY = y;
        cardH = buttonsY + 20 + PAD;
        cardY = Math.max(16, (this.height - cardH) / 2);
        cardX = (this.width - cardW) / 2;

        listTop += cardY;
        listBottom += cardY;
        worldHeaderY += cardY;
        settingsHeaderY += cardY;
        gameRowY += cardY;
        cmdRowY += cardY;
        diffRowY += cardY;
        playersRowY += cardY;
        accessLabelY += cardY;
        accessRowY += cardY;
        premiumRowY += cardY;
        buttonsY += cardY;

        int labelW = Math.max(Math.max(
                        Math.max(this.font.width(Component.translatable("gui.lanplus.host.gamemode")),
                                this.font.width(Component.translatable("gui.lanplus.host.commands"))),
                        this.font.width(Component.translatable("gui.lanplus.host.difficulty"))),
                this.font.width(Component.translatable("gui.lanplus.host.maxplayers")));
        ctrlX = cardX + PAD + labelW + LABEL_PAD;
    }

    @Override
    protected void init() {
        if (!inWorld) {
            if (loading && !loadStarted) {
                loadStarted = true;
                loadWorlds();
            } else if (!loading && !iconsLoaded && !worlds.isEmpty()) {
                loadIcons();
            }
        }
        layout();

        hostButton = LanplusButton.create(Component.translatable("gui.lanplus.host.start"), b -> doStart())
                .height(20).primary().build();
        hostButton.active = inWorld || selected >= 0;
        cancelButton = LanplusButton.create(CommonComponents.GUI_CANCEL, b -> onClose())
                .height(20).build();
        addRenderableWidget(hostButton);
        addRenderableWidget(cancelButton);
        layoutButtons();
    }

    private void layoutButtons() {
        int colW = cardW - 2 * PAD;
        hostButton.setPosition(cardX + PAD, buttonsY);
        hostButton.setWidth((colW - 6) / 2 + 30);
        int cancelW = colW - 6 - ((colW - 6) / 2 + 30);
        cancelButton.setPosition(cardX + cardW - PAD - cancelW, buttonsY);
        cancelButton.setWidth(cancelW);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawBackdrop(g);

        LanPlusUI.panel(g, cardX, cardY, cardX + cardW, cardY + cardH);
        LanPlusUI.rivets(g, cardX, cardY, cardX + cardW, cardY + cardH, LanPlusUI.FAINT);
        renderHeader(g);

        boolean anyOpen = gameTypeDd.isOpen() || difficultyDd.isOpen();
        int bmx = anyOpen ? -1 : mouseX;
        int bmy = anyOpen ? -1 : mouseY;

        if (!inWorld) {
            renderSectionHeader(g, Component.translatable("selectWorld.title"), worldHeaderY);
            LanPlusUI.slot(g, cardX + PAD, listTop, cardX + cardW - PAD, listBottom);
            if (loading) {
                g.drawCenteredString(this.font, Component.translatable("gui.lanplus.host.loading"),
                        cardX + cardW / 2, emptyListTextY(), LanPlusUI.FAINT);
            } else if (loadFailed) {
                g.drawCenteredString(this.font, Component.translatable("gui.lanplus.host.load_failed"),
                        cardX + cardW / 2, emptyListTextY(), LanPlusUI.RED);
            } else if (worlds.isEmpty()) {
                g.drawCenteredString(this.font, Component.translatable("gui.lanplus.host.noworlds"),
                        cardX + cardW / 2, emptyListTextY(), LanPlusUI.FAINT);
            } else {
                renderWorldList(g, bmx, bmy);
            }
        }

        renderSectionHeader(g, this.title, settingsHeaderY);
        renderWorldSettings(g, bmx, bmy);
        renderAccess(g, bmx, bmy);

        super.render(g, bmx, bmy, partialTick);

        renderOpenDropdowns(g, mouseX, mouseY);
    }

    private int emptyListTextY() {
        return listTop + (listBottom - listTop - this.font.lineHeight) / 2;
    }

    private void renderHeader(GuiGraphics g) {
        int x = cardX + PAD;
        int y = cardY + PAD;
        int wordmarkRight = LanPlusUI.wordmark(g, this.font, x, y);
        g.drawString(this.font, Component.translatable("gui.lanplus.host.word"),
                wordmarkRight + 6, y, LanPlusUI.MUTED, false);
        g.fill(x, y + 13, cardX + cardW - PAD, y + 14, LanPlusUI.BORDER);
    }

    private void renderSectionHeader(GuiGraphics g, Component label, int y) {
        LanPlusUI.sectionHeader(g, this.font, label, cardX + PAD, y, cardX + cardW - PAD);
    }

    private void renderOpenDropdowns(GuiGraphics g, int mouseX, int mouseY) {
        if (gameTypeDd.isOpen()) {
            gameTypeDd.render(g, this.font, ctrlX, gameRowY + DROPDOWN_H, CTRL_W, CTRL_W, this.height - 4,
                    selectLabels(GAME_TYPES.length, i -> gameTypeLabel(GAME_TYPES[i])), gameType.ordinal(), mouseX, mouseY);
        }
        if (difficultyDd.isOpen()) {
            difficultyDd.render(g, this.font, ctrlX, diffRowY + DROPDOWN_H, CTRL_W, CTRL_W, this.height - 4,
                    selectLabels(DIFFICULTIES.length, i -> difficultyLabel(DIFFICULTIES[i])), difficulty.ordinal(), mouseX, mouseY);
        }
    }

    private List<Component> selectLabels(int count, IntFunction<Component> labelFn) {
        List<Component> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(labelFn.apply(i));
        }
        return out;
    }

    private void renderWorldSettings(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, Component.translatable("gui.lanplus.host.gamemode"),
                cardX + PAD, gameRowY + (DROPDOWN_H - 8) / 2, LanPlusUI.MUTED, false);
        renderDropdownButton(g, ctrlX, gameRowY, gameTypeLabel(gameType), gameTypeDd.isOpen(), mouseX, mouseY);

        g.drawString(this.font, Component.translatable("gui.lanplus.host.commands"),
                cardX + PAD, cmdRowY + (DROPDOWN_H - 8) / 2, LanPlusUI.MUTED, false);
        Component commands = Component.translatable(
                allowCheats ? "gui.lanplus.host.commands.on" : "gui.lanplus.host.commands.off");
        LanPlusUI.chip(g, this.font, commands, ctrlX, cmdRowY, CTRL_W, DROPDOWN_H,
                allowCheats, true, in(mouseX, mouseY, ctrlX, cmdRowY, CTRL_W, DROPDOWN_H));

        g.drawString(this.font, Component.translatable("gui.lanplus.host.difficulty"),
                cardX + PAD, diffRowY + (DROPDOWN_H - 8) / 2, LanPlusUI.MUTED, false);
        renderDropdownButton(g, ctrlX, diffRowY, difficultyLabel(difficulty), difficultyDd.isOpen(), mouseX, mouseY);

        g.drawString(this.font, Component.translatable("gui.lanplus.host.maxplayers"),
                cardX + PAD, playersRowY + (DROPDOWN_H - 8) / 2, LanPlusUI.MUTED, false);
        int textY = playersRowY + (DROPDOWN_H - 8) / 2;
        boolean canDown = maxPlayers > MIN_PLAYERS;
        boolean canUp = maxPlayers < MAX_PLAYERS;
        boolean hoverDown = canDown && in(mouseX, mouseY, ctrlX + 4, playersRowY, STEP_HIT_W, DROPDOWN_H);
        boolean hoverUp = canUp && in(mouseX, mouseY, ctrlX + CTRL_W - 4 - STEP_HIT_W, playersRowY, STEP_HIT_W, DROPDOWN_H);
        LanPlusUI.button3d(g, ctrlX, playersRowY, ctrlX + CTRL_W, playersRowY + DROPDOWN_H, LanPlusUI.SURFACE_RAISED);
        g.drawString(this.font, Component.literal("-"), ctrlX + 8, textY,
                canDown ? (hoverDown ? LanPlusUI.TEXT : LanPlusUI.MUTED) : LanPlusUI.FAINT, false);
        g.drawString(this.font, Component.literal("+"), ctrlX + CTRL_W - 12, textY,
                canUp ? (hoverUp ? LanPlusUI.TEXT : LanPlusUI.MUTED) : LanPlusUI.FAINT, false);
        g.drawCenteredString(this.font, Component.literal(Integer.toString(maxPlayers)),
                ctrlX + CTRL_W / 2, textY, LanPlusUI.TEXT);
    }

    private void renderDropdownButton(GuiGraphics g, int x, int y, Component label,
                                      boolean open, int mouseX, int mouseY) {
        boolean hover = in(mouseX, mouseY, x, y, CTRL_W, DROPDOWN_H);
        int bg = open ? LanPlusUI.ACCENT : hover ? LanPlusUI.SURFACE_HOVER : LanPlusUI.SURFACE_RAISED;
        LanPlusUI.button3d(g, x, y, x + CTRL_W, y + DROPDOWN_H, bg);
        g.drawString(this.font, label, x + 6, y + (DROPDOWN_H - 8) / 2, LanPlusUI.TEXT, false);
        Component caret = Component.literal(open ? "▲" : "▼");
        g.drawString(this.font, caret, x + CTRL_W - 14, y + (DROPDOWN_H - 8) / 2, LanPlusUI.MUTED, false);
    }

    private void renderAccess(GuiGraphics g, int mouseX, int mouseY) {
        renderSectionHeader(g, Component.translatable("gui.lanplus.host.access"), accessLabelY);
        int chipW = accessChipW();
        renderModeChip(g, mouseX, mouseY, HostAccessMode.EVERYONE, "gui.lanplus.host.access.everyone",
                cardX + PAD, chipW);
        renderModeChip(g, mouseX, mouseY, HostAccessMode.FRIENDS, "gui.lanplus.host.access.friends",
                cardX + PAD + chipW + 6, chipW);
        renderModeChip(g, mouseX, mouseY, HostAccessMode.INVITED, "gui.lanplus.host.access.invited",
                cardX + PAD + 2 * (chipW + 6), cardW - 2 * PAD - 2 * (chipW + 6));

        int halfW = (cardW - 2 * PAD - 6) / 2;
        int premiumX = cardX + PAD;
        int vanillaX = cardX + PAD + halfW + 6;
        int vanillaW = cardX + cardW - PAD - vanillaX;

        Component premium = Component.translatable("gui.lanplus.host.nonpremium", Component.translatable(
                allowNonPremium ? "gui.lanplus.host.nonpremium.on" : "gui.lanplus.host.nonpremium.off"));
        boolean hover = in(mouseX, mouseY, premiumX, premiumRowY, halfW, DROPDOWN_H);
        LanPlusUI.chip(g, this.font, premium, premiumX, premiumRowY, halfW, DROPDOWN_H,
                allowNonPremium, true, hover);
        if (hover) {
            g.renderTooltip(this.font,
                    this.font.split(Component.translatable("gui.lanplus.host.nonpremium.tip"), 220),
                    mouseX, mouseY);
        }

        boolean vanillaEnabled = allowNonPremium && accessMode == HostAccessMode.EVERYONE;
        Component vanilla = Component.translatable("gui.lanplus.host.vanilla", Component.translatable(
                allowVanillaJoin ? "gui.lanplus.host.vanilla.on" : "gui.lanplus.host.vanilla.off"));
        boolean vanillaHover = in(mouseX, mouseY, vanillaX, premiumRowY, vanillaW, DROPDOWN_H);
        LanPlusUI.chip(g, this.font, vanilla, vanillaX, premiumRowY, vanillaW, DROPDOWN_H,
                allowVanillaJoin && vanillaEnabled, vanillaEnabled, vanillaHover);
        if (vanillaHover) {
            g.renderTooltip(this.font,
                    this.font.split(Component.translatable("gui.lanplus.host.vanilla.tip"), 220),
                    mouseX, mouseY);
        }
    }

    private void renderModeChip(GuiGraphics g, int mouseX, int mouseY, HostAccessMode mode, String key,
                                int x, int w) {
        boolean hover = in(mouseX, mouseY, x, accessRowY, w, DROPDOWN_H);
        LanPlusUI.chip(g, this.font, Component.translatable(key), x, accessRowY, w, DROPDOWN_H,
                accessMode == mode, true, hover);
    }

    private int accessChipW() {
        return (cardW - 2 * PAD - 2 * 6) / 3;
    }

    private Component gameTypeLabel(GameType gt) {
        return Component.translatable(switch (gt) {
            case CREATIVE -> "selectWorld.gameMode.creative";
            case ADVENTURE -> "selectWorld.gameMode.adventure";
            case SPECTATOR -> "selectWorld.gameMode.spectator";
            default -> "gameMode.survival";
        });
    }

    private Component difficultyLabel(Difficulty d) {
        return Component.translatable(switch (d) {
            case PEACEFUL -> "options.difficulty.peaceful";
            case EASY -> "options.difficulty.easy";
            case HARD -> "options.difficulty.hard";
            default -> "options.difficulty.normal";
        });
    }

    private void renderWorldList(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = cardX + PAD;
        int x1 = cardX + cardW - PAD;
        int y = listTop + 2 - listScroll;
        for (int i = 0; i < worlds.size(); i++) {
            if (y + ROW_H > listTop && y < listBottom) {
                boolean sel = i == selected;
                boolean hover = in(mouseX, mouseY, x0, Math.max(y, listTop), x1 - x0,
                        Math.min(y + ROW_H, listBottom) - Math.max(y, listTop));
                if (sel || hover) {
                    int rowTop = Math.max(y, listTop + 1);
                    int rowBottom = Math.min(y + ROW_H, listBottom - 1);
                    g.fill(x0 + 2, rowTop, x1 - 4, rowBottom,
                            sel ? LanPlusUI.ACCENT_TINT : LanPlusUI.SURFACE_HOVER);
                }
                if (sel) {
                    int rowTop = Math.max(y, listTop + 1);
                    int rowBottom = Math.min(y + ROW_H, listBottom - 1);
                    LanPlusUI.outline1(g, x0 + 2, rowTop, x1 - 4, rowBottom, LanPlusUI.ACCENT);
                }
                LevelSummary s = worlds.get(i);
                FaviconTexture icon = icons.get(s.getLevelId());
                if (icon != null && y + 2 >= listTop && y + 2 + ICON <= listBottom) {
                    g.blit(icon.textureLocation(), x0 + 4, y + 2, ICON, ICON, 0.0F, 0.0F, 64, 64, 64, 64);
                }
                int textX = x0 + 8 + ICON + 6;
                if (y + 4 >= listTop && y + 12 <= listBottom) {
                    g.drawString(this.font, s.getLevelName(), textX, y + 4, LanPlusUI.TEXT, false);
                }
                if (y + 14 >= listTop && y + 22 <= listBottom) {
                    g.drawString(this.font, s.getLevelId(), textX, y + 14, LanPlusUI.FAINT, false);
                }
            }
            y += ROW_H;
        }
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        if (!inWorld && in(mouseX, mouseY, cardX + PAD, listTop, cardW - 2 * PAD, listBottom - listTop)) {
            int max = Math.max(0, worlds.size() * ROW_H + 4 - (listBottom - listTop));
            listScroll = Math.clamp(listScroll - (int) (delta * ROW_H), 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (handleSettingClick(mouseX, mouseY)) {
                return true;
            }
            if (!inWorld) {
                if (!loading && in(mouseX, mouseY, cardX + PAD, listTop, cardW - 2 * PAD, listBottom - listTop)) {
                    int idx = (int) ((mouseY - (listTop + 2 - listScroll)) / ROW_H);
                    if (idx >= 0 && idx < worlds.size()) {
                        selected = idx;
                        rebuildWidgets();
                    }
                    return true;
                }
            }
        }
        return handleSharedClick(mouseX, mouseY, button);
    }

    private boolean handleSettingClick(double mouseX, double mouseY) {
        if (gameTypeDd.isOpen()) {
            int ci = gameTypeDd.clicked(mouseX, mouseY);
            if (ci >= 0) {
                gameType = GAME_TYPES[ci];
            }
            gameTypeDd.close();
            return true;
        }
        if (difficultyDd.isOpen()) {
            int ci = difficultyDd.clicked(mouseX, mouseY);
            if (ci >= 0) {
                difficulty = DIFFICULTIES[ci];
            }
            difficultyDd.close();
            return true;
        }

        if (in(mouseX, mouseY, ctrlX, gameRowY, CTRL_W, DROPDOWN_H)) {
            gameTypeDd.open(0);
            difficultyDd.close();
            return true;
        }
        if (in(mouseX, mouseY, ctrlX, diffRowY, CTRL_W, DROPDOWN_H)) {
            difficultyDd.open(0);
            gameTypeDd.close();
            return true;
        }

        if (in(mouseX, mouseY, ctrlX, cmdRowY, CTRL_W, DROPDOWN_H)) {
            allowCheats = !allowCheats;
            return true;
        }
        if (in(mouseX, mouseY, ctrlX + 4, playersRowY, STEP_HIT_W, DROPDOWN_H)) {
            maxPlayers = Math.max(MIN_PLAYERS, maxPlayers - 1);
            return true;
        }
        if (in(mouseX, mouseY, ctrlX + CTRL_W - 4 - STEP_HIT_W, playersRowY, STEP_HIT_W, DROPDOWN_H)) {
            maxPlayers = Math.min(MAX_PLAYERS, maxPlayers + 1);
            return true;
        }
        return false;
    }

    private boolean handleSharedClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (in(mouseX, mouseY, cardX + PAD, accessRowY, cardW - 2 * PAD, DROPDOWN_H)) {
                int chipW = accessChipW();
                if (mouseX < cardX + PAD + chipW) {
                    accessMode = HostAccessMode.EVERYONE;
                } else if (mouseX < cardX + PAD + 2 * chipW + 6) {
                    accessMode = HostAccessMode.FRIENDS;
                } else {
                    accessMode = HostAccessMode.INVITED;
                }
                return true;
            }
            int halfW = (cardW - 2 * PAD - 6) / 2;
            int vanillaX = cardX + PAD + halfW + 6;
            if (in(mouseX, mouseY, cardX + PAD, premiumRowY, halfW, DROPDOWN_H)) {
                allowNonPremium = !allowNonPremium;
                return true;
            }
            if (allowNonPremium && accessMode == HostAccessMode.EVERYONE
                    && in(mouseX, mouseY, vanillaX, premiumRowY, cardX + cardW - PAD - vanillaX, DROPDOWN_H)) {
                allowVanillaJoin = !allowVanillaJoin;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void loadWorlds() {
        Minecraft minecraft = Minecraft.getInstance();
        LevelStorageSource source = minecraft.getLevelSource();
        try {
            source.loadLevelSummaries(source.findLevelCandidates()).whenComplete((summaries, error) ->
                    minecraft.execute(() -> {
                        loading = false;
                        if (error != null) {
                            loadFailed = true;
                            worlds = List.of();
                            LOGGER.warn("Failed to load singleplayer worlds", error);
                            if (minecraft.screen == this) {
                                rebuildWidgets();
                            }
                            return;
                        }
                        List<LevelSummary> usable = new ArrayList<>();
                        for (LevelSummary summary : summaries) {
                            if (!summary.isDisabled()) {
                                usable.add(summary);
                            }
                        }
                        this.worlds = usable;
                        this.loadFailed = false;
                        if (minecraft.screen == this) {
                            loadIcons();
                            rebuildWidgets();
                        }
                    }));
        } catch (LevelStorageException e) {
            this.worlds = List.of();
            this.loading = false;
            this.loadFailed = true;
            LOGGER.warn("Failed to find singleplayer worlds", e);
        }
    }

    private void loadIcons() {
        for (LevelSummary world : worlds) {
            loadIcon(world);
        }
        iconsLoaded = true;
    }

    private void loadIcon(LevelSummary summary) {
        Path iconFile = summary.getIcon();
        if (!Files.isRegularFile(iconFile)) {
            return;
        }
        FaviconTexture tex = FaviconTexture.forWorld(
                Minecraft.getInstance().getTextureManager(), summary.getLevelId());
        try (InputStream in = Files.newInputStream(iconFile)) {
            NativeImage image = NativeImage.read(in);
            if (image.getWidth() == 64 && image.getHeight() == 64) {
                tex.upload(image);
                icons.put(summary.getLevelId(), tex);
            } else {
                image.close();
                tex.close();
            }
        } catch (IOException e) {
            tex.close();
            LOGGER.debug("Failed to load icon for world {}", summary.getLevelId(), e);
        }
    }

    @Override
    public void removed() {
        for (FaviconTexture tex : icons.values()) {
            tex.close();
        }
        icons.clear();
        iconsLoaded = false;
    }

    private void doStart() {
        Minecraft minecraft = Minecraft.getInstance();
        HostController.HostSettings settings = new HostController.HostSettings(
                accessMode, Set.of(), allowNonPremium, gameType, difficulty, allowCheats, maxPlayers,
                allowVanillaJoin && accessMode == HostAccessMode.EVERYONE);
        if (inWorld) {
            if (accessMode == HostAccessMode.INVITED) {
                minecraft.setScreen(new InviteOverlay(this, settings));
                return;
            }
            PauseMenuButtons.markHostedInWorld();
            HostController.requestHost(settings);
            onClose();
            return;
        }
        LevelSummary world = selected >= 0 && selected < worlds.size() ? worlds.get(selected) : null;
        if (world == null) {
            return;
        }
        if (accessMode == HostAccessMode.INVITED) {
            minecraft.setScreen(new InviteOverlay(this, world, settings));
        } else {
            HostController.requestHost(settings);
            minecraft.createWorldOpenFlows().openWorld(world.getLevelId(), () -> minecraft.setScreen(this));
        }
    }
}
