package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.LanplusCommon;
import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.client.SkinTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class LanPlusNotifications {

    private static final int W = 216;
    private static final int H = 50;
    private static final int MARGIN = 8;
    private static final int GAP = 6;
    private static final long RISE_MS = 220;
    private static final long FALL_MS = 220;
    private static final long HOLD_ACTION_MS = 9000;
    private static final long HOLD_INFO_MS = 5000;

    private static final ResourceLocation ANNOUNCE_ICON =
            ResourceLocation.fromNamespaceAndPath(LanplusCommon.MODID, "textures/gui/announcements.png");

    private static final CopyOnWriteArrayList<Notif> active = new CopyOnWriteArrayList<>();

    private LanPlusNotifications() {
    }

    public static void friendHosting(UUID friend, String name, String joinCode) {
        boolean invited = joinCode != null && !joinCode.isBlank();
        Component subtitle = Component.translatable(invited
                ? "gui.lanplus.notif.invitedyou" : "gui.lanplus.toast.hosting");
        Component action = invited ? Component.translatable("gui.lanplus.notif.view") : null;
        Runnable onAction = invited ? () -> LanPlusClient.openFriendsFocused(friend) : null;
        push(new Notif(friend, Component.literal(name), subtitle, action, onAction,
                invited ? HOLD_ACTION_MS : HOLD_INFO_MS, LanPlusUI.LIME, null, null));
    }

    public static void friendRequest(UUID from, String name) {
        push(new Notif(from, Component.literal(name),
                Component.translatable("gui.lanplus.toast.request"),
                Component.translatable("gui.lanplus.notif.accept"),
                () -> {
                    if (LanPlusClient.friends() != null) {
                        LanPlusClient.friends().accept(from);
                    }
                }, HOLD_ACTION_MS, LanPlusUI.LIME, null, null));
    }

    public static void info(Component title, Component subtitle) {
        push(new Notif(null, title, subtitle, null, null, HOLD_INFO_MS, LanPlusUI.ACCENT, "+", null));
    }

    public static void test(String title, String body) {
        push(new Notif(null,
                Component.literal(title == null || title.isBlank() ? "Test" : title),
                body == null || body.isBlank() ? null : Component.literal(body),
                null, null, HOLD_INFO_MS, LanPlusUI.ACCENT, "+", null));
    }

    public static void announcement(dev.bgame.lanplus.api.Announcement a) {
        if (a == null) {
            return;
        }
        push(new Notif(null, Component.literal(a.title()),
                Component.translatable("gui.lanplus.toast.announcement"),
                Component.translatable("gui.lanplus.notif.view"),
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.setScreen(new Announcements(mc.screen));
                }, HOLD_ACTION_MS, announcementTint(a.type()), null, ANNOUNCE_ICON));
    }

    private static int announcementTint(dev.bgame.lanplus.api.Announcement.Type type) {
        return switch (type) {
            case UPDATE -> 0xFF55FF55;
            case MAINTENANCE -> 0xFFFFFF55;
            case GENERAL -> 0xFF55FFFF;
            default -> LanPlusUI.LIME;
        };
    }

    private static void push(Notif n) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        mc.execute(() -> {
            active.add(n);
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_IN, 1.0F));
        });
    }

    public static void onRenderGui(GuiGraphics g) {
        renderAll(g, -1, -1);
    }

    public static void onScreenRender(GuiGraphics g, double mouseX, double mouseY) {
        if (hiddenOnCurrentScreen()) {
            return;
        }
        renderAll(g, mouseX, mouseY);
    }

    public static boolean onMouseClick(double mouseX, double mouseY, int button) {
        if (hiddenOnCurrentScreen()) {
            return false;
        }
        return button == 0 && handleClick(mouseX, mouseY);
    }

    private static boolean hiddenOnCurrentScreen() {
        Screen s = Minecraft.getInstance().screen;
        return s instanceof OptionsScreen || s instanceof OptionsSubScreen;
    }

    private static void renderAll(GuiGraphics g, double mouseX, double mouseY) {
        if (active.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();
        List<Notif> dead = new ArrayList<>();

        int slot = 0;
        for (int i = active.size() - 1; i >= 0; i--) {
            Notif n = active.get(i);
            long age = now - n.bornAt;
            float alpha;
            float slide;
            if (n.dismissed) {
                float f = clamp01((now - n.dismissedAt) / (float) FALL_MS);
                alpha = 1 - f;
                slide = f;
                if (f >= 1f) {
                    dead.add(n);
                    continue;
                }
            } else if (age < RISE_MS) {
                float t = age / (float) RISE_MS;
                alpha = t;
                slide = 1 - t;
            } else if (age >= RISE_MS + n.hold) {
                n.dismissed = true;
                n.dismissedAt = now;
                alpha = 1f;
                slide = 0f;
            } else {
                alpha = 1f;
                slide = 0f;
            }
            int x = screenW - MARGIN - W + (int) (slide * (W + MARGIN));
            int y = MARGIN + slot * (H + GAP);
            renderOne(g, n, x, y, alpha, mouseX, mouseY);
            slot++;
        }
        active.removeAll(dead);
    }

    private static void renderOne(GuiGraphics g, Notif n, int x, int y, float alpha, double mx, double my) {
        n.x = x;
        n.y = y;
        LanPlusUI.outline1(g, x, y, x + W, y + H, col(LanPlusUI.EDGE_DARK, alpha));
        g.fill(x + 1, y + 1, x + W - 1, y + H - 1, col(LanPlusUI.SURFACE, alpha * 0.96f));
        g.fill(x + 1, y + 1, x + W - 1, y + 2, col(LanPlusUI.shade(LanPlusUI.SURFACE, 1.6f), alpha * 0.6f));
        g.fill(x, y, x + 3, y + H, col(n.tint, alpha));

        Font font = Minecraft.getInstance().font;
        int textX = x + 12;
        int iconY = y + (H - 24) / 2;
        if (n.avatar != null) {
            SkinTextures textures = LanPlusClient.skinTextures();
            SkinTextures.Resolved resolved = textures == null ? null : textures.get(n.avatar);
            ResourceLocation tex = resolved != null ? resolved.texture() : DefaultPlayerSkin.get(n.avatar).texture();
            g.setColor(1f, 1f, 1f, alpha);
            PlayerFaceRenderer.draw(g, tex, x + 10, iconY, 24);
            g.setColor(1f, 1f, 1f, 1f);
            textX = x + 42;
        } else if (n.icon != null || n.glyph != null) {
            int ix = x + 10;
            g.fill(ix, iconY, ix + 24, iconY + 24, col(LanPlusUI.SURFACE_RAISED, alpha));
            LanPlusUI.outline1(g, ix, iconY, ix + 24, iconY + 24, col(LanPlusUI.EDGE_DARK, alpha));
            if (n.icon != null) {
                g.setColor(1f, 1f, 1f, alpha);
                g.blit(n.icon, ix + 4, iconY + 4, 0, 0, 16, 16, 16, 16);
                g.setColor(1f, 1f, 1f, 1f);
            } else {
                int gw = font.width(n.glyph);
                g.drawString(font, n.glyph, ix + (24 - gw) / 2, iconY + 8, col(n.tint, alpha), false);
            }
            textX = x + 42;
        }

        boolean hasAction = n.action != null;
        n.btnW = 48;
        n.btnH = 20;
        n.btnX = x + W - n.btnW - 8;
        n.btnY = y + (H - n.btnH) / 2;
        int textRight = hasAction ? n.btnX - 8 : x + W - 10;

        g.drawString(font, ellipsize(font, n.title, textRight - textX), textX, y + 12, col(LanPlusUI.TEXT, alpha), false);
        if (n.subtitle != null) {
            g.drawString(font, ellipsize(font, n.subtitle, textRight - textX), textX, y + 27,
                    col(LanPlusUI.MUTED, alpha), false);
        }

        if (hasAction) {
            boolean hover = mx >= n.btnX && mx < n.btnX + n.btnW && my >= n.btnY && my < n.btnY + n.btnH;
            g.fill(n.btnX, n.btnY, n.btnX + n.btnW, n.btnY + n.btnH,
                    col(hover ? LanPlusUI.shade(LanPlusUI.ACCENT_STRONG, 0.4f) : LanPlusUI.SURFACE_RAISED, alpha));
            LanPlusUI.outline1(g, n.btnX, n.btnY, n.btnX + n.btnW, n.btnY + n.btnH,
                    col(hover ? LanPlusUI.ACCENT_HOVER : LanPlusUI.ACCENT, alpha));
            int tw = font.width(n.action);
            g.drawString(font, n.action, n.btnX + (n.btnW - tw) / 2, n.btnY + 6, col(LanPlusUI.TEXT, alpha), false);
        }
    }

    private static boolean handleClick(double mx, double my) {
        for (Notif n : active) {
            if (n.dismissed || n.action == null || n.onAction == null) {
                continue;
            }
            if (mx >= n.btnX && mx < n.btnX + n.btnW && my >= n.btnY && my < n.btnY + n.btnH) {
                try {
                    n.onAction.run();
                } catch (RuntimeException ignored) {
                }
                n.dismissed = true;
                n.dismissedAt = System.currentTimeMillis();
                return true;
            }
        }
        return false;
    }

    private static Component ellipsize(Font font, Component text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String s = text.getString();
        return Component.literal(font.plainSubstrByWidth(s, maxWidth - font.width("…")) + "…");
    }

    private static int col(int rgb, float alpha) {
        int a = Math.clamp((int) (alpha * 255), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static float clamp01(float v) {
        return Math.clamp(v, 0f, 1f);
    }

    private static final class Notif {
        final UUID avatar;
        final Component title;
        final Component subtitle;
        final Component action;
        final Runnable onAction;
        final long hold;
        final int tint;
        final String glyph;
        final ResourceLocation icon;
        final long bornAt = System.currentTimeMillis();
        boolean dismissed;
        long dismissedAt;
        int x, y, btnX, btnY, btnW, btnH;

        Notif(UUID avatar, Component title, Component subtitle, Component action, Runnable onAction, long hold,
              int tint, String glyph, ResourceLocation icon) {
            this.avatar = avatar;
            this.title = title;
            this.subtitle = subtitle;
            this.action = action;
            this.onAction = onAction;
            this.hold = hold;
            this.tint = tint;
            this.glyph = glyph;
            this.icon = icon;
        }
    }
}
