package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.LanplusCommon;
import dev.bgame.lanplus.client.LanPlusClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
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
    private static final ResourceLocation COSMETICS_ICON = icon("cosmetics");

    private static final int RAIL_MARGIN = 8;
    private static final int VMARGIN = 8;
    private static final int BTN = 22;
    private static final int BTN_GAP = 5;
    private static final int ICON = 16;
    private static final int LABEL_PAD = 10;

    private static final List<Hit> hits = new ArrayList<>();

    private TitleScreenPanel() {
    }

    private record Btn(ResourceLocation ic, String glyph, int glyphColor, boolean primary,
                       Component label, int badge, Runnable action) {
    }

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(LanplusCommon.MODID, "textures/gui/" + name + ".png");
    }

    public static void onScreenRender(GuiGraphics g, int mouseX, int mouseY) {
        if (Minecraft.getInstance().screen instanceof TitleScreen) {
            render(g, mouseX, mouseY);
        }
    }

    static void render(GuiGraphics g, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenH = mc.getWindow().getGuiScaledHeight();

        hits.clear();

        List<Btn> buttons = buttons();
        int labelMax = 0;
        for (Btn b : buttons) {
            labelMax = Math.max(labelMax, font.width(b.label()));
        }
        int w = BTN + labelMax + LABEL_PAD;
        int contentH = buttons.size() * BTN + (buttons.size() - 1) * BTN_GAP;
        int x0 = RAIL_MARGIN;
        int y = Math.max(VMARGIN, (screenH - contentH) / 2);

        for (Btn b : buttons) {
            boolean hover = inside(mouseX, mouseY, x0, y, w, BTN);
            drawButton(g, font, x0, y, w, b, hover);
            addHit(x0, y, w, BTN, b.action());
            y += BTN + BTN_GAP;
        }
    }

    private static List<Btn> buttons() {
        int unseen = LanPlusClient.announcements() == null ? 0 : LanPlusClient.announcements().unseenCount();
        List<Btn> out = new ArrayList<>(6);
        out.add(new Btn(null, "+", LanPlusUI.LIME, true, Component.translatable("gui.lanplus.menu.hostworld"), 0,
                () -> open(new HostScreen(title()))));
        out.add(new Btn(FRIENDS_ICON, null, 0, false, Component.translatable("gui.lanplus.friends.word"), 0,
                () -> open(new FriendsScreen(title()))));
        out.add(new Btn(PROFILE_ICON, null, 0, false, Component.translatable("gui.lanplus.profile.word"), 0, () -> {
            UUID id = LanPlusClient.selfUuid();
            if (id != null) {
                open(new ProfileScreen(title(), id));
            }
        }));
        out.add(new Btn(COSMETICS_ICON, null, 0, false, Component.translatable("gui.lanplus.menu.cosmetics"), 0,
                () -> open(new CosmeticScreen(title()))));
        out.add(new Btn(ANNOUNCEMENTS_ICON, null, 0, false, Component.translatable("gui.lanplus.menu.news"), unseen,
                () -> open(new Announcements(title()))));
        out.add(new Btn(SETTINGS_ICON, null, 0, false, Component.translatable("gui.lanplus.settings.word"), 0,
                () -> open(new SettingsScreen(title()))));
        return out;
    }

    private static void drawButton(GuiGraphics g, Font font, int x, int y, int w, Btn b, boolean hover) {
        LanPlusUI.button3d(g, x, y, x + w, y + BTN, hover ? LanPlusUI.SURFACE_HOVER : LanPlusUI.SURFACE_RAISED);
        if (b.primary()) {
            LanPlusUI.outline1(g, x, y, x + w, y + BTN, LanPlusUI.LIME);
        }
        if (b.glyph() != null) {
            g.drawString(font, b.glyph(), x + (BTN - font.width(b.glyph())) / 2, y + (BTN - 8) / 2, b.glyphColor(), true);
        } else {
            g.blit(b.ic(), x + (BTN - ICON) / 2, y + (BTN - ICON) / 2, 0, 0, ICON, ICON, ICON, ICON);
        }
        g.drawString(font, b.label(), x + BTN, y + (BTN - 8) / 2, hover ? LanPlusUI.TEXT : LanPlusUI.MUTED, true);
        if (b.badge() > 0) {
            drawBadge(g, font, x + w - 2, y - 2, b.badge());
        }
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

    private static void addHit(int x, int y, int w, int h, Runnable action) {
        hits.add(new Hit(x, y, w, h, action));
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
}
