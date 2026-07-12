package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.Lanplus;
import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.client.SkinTextures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Mod.EventBusSubscriber(modid = Lanplus.MODID, value = Dist.CLIENT)
public final class LanPlusNotifications {

    private static final int W = 196;
    private static final int H = 44;
    private static final int MARGIN = 8;
    private static final int GAP = 6;
    private static final long RISE_MS = 220;
    private static final long FALL_MS = 220;
    private static final long HOLD_ACTION_MS = 9000;
    private static final long HOLD_INFO_MS = 5000;

    private static final CopyOnWriteArrayList<Notif> active = new CopyOnWriteArrayList<>();

    private LanPlusNotifications() {}

    public static void friendHosting(UUID friend, String name, String joinCode) {
        boolean invited = joinCode != null && !joinCode.isBlank();
        Component subtitle = Component.translatable(invited
                ? "gui.lanplus.notif.invitedyou" : "gui.lanplus.toast.hosting");
        Component action = invited ? Component.translatable("gui.lanplus.notif.join") : null;
        Runnable onAction = invited ? () -> LanPlusClient.joinByInviteCode(joinCode) : null;
        push(new Notif(friend, Component.literal(name), subtitle, action, onAction,
                invited ? HOLD_ACTION_MS : HOLD_INFO_MS));
    }

    public static void friendRequest(UUID from, String name) {
        push(new Notif(from, Component.literal(name),
                Component.translatable("gui.lanplus.toast.request"),
                Component.translatable("gui.lanplus.notif.accept"),
                () -> {
                    if (LanPlusClient.friends() != null) {
                        LanPlusClient.friends().accept(from);
                    }
                }, HOLD_ACTION_MS));
    }

    public static void info(Component title, Component subtitle) {
        push(new Notif(null, title, subtitle, null, null, HOLD_INFO_MS));
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

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        renderAll(event.getGuiGraphics(), -1, -1);
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        renderAll(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() == 0 && handleClick(event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
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
            int y = screenH - MARGIN - H - slot * (H + GAP);
            renderOne(g, n, x, y, alpha, mouseX, mouseY);
            slot++;
        }
        active.removeAll(dead);
    }

    private static void renderOne(GuiGraphics g, Notif n, int x, int y, float alpha, double mx, double my) {
        n.x = x;
        n.y = y;
        g.fill(x, y, x + W, y + H, col(LanPlusUi.SURFACE, alpha * 0.95f));
        borderAlpha(g, x, y, x + W, y + H, alpha);
        g.fill(x, y, x + 2, y + H, col(LanPlusUi.BLURPLE, alpha));

        Font font = Minecraft.getInstance().font;
        int textX = x + 8;
        if (n.avatar != null) {
            SkinTextures textures = LanPlusClient.skinTextures();
            SkinTextures.Resolved resolved = textures == null ? null : textures.get(n.avatar);
            ResourceLocation tex = resolved != null ? resolved.texture() : DefaultPlayerSkin.getDefaultSkin(n.avatar);
            g.setColor(1f, 1f, 1f, alpha);
            PlayerFaceRenderer.draw(g, tex, x + 8, y + 10, 24);
            g.setColor(1f, 1f, 1f, 1f);
            textX = x + 40;
        }

        boolean hasAction = n.action != null;
        n.btnW = 42;
        n.btnH = 18;
        n.btnX = x + W - n.btnW - 8;
        n.btnY = y + (H - n.btnH) / 2;
        int textRight = hasAction ? n.btnX - 6 : x + W - 8;

        g.drawString(font, ellipsize(font, n.title, textRight - textX), textX, y + 9, col(LanPlusUi.TEXT, alpha), false);
        if (n.subtitle != null) {
            g.drawString(font, ellipsize(font, n.subtitle, textRight - textX), textX, y + 22,
                    col(LanPlusUi.MUTED, alpha), false);
        }

        if (hasAction) {
            boolean hover = mx >= n.btnX && mx < n.btnX + n.btnW && my >= n.btnY && my < n.btnY + n.btnH;
            g.fill(n.btnX, n.btnY, n.btnX + n.btnW, n.btnY + n.btnH,
                    col(hover ? LanPlusUi.BLURPLE_HOVER : LanPlusUi.BLURPLE, alpha));
            int tw = font.width(n.action);
            g.drawString(font, n.action, n.btnX + (n.btnW - tw) / 2, n.btnY + 5, col(0xFFFFFFFF, alpha), false);
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

    private static void borderAlpha(GuiGraphics g, int x0, int y0, int x1, int y1, float a) {
        int c = col(0xFFFFFF, a * 0.25f);
        g.fill(x0, y0, x1, y0 + 1, c);
        g.fill(x0, y1 - 1, x1, y1, c);
        g.fill(x0, y0, x0 + 1, y1, c);
        g.fill(x1 - 1, y0, x1, y1, c);
    }

    private static int col(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, (int) (alpha * 255)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static final class Notif {
        final UUID avatar;
        final Component title;
        final Component subtitle;
        final Component action;
        final Runnable onAction;
        final long hold;
        final long bornAt = System.currentTimeMillis();
        boolean dismissed;
        long dismissedAt;
        int x, y, btnX, btnY, btnW, btnH;

        Notif(UUID avatar, Component title, Component subtitle, Component action, Runnable onAction, long hold) {
            this.avatar = avatar;
            this.title = title;
            this.subtitle = subtitle;
            this.action = action;
            this.onAction = onAction;
            this.hold = hold;
        }
    }
}