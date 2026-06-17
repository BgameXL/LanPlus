package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.api.Friend;
import dev.bgame.lanplus.api.HostAccessMode;
import dev.bgame.lanplus.client.HostController;
import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.friends.FriendsService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelSummary;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Small overlay shown after picking a world to host with FRIENDS/INVITED access: tick the friends to
 * pre-admit, then "Host now". Optional — closing it (Esc or the X) still hosts the world (Essential-style).
 */
public final class InviteOverlayScreen extends Screen {

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 200;
    private static final int ROW_H = 18;

    private final Screen parent;
    private final LevelSummary world;
    private final HostAccessMode mode;
    private final Set<UUID> picked = new HashSet<>();
    private boolean launched;

    private int panelX;
    private int panelY;

    public InviteOverlayScreen(Screen parent, LevelSummary world, HostAccessMode mode) {
        super(Component.translatable("gui.lanplus.invite.title"));
        this.parent = parent;
        this.world = world;
        this.mode = mode;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;
        addRenderableWidget(Button.builder(Component.literal("X"), b -> hostNow())
                .bounds(panelX + PANEL_W - 18, panelY + 4, 14, 14).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.invite.hostnow"), b -> hostNow())
                .bounds(panelX + PANEL_W / 2 - 50, panelY + PANEL_H - 26, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xF0101018);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + 22, 0xFF1B1B26);
        g.drawString(this.font, this.title, panelX + 8, panelY + 7, 0xFFFFFFFF);

        List<Friend> friends = friends();
        int listTop = panelY + 28;
        int listBottom = panelY + PANEL_H - 32;
        if (friends.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.invite.nofriends"),
                    panelX + PANEL_W / 2, listTop + 20, 0xFF888888);
        } else {
            int y = listTop;
            for (Friend f : friends) {
                if (y + ROW_H > listBottom) {
                    break;
                }
                boolean on = picked.contains(f.uuid());
                boolean hover = mouseX >= panelX + 6 && mouseX <= panelX + PANEL_W - 6 && mouseY >= y && mouseY < y + ROW_H;
                if (hover) {
                    g.fill(panelX + 6, y, panelX + PANEL_W - 6, y + ROW_H, 0x20FFFFFF);
                }
                int bx = panelX + 10;
                int by = y + 5;
                g.fill(bx, by, bx + 9, by + 9, 0xFF555560);
                g.fill(bx + 1, by + 1, bx + 8, by + 8, on ? 0xFF43B581 : 0xFF1B1B26);
                g.drawString(this.font, f.username(), panelX + 26, y + 5, 0xFFFFFFFF);
                y += ROW_H;
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int y = panelY + 28;
            int listBottom = panelY + PANEL_H - 32;
            for (Friend f : friends()) {
                if (y + ROW_H > listBottom) {
                    break;
                }
                if (mouseX >= panelX + 6 && mouseX <= panelX + PANEL_W - 6 && mouseY >= y && mouseY < y + ROW_H) {
                    if (!picked.remove(f.uuid())) {
                        picked.add(f.uuid());
                    }
                    return true;
                }
                y += ROW_H;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        hostNow();
    }

    private void hostNow() {
        if (launched) {
            return;
        }
        launched = true;
        HostController.requestHost(mode, picked, false); // premium path: keep online-mode + uuid gate
        this.minecraft.createWorldOpenFlows().loadLevel(parent, world.getLevelId());
    }

    private List<Friend> friends() {
        FriendsService friends = LanPlusClient.friends();
        return friends == null ? List.of() : friends.friends();
    }
}
