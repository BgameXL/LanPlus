package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.api.HostAccessMode;
import dev.bgame.lanplus.client.HostController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * "Host a World" — pick one of your singleplayer worlds, choose who can join, and open it to LAN+
 * (Essential-style). Opened from the small button next to Singleplayer on the title screen.
 * Inviting specific friends is done from the friends overlay (O); this screen only picks the mode.
 */
public final class
HostScreen extends Screen {

    private static final int PANEL_BG = 0xC0101018;
    private static final int MARGIN = 20;
    private static final int CONTENT_TOP = 40;
    private static final int ROW_H = 24;

    private final Screen parent;
    private List<LevelSummary> worlds = List.of();
    private boolean loading = true;
    private int selected = -1;
    private HostAccessMode accessMode = HostAccessMode.FRIENDS;
    private boolean allowNonPremium = false;

    public HostScreen(Screen parent) {
        super(Component.translatable("gui.lanplus.host.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (loading) {
            loadWorlds();
        }
        int modeY = this.height - 78;
        int x = MARGIN;
        addRenderableWidget(modeButton("gui.lanplus.host.access.everyone", HostAccessMode.EVERYONE, x, modeY));
        addRenderableWidget(modeButton("gui.lanplus.host.access.friends", HostAccessMode.FRIENDS, x + 118, modeY));
        addRenderableWidget(modeButton("gui.lanplus.host.access.invited", HostAccessMode.INVITED, x + 236, modeY));
        addRenderableWidget(nonPremiumButton(x, this.height - 52));

        Button hostButton = Button.builder(Component.translatable("gui.lanplus.host.start"), b -> doHost())
                .bounds(this.width - MARGIN - 200, this.height - 28, 96, 20).build();
        hostButton.active = selected >= 0;
        addRenderableWidget(hostButton);
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(this.width - MARGIN - 100, this.height - 28, 96, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawString(this.font, this.title, MARGIN, 14, 0xFFFFFFFF);

        int listBottom = this.height - 86;
        g.fill(MARGIN, CONTENT_TOP, this.width - MARGIN, listBottom, PANEL_BG);

        if (loading) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.host.loading"),
                    this.width / 2, CONTENT_TOP + 30, 0xFF888888);
        } else if (worlds.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.host.noworlds"),
                    this.width / 2, CONTENT_TOP + 30, 0xFF888888);
        } else {
            renderWorldList(g, mouseX, mouseY, listBottom);
        }

        g.drawString(this.font, Component.translatable("gui.lanplus.host.access"), MARGIN, this.height - 92, 0xFF9AA0A6);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderWorldList(GuiGraphics g, int mouseX, int mouseY, int listBottom) {
        int y = CONTENT_TOP + 2;
        for (int i = 0; i < worlds.size(); i++) {
            if (y + ROW_H > listBottom) {
                break;
            }
            boolean sel = i == selected;
            boolean hover = mouseX >= MARGIN && mouseX <= this.width - MARGIN && mouseY >= y && mouseY < y + ROW_H;
            if (sel || hover) {
                g.fill(MARGIN, y, this.width - MARGIN, y + ROW_H, sel ? 0x40FFFFFF : 0x20FFFFFF);
            }
            LevelSummary s = worlds.get(i);
            g.drawString(this.font, s.getLevelName(), MARGIN + 8, y + 4, 0xFFFFFFFF);
            g.drawString(this.font, s.getLevelId(), MARGIN + 8, y + 14, 0xFF9AA0A6);
            y += ROW_H;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !loading) {
            int listBottom = this.height - 86;
            int y = CONTENT_TOP + 2;
            for (int i = 0; i < worlds.size() && y + ROW_H <= listBottom; i++) {
                if (mouseX >= MARGIN && mouseX <= this.width - MARGIN && mouseY >= y && mouseY < y + ROW_H) {
                    selected = i;
                    rebuildWidgets();
                    return true;
                }
                y += ROW_H;
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
                            }
                        }
                        this.worlds = usable;
                        this.loading = false;
                    }));
        } catch (LevelStorageException e) {
            this.worlds = List.of();
            this.loading = false;
        }
    }

    private void doHost() {
        LevelSummary world = selected >= 0 && selected < worlds.size() ? worlds.get(selected) : null;
        if (world == null) {
            return;
        }
        if (accessMode == HostAccessMode.EVERYONE) {
            HostController.requestHost(accessMode, Set.of(), allowNonPremium);
            this.minecraft.createWorldOpenFlows().loadLevel(this, world.getLevelId());
        } else {
            this.minecraft.setScreen(new InviteOverlayScreen(this, world, accessMode));
        }
    }

    private Button modeButton(String key, HostAccessMode mode, int x, int y) {
        Button b = Button.builder(Component.translatable(key), btn -> {
            this.accessMode = mode;
            rebuildWidgets();
        }).bounds(x, y, 112, 20).build();
        b.active = !allowNonPremium && this.accessMode != mode;
        return b;
    }

    private Button nonPremiumButton(int x, int y) {
        String state = allowNonPremium ? "gui.lanplus.host.nonpremium.on" : "gui.lanplus.host.nonpremium.off";
        Button b = Button.builder(Component.translatable("gui.lanplus.host.nonpremium",
                Component.translatable(state)), btn -> {
            this.allowNonPremium = !this.allowNonPremium;
            if (this.allowNonPremium) {
                this.accessMode = HostAccessMode.EVERYONE; // offline-mode is open-world only (Phase 1)
            }
            rebuildWidgets();
        }).bounds(x, y, 230, 20).build();
        b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.lanplus.host.nonpremium.tip")));
        return b;
    }
}