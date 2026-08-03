package dev.bgame.lanplus.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bgame.lanplus.api.HostAccessMode;
import dev.bgame.lanplus.client.HostController;
import dev.bgame.lanplus.client.PauseMenuButtons;
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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * "Host a World" — pick one of your worlds from the title flow (or configure the running world from
 * the pause menu, in-game flow), choose who can join, and open it to LAN+.
 * Each option row shows its translatable label with the control (dropdown / toggle) right next to it;
 * opening a dropdown draws it on top of the card without resizing it.
 */
public final class HostScreen extends Screen {

    private static final int CARD_W = 320;
    private static final int ROW_H = 24;
    private static final int PAD = 10;
    private static final int ICON = ROW_H - 4;
    private static final int ITEM_H = 18;
    private static final int DROPDOWN_H = 20;
    private static final int ROW_GAP = 26;
    private static final int LABEL_PAD = 12;
    private static final int CTRL_W = 110;

    private static final GameType[] GAME_TYPES = GameType.values();
    private static final Difficulty[] DIFFICULTIES = Difficulty.values();

    private final Screen parent;
    private final boolean inWorld;
    private final Map<String, FaviconTexture> icons = new HashMap<>();
    private List<LevelSummary> worlds = List.of();
    private boolean loading = true;
    private int selected = -1;
    private int listScroll;
    private HostAccessMode accessMode = HostAccessMode.FRIENDS;
    private boolean allowNonPremium = false;

    private GameType gameType = GameType.SURVIVAL;
    private Difficulty difficulty = Difficulty.NORMAL;
    private boolean allowCheats;
    private boolean gameTypeOpen;
    private boolean difficultyOpen;

    private int cardX, cardY, cardW, cardH;
    private int listTop, listBottom;
    private int gameRowY, cmdRowY, diffRowY;
    private int accessLabelY, accessRowY, premiumRowY;
    private int ctrlX, ctrlW;
    private int buttonsY;
    private LanPlusButton hostButton;
    private LanPlusButton cancelButton;

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
        int y = cardY + PAD + 24;

        if (!inWorld) {
            int listRows = Math.max(3, Math.min(visibleRowsWanted(), (this.height - 190) / ROW_H));
            int listH = listRows * ROW_H + 4;
            listTop = y;
            listBottom = listTop + listH;
            y = listBottom + 8;
        }

        gameRowY = y;
        y += ROW_GAP;
        cmdRowY = y;
        y += ROW_GAP;
        diffRowY = y;
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
        cardH = buttonsY + 20 + PAD - cardY;
        cardY = Math.max(16, (this.height - cardH) / 2);
        cardX = (this.width - cardW) / 2;

        int labelW = Math.max(
                Math.max(this.font.width(Component.translatable("gui.lanplus.host.gamemode")),
                        this.font.width(Component.translatable("gui.lanplus.host.commands"))),
                this.font.width(Component.translatable("gui.lanplus.host.difficulty")));
        ctrlX = cardX + PAD + labelW + LABEL_PAD;
        ctrlW = CTRL_W;
    }

    private int visibleRowsWanted() {
        return loading || worlds.isEmpty() ? 4 : Math.min(worlds.size(), 6);
    }

    @Override
    protected void init() {
        if (!inWorld) {
            if (loading) {
                loadWorlds();
            } else if (icons.isEmpty() && !worlds.isEmpty()) {
                for (LevelSummary s : worlds) {
                    loadIcon(s);
                }
            }
        }
        layout();

        hostButton = LanPlusButton.create(Component.translatable("gui.lanplus.host.start"), b -> doStart())
                .height(20).build();
        hostButton.active = inWorld || selected >= 0;
        cancelButton = LanPlusButton.create(CommonComponents.GUI_CANCEL, b -> onClose())
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
        renderBackground(g);
        LanPlusUi.backdrop(g, this.width, this.height);
        layout();
        layoutButtons();

        LanPlusUi.panel(g, cardX, cardY, cardX + cardW, cardY + cardH);
        LanPlusUi.header(g, this.font, this.title, cardX + PAD, cardY + PAD, cardW - 2 * PAD);

        if (!inWorld) {
            g.fill(cardX + PAD, listTop, cardX + cardW - PAD, listBottom, LanPlusUi.SURFACE_RAISED);
            LanPlusUi.border(g, cardX + PAD, listTop, cardX + cardW - PAD, listBottom);
            if (loading) {
                g.drawCenteredString(this.font, Component.translatable("gui.lanplus.host.loading"),
                        cardX + cardW / 2, listTop + 24, LanPlusUi.FAINT);
            } else if (worlds.isEmpty()) {
                g.drawCenteredString(this.font, Component.translatable("gui.lanplus.host.noworlds"),
                        cardX + cardW / 2, listTop + 24, LanPlusUi.FAINT);
            } else {
                renderWorldList(g, mouseX, mouseY);
            }
        }

        renderWorldSettings(g, mouseX, mouseY);
        renderAccess(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);

        renderOpenDropdowns(g, mouseX, mouseY);
    }

    private void renderOpenDropdowns(GuiGraphics g, int mouseX, int mouseY) {
        if (gameTypeOpen) {
            renderSelectItems(g, ctrlX, gameRowY + DROPDOWN_H, ctrlW, GAME_TYPES.length,
                    i -> gameTypeLabel(GAME_TYPES[i]), indexOf(gameType, GAME_TYPES), mouseX, mouseY);
        }
        if (difficultyOpen) {
            renderSelectItems(g, ctrlX, diffRowY + DROPDOWN_H, ctrlW, DIFFICULTIES.length,
                    i -> difficultyLabel(DIFFICULTIES[i]), indexOf(difficulty, DIFFICULTIES), mouseX, mouseY);
        }
    }

    private void renderWorldSettings(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, Component.translatable("gui.lanplus.host.gamemode"),
                cardX + PAD, gameRowY + (DROPDOWN_H - 8) / 2, LanPlusUi.MUTED, false);
        renderDropdownButton(g, ctrlX, gameRowY, gameTypeLabel(gameType), gameTypeOpen, mouseX, mouseY);

        g.drawString(this.font, Component.translatable("gui.lanplus.host.commands"),
                cardX + PAD, cmdRowY + (DROPDOWN_H - 8) / 2, LanPlusUi.MUTED, false);
        Component commands = Component.translatable(
                allowCheats ? "gui.lanplus.host.commands.on" : "gui.lanplus.host.commands.off");
        LanPlusUi.chip(g, this.font, commands, ctrlX, cmdRowY, ctrlW, DROPDOWN_H,
                allowCheats, true, in(mouseX, mouseY, ctrlX, cmdRowY, ctrlW, DROPDOWN_H));

        g.drawString(this.font, Component.translatable("gui.lanplus.host.difficulty"),
                cardX + PAD, diffRowY + (DROPDOWN_H - 8) / 2, LanPlusUi.MUTED, false);
        renderDropdownButton(g, ctrlX, diffRowY, difficultyLabel(difficulty), difficultyOpen, mouseX, mouseY);
    }

    private void renderDropdownButton(GuiGraphics g, int x, int y, Component label,
                                      boolean open, int mouseX, int mouseY) {
        boolean hover = in(mouseX, mouseY, x, y, ctrlW, DROPDOWN_H);
        int bg = open ? LanPlusUi.BLURPLE : hover ? 0xFF35373C : LanPlusUi.SURFACE_RAISED;
        g.fill(x, y, x + ctrlW, y + DROPDOWN_H, bg);
        LanPlusUi.border(g, x, y, x + ctrlW, y + DROPDOWN_H);
        g.drawString(this.font, label, x + 6, y + (DROPDOWN_H - 8) / 2, LanPlusUi.TEXT, false);
        Component caret = Component.literal(open ? "\u25B2" : "\u25BC");
        g.drawString(this.font, caret, x + ctrlW - 14, y + (DROPDOWN_H - 8) / 2, LanPlusUi.MUTED, false);
    }

    private void renderSelectItems(GuiGraphics g, int x, int menuTop, int w, int count,
                                   Function<Integer, Component> labelFn, int selectedIdx,
                                   int mouseX, int mouseY) {
        int menuH = count * ITEM_H + 4;
        g.fill(x, menuTop, x + w, menuTop + menuH, LanPlusUi.SURFACE_RAISED);
        LanPlusUi.border(g, x, menuTop, x + w, menuTop + menuH);
        for (int i = 0; i < count; i++) {
            int iy = menuTop + 2 + i * ITEM_H;
            boolean hover = in(mouseX, mouseY, x, iy, w, ITEM_H);
            int bg = hover ? 0xFF35373C : LanPlusUi.SURFACE_RAISED;
            if (i == selectedIdx) {
                bg = LanPlusUi.BLURPLE_TINT;
            }
            g.fill(x + 1, iy, x + w - 1, iy + ITEM_H, bg);
            g.drawString(this.font, labelFn.apply(i), x + 6, iy + (ITEM_H - 8) / 2,
                    i == selectedIdx ? LanPlusUi.TEXT : LanPlusUi.MUTED, false);
        }
    }

    private void renderAccess(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, Component.translatable("gui.lanplus.host.access"),
                cardX + PAD, accessLabelY, LanPlusUi.MUTED, false);
        int chipW = (cardW - 2 * PAD - 2 * 6) / 3;
        renderModeChip(g, mouseX, mouseY, HostAccessMode.EVERYONE, "gui.lanplus.host.access.everyone",
                cardX + PAD, chipW);
        renderModeChip(g, mouseX, mouseY, HostAccessMode.FRIENDS, "gui.lanplus.host.access.friends",
                cardX + PAD + chipW + 6, chipW);
        renderModeChip(g, mouseX, mouseY, HostAccessMode.INVITED, "gui.lanplus.host.access.invited",
                cardX + PAD + 2 * (chipW + 6), cardW - 2 * PAD - 2 * (chipW + 6));

        Component premium = Component.translatable("gui.lanplus.host.nonpremium", Component.translatable(
                allowNonPremium ? "gui.lanplus.host.nonpremium.on" : "gui.lanplus.host.nonpremium.off"));
        boolean hover = in(mouseX, mouseY, cardX + PAD, premiumRowY, cardW - 2 * PAD, DROPDOWN_H);
        LanPlusUi.chip(g, this.font, premium, cardX + PAD, premiumRowY, cardW - 2 * PAD, DROPDOWN_H,
                allowNonPremium, true, hover);
        if (hover) {
            g.renderTooltip(this.font, Component.translatable("gui.lanplus.host.nonpremium.tip"), mouseX, mouseY);
        }
    }

    private void renderModeChip(GuiGraphics g, int mouseX, int mouseY, HostAccessMode mode, String key,
                                int x, int w) {
        boolean enabled = !allowNonPremium;
        boolean hover = enabled && in(mouseX, mouseY, x, accessRowY, w, DROPDOWN_H);
        LanPlusUi.chip(g, this.font, Component.translatable(key), x, accessRowY, w, DROPDOWN_H,
                accessMode == mode, enabled, hover);
    }

    private static <T> int indexOf(T value, T[] values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private static String gameTypeKey(GameType gt) {
        return switch (gt) {
            case CREATIVE -> "selectWorld.gameMode.creative";
            case ADVENTURE -> "selectWorld.gameMode.adventure";
            case SPECTATOR -> "selectWorld.gameMode.spectator";
            default -> "gameMode.survival";
        };
    }

    private Component gameTypeLabel(GameType gt) {
        return Component.translatable(gameTypeKey(gt));
    }

    private static String difficultyKey(Difficulty d) {
        return switch (d) {
            case PEACEFUL -> "options.difficulty.peaceful";
            case EASY -> "options.difficulty.easy";
            case HARD -> "options.difficulty.hard";
            default -> "options.difficulty.normal";
        };
    }

    private Component difficultyLabel(Difficulty d) {
        return Component.translatable(difficultyKey(d));
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
                    g.fill(x0 + 1, Math.max(y, listTop), x1 - 1, Math.min(y + ROW_H, listBottom),
                            sel ? LanPlusUi.BLURPLE_TINT : 0x14FFFFFF);
                }
                if (sel) {
                    g.fill(x0 + 1, Math.max(y, listTop), x0 + 3, Math.min(y + ROW_H, listBottom),
                            LanPlusUi.BLURPLE);
                }
                LevelSummary s = worlds.get(i);
                FaviconTexture icon = icons.get(s.getLevelId());
                if (icon != null && y + 2 >= listTop && y + 2 + ICON <= listBottom) {
                    g.blit(icon.textureLocation(), x0 + 4, y + 2, ICON, ICON, 0.0F, 0.0F, 64, 64, 64, 64);
                }
                int textX = x0 + 8 + ICON + 6;
                if (y + 4 >= listTop && y + 12 <= listBottom) {
                    g.drawString(this.font, s.getLevelName(), textX, y + 4, LanPlusUi.TEXT, false);
                }
                if (y + 14 >= listTop && y + 22 <= listBottom) {
                    g.drawString(this.font, s.getLevelId(), textX, y + 14, LanPlusUi.FAINT, false);
                }
            }
            y += ROW_H;
        }
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!inWorld && in(mouseX, mouseY, cardX + PAD, listTop, cardW - 2 * PAD, listBottom - listTop)) {
            int max = Math.max(0, worlds.size() * ROW_H + 4 - (listBottom - listTop));
            listScroll = Math.max(0, Math.min(max, listScroll - (int) (delta * ROW_H)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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
                        layoutButtons();
                    }
                    return true;
                }
            }
        }
        return handleSharedClick(mouseX, mouseY, button);
    }

    private boolean handleSettingClick(double mouseX, double mouseY) {
        if (gameTypeOpen && in(mouseX, mouseY, ctrlX, gameRowY + DROPDOWN_H, ctrlW,
                GAME_TYPES.length * ITEM_H + 4)) {
            int idx = (int) ((mouseY - (gameRowY + DROPDOWN_H + 2)) / ITEM_H);
            if (idx >= 0 && idx < GAME_TYPES.length) {
                gameType = GAME_TYPES[idx];
            }
            gameTypeOpen = false;
            return true;
        }
        if (difficultyOpen && in(mouseX, mouseY, ctrlX, diffRowY + DROPDOWN_H, ctrlW,
                DIFFICULTIES.length * ITEM_H + 4)) {
            int idx = (int) ((mouseY - (diffRowY + DROPDOWN_H + 2)) / ITEM_H);
            if (idx >= 0 && idx < DIFFICULTIES.length) {
                difficulty = DIFFICULTIES[idx];
            }
            difficultyOpen = false;
            return true;
        }

        if (in(mouseX, mouseY, ctrlX, gameRowY, ctrlW, DROPDOWN_H)) {
            gameTypeOpen = !gameTypeOpen;
            difficultyOpen = false;
            return true;
        }
        if (in(mouseX, mouseY, ctrlX, cmdRowY, ctrlW, DROPDOWN_H)) {
            allowCheats = !allowCheats;
            return true;
        }
        if (in(mouseX, mouseY, ctrlX, diffRowY, ctrlW, DROPDOWN_H)) {
            difficultyOpen = !difficultyOpen;
            gameTypeOpen = false;
            return true;
        }
        return false;
    }

    private boolean handleSharedClick(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (!allowNonPremium && in(mouseX, mouseY, cardX + PAD, accessRowY, cardW - 2 * PAD, DROPDOWN_H)) {
                int chipW = (cardW - 2 * PAD - 2 * 6) / 3;
                if (mouseX < cardX + PAD + chipW) {
                    accessMode = HostAccessMode.EVERYONE;
                } else if (mouseX < cardX + PAD + 2 * chipW + 6) {
                    accessMode = HostAccessMode.FRIENDS;
                } else {
                    accessMode = HostAccessMode.INVITED;
                }
                return true;
            }
            if (in(mouseX, mouseY, cardX + PAD, premiumRowY, cardW - 2 * PAD, DROPDOWN_H)) {
                allowNonPremium = !allowNonPremium;
                if (allowNonPremium) {
                    accessMode = HostAccessMode.EVERYONE;
                    gameTypeOpen = false;
                    difficultyOpen = false;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private void loadWorlds() {
        LevelStorageSource source = this.minecraft.getLevelSource();
        try {
            source.loadLevelSummaries(source.findLevelCandidates()).thenAccept(list ->
                    this.minecraft.execute(() -> {
                        List<LevelSummary> usable = new ArrayList<>();
                        for (LevelSummary s : list) {
                            if (!s.isDisabled()) {
                                usable.add(s);
                                loadIcon(s);
                            }
                        }
                        this.worlds = usable;
                        this.loading = false;
                        rebuildWidgets();
                    }));
        } catch (LevelStorageException e) {
            this.worlds = List.of();
            this.loading = false;
        }
    }

    private void loadIcon(LevelSummary summary) {
        Path iconFile = summary.getIcon();
        if (iconFile == null || !Files.isRegularFile(iconFile)) {
            return;
        }
        FaviconTexture tex = FaviconTexture.forWorld(this.minecraft.getTextureManager(), summary.getLevelId());
        try (InputStream in = Files.newInputStream(iconFile)) {
            NativeImage image = NativeImage.read(in);
            if (image.getWidth() == 64 && image.getHeight() == 64) {
                tex.upload(image);
                icons.put(summary.getLevelId(), tex);
            } else {
                image.close();
                tex.close();
            }
        } catch (Throwable t) {
            tex.close();
        }
    }

    @Override
    public void removed() {
        for (FaviconTexture tex : icons.values()) {
            tex.close();
        }
        icons.clear();
    }

    private void doStart() {
        if (inWorld) {
            PauseMenuButtons.markHostedInWorld();
            HostController.requestHost(new HostController.HostSettings(
                    accessMode, Set.of(), allowNonPremium, gameType, difficulty, allowCheats));
            onClose();
            return;
        }
        LevelSummary world = selected >= 0 && selected < worlds.size() ? worlds.get(selected) : null;
        if (world == null) {
            return;
        }
        if (accessMode == HostAccessMode.INVITED) {
            this.minecraft.setScreen(new InviteOverlayScreen(this, world, accessMode));
        } else {
            HostController.requestHost(accessMode, Set.of(), allowNonPremium);
            this.minecraft.createWorldOpenFlows().loadLevel(this, world.getLevelId());
        }
    }
}