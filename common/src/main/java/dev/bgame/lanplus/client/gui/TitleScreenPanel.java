package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.LanplusCommon;
import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.client.SkinTextures;
import dev.bgame.lanplus.cosmetics.CosmeticSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TitleScreenPanel {

    private static final ResourceLocation FRIENDS_ICON = icon("friends");
    private static final ResourceLocation PROFILE_ICON = icon("profile");
    private static final ResourceLocation ANNOUNCEMENTS_ICON = icon("announcements");
    private static final ResourceLocation SETTINGS_ICON = icon("settings");

    private static final int MARGIN = 50;
    private static final int VMARGIN = 8;
    private static final int MENU_GAP = 12;
    private static final int PAD = 10;
    private static final int PANEL_W = 168;
    private static final int WORDMARK_H = 12;
    private static final int IDENTITY_H = 26;
    private static final int HOST_H = 24;
    private static final int GRID_H = 28;
    private static final int GRID_GAP = 6;
    private static final int CONTENT_H = PAD + WORDMARK_H + 8 + IDENTITY_H + 10 + HOST_H + 8 + (2 * GRID_H + GRID_GAP) + PAD;

    private static final int STAGE_W = 150;
    private static final int STAGE_RENDER_H = 140;
    private static final int STAGE_SLOT = 22;
    private static final int STAGE_SLOT_GAP = 6;
    private static final int STAGE_PER_ROW = 4;
    private static final int STAGE_ROWS = (CosmeticSlot.values().length + STAGE_PER_ROW - 1) / STAGE_PER_ROW;
    private static final int STAGE_SLOTS_H = STAGE_ROWS * STAGE_SLOT + (STAGE_ROWS - 1) * STAGE_SLOT_GAP;
    private static final int STAGE_BUTTON_H = 22;
    private static final int STAGE_CONTENT_H = PAD + 12 + 8 + STAGE_RENDER_H + 8 + 10 + 4 + STAGE_SLOTS_H + 8 + STAGE_BUTTON_H + PAD;

    private static final List<Hit> hits = new ArrayList<>();
    private static Component tooltip;
    private static int tooltipX;
    private static int tooltipY;
    private static int rawMouseX;
    private static int rawMouseY;
    private static float curScale = 1f;
    private static int curOX;
    private static int curOY;

    private TitleScreenPanel() {
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(LanplusCommon.MODID, "textures/gui/" + name + ".png");
    }

    public static void onScreenRender(GuiGraphics g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TitleScreen)) {
            return;
        }
        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int cx = screenW / 2;
        int menuLeft = cx - 100 - MENU_GAP;
        int menuRight = cx + 100 + MENU_GAP;

        hits.clear();
        tooltip = null;
        rawMouseX = mouseX;
        rawMouseY = mouseY;

        int leftRoom = menuLeft - 4;
        float ps = Math.min(1f, leftRoom / (float) PANEL_W);
        if (ps >= 0.5f) {
            int drawnW = Math.round(PANEL_W * ps);
            int x0 = Math.max(4, Math.min(MARGIN, menuLeft - drawnW));
            Rect r = layout(x0, PANEL_W, CONTENT_H, ps);
            beginScaled(g, r.x0(), r.y0(), ps);
            drawPanel(g, font, r, amX(mouseX), amY(mouseY));
            endScaled(g);
        }

        int rightRoom = screenW - 4 - menuRight;
        float ss = Math.min(1f, rightRoom / (float) STAGE_W);
        if (ss >= 0.5f) {
            int drawnW = Math.round(STAGE_W * ss);
            int x0 = Math.max(menuRight, screenW - MARGIN - drawnW);
            Rect r = layout(x0, STAGE_W, STAGE_CONTENT_H, ss);
            beginScaled(g, r.x0(), r.y0(), ss);
            drawStage(g, font, r, amX(mouseX), amY(mouseY));
            endScaled(g);
        }

        if (tooltip != null) {
            drawTooltip(g, font, tooltip, tooltipX, tooltipY);
        }
    }

    private static void beginScaled(GuiGraphics g, int ox, int oy, float s) {
        curScale = s;
        curOX = ox;
        curOY = oy;
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(s, s, 1f);
        g.pose().translate(-ox, -oy, 0);
    }

    private static void endScaled(GuiGraphics g) {
        g.pose().popPose();
        curScale = 1f;
        curOX = 0;
        curOY = 0;
    }

    private static int amX(int mouseX) {
        return curOX + Math.round((mouseX - curOX) / curScale);
    }

    private static int amY(int mouseY) {
        return curOY + Math.round((mouseY - curOY) / curScale);
    }

    private static int screenX(int x) {
        return curOX + Math.round((x - curOX) * curScale);
    }

    private static int screenY(int y) {
        return curOY + Math.round((y - curOY) * curScale);
    }

    private static void addHit(int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(screenX(x), screenY(y), Math.round(w * curScale), Math.round(h * curScale), action));
    }

    private static void drawPanel(GuiGraphics g, Font font, Rect r, int mouseX, int mouseY) {
        int innerX = r.innerX();
        int innerW = r.innerW();
        LanPlusUI.panel(g, r.x0(), r.y0(), r.x1(), r.y1());
        LanPlusUI.rivets(g, r.x0(), r.y0(), r.x1(), r.y1(), LanPlusUI.FAINT);

        int cy = r.y0() + PAD;

        drawWordmark(g, font, innerX, cy);
        cy += WORDMARK_H + 8;

        drawIdentity(g, font, innerX, cy, innerW);
        cy += IDENTITY_H + 10;

        boolean hostHover = inside(mouseX, mouseY, innerX, cy, innerW, HOST_H);
        drawHost(g, font, innerX, cy, innerW, HOST_H, hostHover);
        addHit(innerX, cy, innerW, HOST_H, () -> open(new HostScreen(title())));
        cy += HOST_H + 8;

        int cellW = (innerW - GRID_GAP) / 2;
        int col2 = innerX + cellW + GRID_GAP;

        drawCell(g, font, innerX, cy, cellW, mouseX, mouseY, FRIENDS_ICON, Component.translatable("gui.lanplus.friends.word"), 0, () -> open(new FriendsScreen(title())));
        drawCell(g, font, col2, cy, cellW, mouseX, mouseY, PROFILE_ICON, Component.translatable("gui.lanplus.profile.word"), 0, () -> {
            UUID id = LanPlusClient.selfUuid();
            if (id != null) {
                open(new ProfileScreen(title(), id));
            }
        });
        cy += GRID_H + GRID_GAP;

        int unseen = LanPlusClient.announcements() == null ? 0 : LanPlusClient.announcements().unseenCount();
        drawCell(g, font, innerX, cy, cellW, mouseX, mouseY, ANNOUNCEMENTS_ICON, Component.translatable("gui.lanplus.menu.news"), unseen, () -> open(new Announcements(title())));
        drawCell(g, font, col2, cy, cellW, mouseX, mouseY, SETTINGS_ICON, Component.translatable("gui.lanplus.settings.word"), 0, () -> open(new SettingsScreen(title())));
    }

    public static boolean onMouseClick(double mx, double my, int button) {
        if (button != 0 || !(Minecraft.getInstance().screen instanceof TitleScreen)) {
            return false;
        }
        for (Hit h : hits) {
            if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) {
                h.action.run();
                return true;
            }
        }
        return false;
    }

    private static void drawIdentity(GuiGraphics g, Font font, int x, int y, int w) {
        User user = Minecraft.getInstance().getUser();
        String name = user == null ? "Player" : user.getName();

        int face = 24;
        int fy = y + (IDENTITY_H - face) / 2;
        PlayerFaceRenderer.draw(g, resolveSelf().texture(), x, fy, face);

        int tx = x + face + 8;
        g.drawString(font, ellipsize(font, name, w - face - 8), tx, y + 2, LanPlusUI.TEXT, false);

        boolean connected = LanPlusClient.selfUuid() != null;
        Component status = connected ? Component.translatable("gui.lanplus.status.connected") : Component.translatable("gui.lanplus.menu.connecting");
        int dot = connected ? LanPlusUI.ONLINE : LanPlusUI.AMBER;
        g.drawString(font, "+", tx, y + 15, dot, false);
        g.drawString(font, status, tx + font.width("+") + 4, y + 15, LanPlusUI.MUTED, false);
    }

    private static void drawWordmark(GuiGraphics g, Font font, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.drawString(font, "LAN", 0, 0, LanPlusUI.LAVENDER, true);
        g.drawString(font, "+", font.width("LAN"), 0, LanPlusUI.LIME, true);
        g.pose().popPose();
    }

    private static void drawHost(GuiGraphics g, Font font, int x, int y, int w, int h, boolean hover) {
        LanPlusUI.primaryButton(g, x, y, x + w, y + h, hover, true);
        Component label = Component.translatable("gui.lanplus.menu.hostworld");
        int plusW = font.width("+");
        int total = plusW + 5 + font.width(label);
        int sx = x + (w - total) / 2;
        int ty = y + (h - 8) / 2;
        g.drawString(font, "+", sx, ty, LanPlusUI.LIME, true);
        g.drawString(font, label, sx + plusW + 5, ty, LanPlusUI.TEXT, true);
    }

    private static void drawCell(GuiGraphics g, Font font, int x, int y, int w, int mouseX, int mouseY, ResourceLocation ic, Component label, int badge, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, w, GRID_H);
        LanPlusUI.button3d(g, x, y, x + w, y + GRID_H, hover ? LanPlusUI.SURFACE_HOVER : LanPlusUI.SURFACE_RAISED);
        int ix = x + 6;
        int iy = y + (GRID_H - 16) / 2;
        g.blit(ic, ix, iy, 0, 0, 16, 16, 16, 16);
        g.drawString(font, ellipsize(font, label.getString(), w - 22 - 6), ix + 16 + 5, y + (GRID_H - 8) / 2, hover ? LanPlusUI.TEXT : LanPlusUI.MUTED, true);
        if (badge > 0) {
            drawBadge(g, font, x + w - 4, y + 2, badge);
        }
        addHit(x, y, w, GRID_H, action);
    }

    private static void drawStage(GuiGraphics g, Font font, Rect r, int mouseX, int mouseY) {
        int innerX = r.innerX();
        int innerW = r.innerW();

        LanPlusUI.panel(g, r.x0(), r.y0(), r.x1(), r.y1());
        LanPlusUI.rivets(g, r.x0(), r.y0(), r.x1(), r.y1(), LanPlusUI.FAINT);

        int cy = r.y0() + PAD;
        Component title = Component.translatable("gui.lanplus.menu.cosmetics");
        g.drawString(font, "+", innerX, cy, LanPlusUI.LIME, false);
        g.drawString(font, title, innerX + font.width("+") + 4, cy, LanPlusUI.TEXT, false);
        g.fill(innerX, cy + 11, innerX + innerW, cy + 12, LanPlusUI.BORDER);
        cy += 12 + 8;

        int boxX = innerX;
        int boxY = cy;
        int boxW = innerW;
        int boxBottom = boxY + STAGE_RENDER_H;
        g.fill(boxX, boxY, boxX + boxW, boxBottom, LanPlusUI.SLOT);
        LanPlusUI.outline(g, boxX, boxY, boxX + boxW, boxBottom, LanPlusUI.BORDER);
        cornerTicks(g, boxX, boxY, boxX + boxW, boxBottom, LanPlusUI.FAINT);

        int cxModel = boxX + boxW / 2;
        int feetY = boxBottom - 18;
        float yaw = clamp((cxModel - mouseX) * 0.45f, -45f, 45f);
        float pitch = clamp(((boxY + (float) STAGE_RENDER_H / 2) - mouseY) * 0.25f, -25f, 25f);

        SelfSkin self = resolveSelf();
        g.enableScissor(screenX(boxX + 1), screenY(boxY + 1), screenX(boxX + boxW - 1), screenY(boxBottom - 1));
        drawPlusWatermark(g, font, cxModel, boxY + STAGE_RENDER_H / 2);
        drawFeetShadow(g, cxModel, feetY);
        PlayerPreview.render(g, cxModel, feetY, 44f, yaw, pitch, self.texture(), self.slim(), LanPlusClient.selfUuid());
        g.disableScissor();
        cy += STAGE_RENDER_H + 8;

        g.drawString(font, Component.translatable("gui.lanplus.menu.equipped"), innerX, cy, LanPlusUI.MUTED, false);
        cy += 10 + 4;

        CosmeticSlot[] slots = CosmeticSlot.values();
        int n = slots.length;
        for (int i = 0; i < n; i++) {
            int row = i / STAGE_PER_ROW;
            int col = i % STAGE_PER_ROW;
            int rowCount = Math.min(STAGE_PER_ROW, n - row * STAGE_PER_ROW);
            int rowW = rowCount * STAGE_SLOT + (rowCount - 1) * STAGE_SLOT_GAP;
            int rowX = innerX + (innerW - rowW) / 2;
            int bx = rowX + col * (STAGE_SLOT + STAGE_SLOT_GAP);
            int by = cy + row * (STAGE_SLOT + STAGE_SLOT_GAP);
            drawCosmeticSlot(g, font, bx, by, mouseX, mouseY, slots[i]);
        }
        cy += STAGE_SLOTS_H + 8;

        boolean editHover = inside(mouseX, mouseY, innerX, cy, innerW, STAGE_BUTTON_H);
        LanPlusUI.button3d(g, innerX, cy, innerX + innerW, cy + STAGE_BUTTON_H, editHover ? LanPlusUI.SURFACE_HOVER : LanPlusUI.SURFACE_RAISED);
        Component edit = Component.translatable("gui.lanplus.menu.editcosmetics");
        g.drawString(font, edit, innerX + (innerW - font.width(edit)) / 2, cy + (STAGE_BUTTON_H - 8) / 2, editHover ? LanPlusUI.TEXT : LanPlusUI.MUTED, true);
        addHit(innerX, cy, innerW, STAGE_BUTTON_H, () -> open(new CosmeticScreen(title())));
    }

    private static void drawTooltip(GuiGraphics g, Font font, Component text, int mx, int my) {
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int w = font.width(text);
        int x = mx + 10;
        int y = my - 12;
        if (x + w + 4 > screenW) {
            x = mx - w - 10;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x - 4, y - 4, x + w + 4, y + 12, 0xF00A0A10);
        LanPlusUI.outline1(g, x - 4, y - 4, x + w + 4, y + 12, LanPlusUI.ACCENT);
        g.drawString(font, text, x, y, LanPlusUI.TEXT, false);
        g.pose().popPose();
    }

    private static void drawCosmeticSlot(GuiGraphics g, Font font, int x, int y, int mouseX, int mouseY, CosmeticSlot slot) {
        boolean hover = inside(mouseX, mouseY, x, y, STAGE_SLOT, STAGE_SLOT);
        LanPlusUI.slot(g, x, y, x + STAGE_SLOT, y + STAGE_SLOT);
        int pw = font.width("+");
        g.drawString(font, "+", x + (STAGE_SLOT - pw) / 2, y + (STAGE_SLOT - 8) / 2, hover ? LanPlusUI.MUTED : LanPlusUI.FAINT, false);
        if (hover) {
            LanPlusUI.outline1(g, x, y, x + STAGE_SLOT, y + STAGE_SLOT, LanPlusUI.LAVENDER);
            tooltip = Component.translatable("gui.lanplus.menu.slot." + slot.name().toLowerCase(java.util.Locale.ROOT));
            tooltipX = rawMouseX;
            tooltipY = rawMouseY;
        }
        addHit(x, y, STAGE_SLOT, STAGE_SLOT, () -> open(new CosmeticScreen(title(), slot)));
    }

    private static void cornerTicks(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int t = 4;
        g.fill(x0, y0, x0 + t, y0 + 1, color);
        g.fill(x0, y0, x0 + 1, y0 + t, color);
        g.fill(x1 - t, y0, x1, y0 + 1, color);
        g.fill(x1 - 1, y0, x1, y0 + t, color);
        g.fill(x0, y1 - 1, x0 + t, y1, color);
        g.fill(x0, y1 - t, x0 + 1, y1, color);
        g.fill(x1 - t, y1 - 1, x1, y1, color);
        g.fill(x1 - 1, y1 - t, x1, y1, color);
    }

    private static void drawFeetShadow(GuiGraphics g, int cx, int feetY) {
        g.fill(cx - 10, feetY - 2, cx + 10, feetY - 1, 0x33000000);
        g.fill(cx - 14, feetY - 1, cx + 14, feetY, 0x44000000);
        g.fill(cx - 10, feetY, cx + 10, feetY + 1, 0x33000000);
    }

    private static void drawPlusWatermark(GuiGraphics g, Font font, int cx, int cy) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(6f, 6f, 1f);
        g.drawString(font, "+", -font.width("+") / 2, -4, 0x18C6CBD4, false);
        g.pose().popPose();
    }

    private static void drawBadge(GuiGraphics g, Font font, int rightX, int y, int count) {
        String s = count > 9 ? "9+" : Integer.toString(count);
        int w = font.width(s) + 6;
        int bx = rightX - w;
        g.fill(bx, y, bx + w, y + 10, LanPlusUI.LIME);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.drawString(font, s, bx + 3, y + 1, LanPlusUI.EDGE_DARK, false);
        g.pose().popPose();
    }

    private static SelfSkin resolveSelf() {
        UUID id = LanPlusClient.selfUuid();
        User user = Minecraft.getInstance().getUser();
        if (id == null && user != null) {
            id = user.getProfileId();
        }
        SkinTextures st = LanPlusClient.skinTextures();
        SkinTextures.Resolved res = st == null || id == null ? null : st.get(id);
        ResourceLocation tex = res != null ? res.texture() : DefaultPlayerSkin.get(id == null ? UUID.randomUUID() : id).texture();
        return new SelfSkin(tex, res != null && res.slim());
    }

    private static Component ellipsize(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return Component.literal(text);
        }
        return Component.literal(font.plainSubstrByWidth(text, maxWidth - font.width("…")) + "…");
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static Rect layout(int x0, int width, int contentH, float s) {
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int visH = Math.round(contentH * s);
        int y0 = Math.max(VMARGIN, (screenH - visH) / 2);
        if (y0 + visH > screenH - VMARGIN) {
            y0 = Math.max(VMARGIN, screenH - VMARGIN - visH);
        }
        return new Rect(x0, y0, x0 + width, y0 + contentH, x0 + PAD, width - 2 * PAD);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static TitleScreen title() {
        return (TitleScreen) Minecraft.getInstance().screen;
    }

    private static void open(Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    private record Hit(int x, int y, int w, int h, Runnable action) {
    }

    private record SelfSkin(ResourceLocation texture, boolean slim) {
    }

    private record Rect(int x0, int y0, int x1, int y1, int innerX, int innerW) {
    }
}
