package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.Config;
import dev.bgame.lanplus.api.SkinRef;
import dev.bgame.lanplus.api.SkinType;
import dev.bgame.lanplus.api.SkinUploadResult;
import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.client.SkinTextures;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class SkinScreen extends LanPlusScreen {

    private static final int PAD = 12;
    private static final int LEFT_W = 150;
    private static final long STATUS_MS = 4000;

    private final Screen parent;
    private final UUID uuid;
    private boolean slim;
    private float modelYaw;
    private float modelPitch;
    private float modelZoom = 1f;
    private boolean dragging;
    private Component status;
    private long statusUntil;

    private int cardX, cardY, cardW, cardH;
    private int mx0, my0, mx1, my1;

    public SkinScreen(Screen parent) {
        super(Component.translatable("gui.lanplus.profile.skin.header"));
        this.parent = parent;
        this.uuid = LanPlusClient.selfUuid();
        this.slim = Config.skinSlim;
    }

    private boolean custom() {
        return !Config.skinUrl.isBlank();
    }

    private void layout() {
        cardW = Math.min(this.width - 60, 430);
        cardH = Math.min(this.height - 60, 250);
        cardX = (this.width - cardW) / 2;
        cardY = Math.max(20, (this.height - cardH) / 2);
        mx0 = cardX + PAD;
        my0 = cardY + 30;
        mx1 = mx0 + LEFT_W;
        my1 = cardY + cardH - PAD;
    }

    @Override
    protected void init() {
        layout();
        int rx = cardX + PAD + LEFT_W + 12;
        int rw = cardX + cardW - PAD - rx;
        int y = my0 + 4;
        if (custom()) {
            addRenderableWidget(LanplusButton.create(sourceLabel(), b -> toggleSource())
                    .bounds(rx, y, rw, 20).build());
            y += 26;
        }
        addRenderableWidget(LanplusButton.create(slimLabel(), b -> toggleSlim())
                .bounds(rx, y, rw, 20).build());
        y += 26;
        if (custom()) {
            addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.profile.skin.remove"),
                            b -> remove())
                    .bounds(rx, y, rw, 20).build());
        }
        addRenderableWidget(LanplusButton.create(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(cardX + cardW - 80 - PAD, cardY + cardH - PAD - 20, 80, 20).primary().build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawBackdrop(g);
        layout();
        LanPlusUI.panel(g, cardX, cardY, cardX + cardW, cardY + cardH);
        int wx = LanPlusUI.wordmark(g, this.font, cardX + PAD, cardY + PAD);
        g.drawString(this.font, this.title, wx + 6, cardY + PAD, LanPlusUI.MUTED, false);
        g.fill(cardX + PAD, cardY + 24, cardX + cardW - PAD, cardY + 25, LanPlusUI.DIVIDER);

        LanPlusUI.slot(g, mx0, my0, mx1, my1);
        int cx = (mx0 + mx1) / 2;
        int feetY = my1 - 14;
        SkinTextures st = LanPlusClient.skinTextures();
        SkinTextures.Resolved res = st == null || uuid == null ? null : st.get(uuid);
        ResourceLocation skin = res != null ? res.texture()
                : DefaultPlayerSkin.get(uuid == null ? UUID.randomUUID() : uuid).texture();
        boolean modelSlim = res != null ? res.slim() : slim;
        float scale = Math.min(72f, (feetY - my0 - 14) * 0.5f) * modelZoom;
        g.enableScissor(mx0 + 1, my0 + 1, mx1 - 1, my1 - 1);
        g.fill(cx - 22, feetY - 1, cx + 22, feetY, 0x44000000);
        PlayerPreview.render(g, cx, feetY, scale, modelYaw, modelPitch, skin, modelSlim, uuid);
        g.disableScissor();

        int rx = cardX + PAD + LEFT_W + 12;
        int rw = cardX + cardW - PAD - rx;
        int hintY = custom() ? my0 + 4 + 26 * 3 + 4 : my0 + 4 + 26 + 4;
        for (FormattedCharSequence line : this.font.split(
                Component.translatable("gui.lanplus.profile.skin.hint"), rw)) {
            g.drawString(this.font, line, rx, hintY, LanPlusUI.FAINT, false);
            hintY += 10;
        }

        if (status != null && System.currentTimeMillis() < statusUntil) {
            g.drawString(this.font, status, cardX + PAD, cardY + cardH - PAD - 2 - 10, LanPlusUI.AMBER, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private Component sourceLabel() {
        boolean showingCustom = Config.skinCustomActive && custom();
        return Component.translatable(showingCustom
                ? "gui.lanplus.profile.skin.source.custom" : "gui.lanplus.profile.skin.source.mojang");
    }

    private Component slimLabel() {
        return Component.translatable(slim
                ? "gui.lanplus.profile.skin.slim" : "gui.lanplus.profile.skin.classic");
    }

    private void toggleSource() {
        Config.setSkinCustomActive(!Config.skinCustomActive);
        applyLocalSkin();
        rebuildWidgets();
    }

    private void toggleSlim() {
        slim = !slim;
        if (custom()) {
            Config.setSkin(Config.skinUrl, slim);
            applyLocalSkin();
        }
        rebuildWidgets();
    }

    private void remove() {
        if (LanPlusClient.skins() == null) {
            return;
        }
        setStatus(Component.translatable("gui.lanplus.profile.skin.removing"));
        LanPlusClient.skins().deleteSkin().whenComplete((deleted, ex) -> {
            if (ex != null || !Boolean.TRUE.equals(deleted)) {
                this.minecraft.execute(() -> setStatus(Component.translatable("gui.lanplus.profile.err.offline")));
                return;
            }
            Config.setSkin("", slim);
            this.minecraft.execute(() -> {
                applyLocalSkin();
                setStatus(Component.translatable("gui.lanplus.profile.skin.removed"));
                rebuildWidgets();
            });
        });
    }

    private void applyLocalSkin() {
        UUID self = LanPlusClient.selfUuid();
        if (self == null || LanPlusClient.skins() == null) {
            return;
        }
        boolean showCustom = Config.skinCustomActive && custom();
        if (!showCustom && LanPlusClient.skinTextures() != null) {
            LanPlusClient.skinTextures().remove(self);
        }
        SkinRef ref = showCustom
                ? new SkinRef(SkinType.CUSTOM, Config.skinUrl, null, Config.skinSlim ? "slim" : null)
                : new SkinRef(SkinType.MOJANG, self.toString(), null, null);
        LanPlusClient.skins().resolve(self, ref);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths == null || LanPlusClient.skins() == null) {
            return;
        }
        Path png = paths.stream()
                .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                .findFirst().orElse(null);
        if (png == null) {
            setStatus(Component.translatable("gui.lanplus.profile.skin.err.bad_png"));
            return;
        }
        setStatus(Component.translatable("gui.lanplus.profile.skin.uploading"));
        boolean up = slim;
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return Files.size(png) > 32 * 1024 ? null : Files.readAllBytes(png);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, Util.ioPool())
                .thenCompose(bytes -> bytes == null
                        ? CompletableFuture.completedFuture(new SkinUploadResult(null, null, "too_large"))
                        : LanPlusClient.skins().uploadSkin(bytes, up))
                .whenComplete((result, ex) -> {
                    boolean uploaded = ex == null && result != null && result.success();
                    if (uploaded) {
                        Config.setSkin(result.url(), up);
                        Config.setSkinCustomActive(true);
                    }
                    this.minecraft.execute(() -> {
                        if (ex != null) {
                            setStatus(Component.translatable("gui.lanplus.profile.skin.err.read"));
                            return;
                        }
                        if (!uploaded) {
                            setStatus(Component.translatable(skinErrorKey(result == null ? null : result.error())));
                            return;
                        }
                        applyLocalSkin();
                        setStatus(Component.translatable("gui.lanplus.profile.skin.uploaded"));
                        rebuildWidgets();
                    });
                });
    }

    private static String skinErrorKey(String error) {
        return switch (error == null ? "" : error) {
            case "too_large" -> "gui.lanplus.profile.skin.err.too_large";
            case "bad_dimensions" -> "gui.lanplus.profile.skin.err.bad_dimensions";
            case "bad_png" -> "gui.lanplus.profile.skin.err.bad_png";
            default -> "gui.lanplus.profile.err.offline";
        };
    }

    private void setStatus(Component message) {
        this.status = message;
        this.statusUntil = System.currentTimeMillis() + STATUS_MS;
    }

    private boolean inModel(double mx, double my) {
        return mx >= mx0 && mx < mx1 && my >= my0 && my < my1;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && inModel(mouseX, mouseY) && !super.mouseClicked(mouseX, mouseY, button)) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging && button == 0) {
            modelYaw -= (float) dx;
            modelPitch = Math.clamp(modelPitch - (float) dy, -35f, 35f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double delta) {
        if (inModel(mouseX, mouseY)) {
            modelZoom = Math.clamp(modelZoom + (float) delta * 0.15f, 0.6f, 2.5f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, sx, delta);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
