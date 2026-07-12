package dev.bgame.lanplus.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bgame.lanplus.api.HostAccessMode;
import dev.bgame.lanplus.client.HostController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
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

/**
 * "Host a World" — pick one of your singleplayer worlds, choose who can join, and open it to LAN+
 * (Essential-style). Opened from the small button next to Singleplayer on the title screen.
 * Inviting specific friends is done from the friends overlay (O); this screen only picks the mode.
 * Laid out as a compact centered card in the shared LAN+ style (see {@link LanPlusUi}).
 */
public final class HostScreen extends Screen {

    private static final int CARD_W = 320;
    private static final int ROW_H = 24;
    private static final int PAD = 10;
    private static final int ICON = ROW_H - 4;

    private final Screen parent;
    private final Map<String, FaviconTexture> icons = new HashMap<>();
    private List<LevelSummary> worlds = List.of();
    private boolean loading = true;
    private int selected = -1;
    private int listScroll;
    private HostAccessMode accessMode = HostAccessMode.FRIENDS;
    private boolean allowNonPremium = false;

    private int cardX, cardY, cardW, cardH;
    private int listTop, listBottom;
    private int chipsY, premiumY, buttonsY;

    public HostScreen(Screen parent) {
        super(Component.translatable("gui.lanplus.host.title"));
        this.parent = parent;
    }

    private void layout() {
        cardW = Math.min(this.width - 40, CARD_W);
        int listRows = Math.max(3, Math.min(visibleRowsWanted(), (this.height - 190) / ROW_H));
        int listH = listRows * ROW_H + 4;
        cardH = 24 + listH + 10 + 16 + 20 + 8 + 20 + 10 + 20 + 2 * PAD;
        cardX = (this.width - cardW) / 2;
        cardY = Math.max(16, (this.height - cardH) / 2);
        listTop = cardY + PAD + 24;
        listBottom = listTop + listH;
        chipsY = listBottom + 10 + 16;
        premiumY = chipsY + 20 + 8;
        buttonsY = premiumY + 20 + 10;
    }

    private int visibleRowsWanted() {
        return loading || worlds.isEmpty() ? 4 : Math.min(worlds.size(), 6);
    }

    @Override
    protected void init() {
        if (loading) {
            loadWorlds();
        } else if (icons.isEmpty() && !worlds.isEmpty()) {
            for (LevelSummary s : worlds) {
                loadIcon(s);
            }
        }
        layout();

        Button hostButton = Button.builder(Component.translatable("gui.lanplus.host.start"), b -> doHost())
                .bounds(cardX + PAD, buttonsY, (cardW - 2 * PAD - 6) / 2 + 30, 20).build();
        hostButton.active = selected >= 0;
        addRenderableWidget(hostButton);
        int cancelW = cardW - 2 * PAD - 6 - ((cardW - 2 * PAD - 6) / 2 + 30);
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(cardX + cardW - PAD - cancelW, buttonsY, cancelW, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        LanPlusUi.backdrop(g, this.width, this.height);
        layout();

        LanPlusUi.panel(g, cardX, cardY, cardX + cardW, cardY + cardH);
        LanPlusUi.header(g, this.font, this.title, cardX + PAD, cardY + PAD, cardW - 2 * PAD);

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

        g.drawString(this.font, Component.translatable("gui.lanplus.host.access"),
                cardX + PAD, chipsY - 14, LanPlusUi.MUTED, false);
        int chipW = (cardW - 2 * PAD - 2 * 6) / 3;
        renderModeChip(g, mouseX, mouseY, HostAccessMode.EVERYONE, "gui.lanplus.host.access.everyone",
                cardX + PAD, chipW);
        renderModeChip(g, mouseX, mouseY, HostAccessMode.FRIENDS, "gui.lanplus.host.access.friends",
                cardX + PAD + chipW + 6, chipW);
        renderModeChip(g, mouseX, mouseY, HostAccessMode.INVITED, "gui.lanplus.host.access.invited",
                cardX + PAD + 2 * (chipW + 6), cardW - 2 * PAD - 2 * (chipW + 6));

        Component premium = Component.translatable("gui.lanplus.host.nonpremium", Component.translatable(
                allowNonPremium ? "gui.lanplus.host.nonpremium.on" : "gui.lanplus.host.nonpremium.off"));
        boolean hover = in(mouseX, mouseY, cardX + PAD, premiumY, cardW - 2 * PAD, 20);
        LanPlusUi.chip(g, this.font, premium, cardX + PAD, premiumY, cardW - 2 * PAD, 20,
                allowNonPremium, true, hover);
        if (hover) {
            g.renderTooltip(this.font, Component.translatable("gui.lanplus.host.nonpremium.tip"), mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderModeChip(GuiGraphics g, int mouseX, int mouseY, HostAccessMode mode, String key,
                                int x, int w) {
        boolean enabled = !allowNonPremium;
        boolean hover = enabled && in(mouseX, mouseY, x, chipsY, w, 20);
        LanPlusUi.chip(g, this.font, Component.translatable(key), x, chipsY, w, 20,
                accessMode == mode, enabled, hover);
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
        if (in(mouseX, mouseY, cardX + PAD, listTop, cardW - 2 * PAD, listBottom - listTop)) {
            int max = Math.max(0, worlds.size() * ROW_H + 4 - (listBottom - listTop));
            listScroll = Math.max(0, Math.min(max, listScroll - (int) (delta * ROW_H)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (!loading && in(mouseX, mouseY, cardX + PAD, listTop, cardW - 2 * PAD, listBottom - listTop)) {
                int idx = (int) ((mouseY - (listTop + 2 - listScroll)) / ROW_H);
                if (idx >= 0 && idx < worlds.size()) {
                    selected = idx;
                    rebuildWidgets();
                }
                return true;
            }
            int chipW = (cardW - 2 * PAD - 2 * 6) / 3;
            if (!allowNonPremium && in(mouseX, mouseY, cardX + PAD, chipsY, cardW - 2 * PAD, 20)) {
                if (mouseX < cardX + PAD + chipW) {
                    accessMode = HostAccessMode.EVERYONE;
                } else if (mouseX < cardX + PAD + 2 * chipW + 6) {
                    accessMode = HostAccessMode.FRIENDS;
                } else {
                    accessMode = HostAccessMode.INVITED;
                }
                return true;
            }
            if (in(mouseX, mouseY, cardX + PAD, premiumY, cardW - 2 * PAD, 20)) {
                allowNonPremium = !allowNonPremium;
                if (allowNonPremium) {
                    accessMode = HostAccessMode.EVERYONE;
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
                tex.upload(image); // takes ownership of the NativeImage
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

    private void doHost() {
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
