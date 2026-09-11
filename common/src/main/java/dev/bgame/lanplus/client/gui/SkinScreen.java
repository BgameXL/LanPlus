package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.Config;
import dev.bgame.lanplus.api.LibrarySkin;
import dev.bgame.lanplus.api.SkinRef;
import dev.bgame.lanplus.api.SkinType;
import dev.bgame.lanplus.api.SkinUploadResult;
import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.client.SkinTextures;
import dev.bgame.lanplus.client.SkinThumbnails;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class SkinScreen extends LanPlusScreen {

    private static final int PAD = 12;
    private static final int LEFT_W = 154;
    private static final int TILE = 32;
    private static final int GAP = 6;
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

    private List<LibrarySkin> library = List.of();
    private String activeId;
    private int gridScroll;
    private final List<Tile> tiles = new ArrayList<>();

    private int cardX, cardY, cardW, cardH;
    private int mx0, my0, mx1, my1;
    private int gridX, gridTop, gridRight, gridBottom, cols;

    private record Tile(int x, int y, String id, String url, boolean slim, boolean isDefault) {
    }

    public SkinScreen(Screen parent) {
        super(Component.translatable("gui.lanplus.profile.skin.header"));
        this.parent = parent;
        this.uuid = LanPlusClient.selfUuid();
        this.slim = Config.skinSlim;
    }

    private boolean custom() {
        return !Config.skinUrl.isBlank();
    }

    private boolean mojangActive() {
        return !Config.skinCustomActive || !custom();
    }

    private void layout() {
        cardW = Math.min(this.width - 60, 520);
        cardH = Math.min(this.height - 60, 300);
        cardX = (this.width - cardW) / 2;
        cardY = Math.max(20, (this.height - cardH) / 2);
        mx0 = cardX + PAD;
        my0 = cardY + 30;
        mx1 = mx0 + LEFT_W;
        my1 = cardY + cardH - PAD;
        gridX = mx1 + 14;
        gridRight = cardX + cardW - PAD;
        gridTop = my0 + 40;
        gridBottom = my1 - 28;
        cols = Math.max(1, (gridRight - gridX + GAP) / (TILE + GAP));
    }

    @Override
    protected void init() {
        layout();
        addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.profile.skin.browse"),
                        b -> openFilePicker())
                .bounds(gridX, my0, gridRight - gridX, 20).build());
        addRenderableWidget(LanplusButton.create(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(cardX + cardW - 80 - PAD, cardY + cardH - PAD - 20, 80, 20).primary().build());
        loadLibrary();
    }

    private void loadLibrary() {
        if (LanPlusClient.skins() == null) {
            return;
        }
        LanPlusClient.skins().library().whenComplete((list, ex) -> this.minecraft.execute(() -> {
            if (ex == null && list != null) {
                library = list;
                for (LibrarySkin s : list) {
                    if (s.active()) {
                        activeId = s.id();
                    }
                }
            }
        }));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawBackdrop(g);
        layout();
        LanPlusUI.panel(g, cardX, cardY, cardX + cardW, cardY + cardH);
        int wx = LanPlusUI.wordmark(g, this.font, cardX + PAD, cardY + PAD);
        g.drawString(this.font, this.title, wx + 6, cardY + PAD, LanPlusUI.MUTED, false);
        g.fill(cardX + PAD, cardY + 24, cardX + cardW - PAD, cardY + 25, LanPlusUI.DIVIDER);

        renderModel(g);

        g.drawString(this.font, Component.translatable("gui.lanplus.profile.skin.saved"),
                gridX, my0 + 28, LanPlusUI.MUTED, false);
        renderGrid(g, mouseX, mouseY);

        if (status != null && System.currentTimeMillis() < statusUntil) {
            g.drawString(this.font, status, cardX + PAD, cardY + cardH - PAD - 2 - 10, LanPlusUI.AMBER, false);
        } else {
            status = null;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderModel(GuiGraphics g) {
        LanPlusUI.slot(g, mx0, my0, mx1, my1);
        int cx = (mx0 + mx1) / 2;
        int feetY = my1 - 26;
        SkinTextures st = LanPlusClient.skinTextures();
        SkinTextures.Resolved res = st == null || uuid == null ? null : st.get(uuid);
        ResourceLocation skin = res != null ? res.texture()
                : DefaultPlayerSkin.get(uuid == null ? UUID.randomUUID() : uuid).texture();
        boolean modelSlim = res != null ? res.slim() : slim;
        float scale = Math.min(79f, (feetY - my0 - 14) * 0.5f) * modelZoom;
        g.enableScissor(mx0 + 1, my0 + 1, mx1 - 1, my1 - 1);
        g.fill(cx - 22, feetY - 1, cx + 22, feetY, 0x44000000);
        PlayerPreview.render(g, cx, feetY, scale, modelYaw, modelPitch, skin, modelSlim, uuid);
        g.disableScissor();
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        tiles.clear();
        List<Tile> all = new ArrayList<>();
        all.add(new Tile(0, 0, null, null, false, true));
        for (LibrarySkin s : library) {
            all.add(new Tile(0, 0, s.id(), s.url(), s.slim(), false));
        }
        int rows = (all.size() + cols - 1) / cols;
        int contentH = rows * (TILE + GAP) - GAP;
        int viewH = gridBottom - gridTop;
        int maxScroll = Math.max(0, contentH - viewH);
        gridScroll = Math.clamp(gridScroll, 0, maxScroll);

        g.enableScissor(gridX, gridTop, gridRight, gridBottom);
        for (int i = 0; i < all.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = gridX + col * (TILE + GAP);
            int y = gridTop + row * (TILE + GAP) - gridScroll;
            if (y + TILE < gridTop || y > gridBottom) {
                continue;
            }
            Tile t = all.get(i);
            boolean active = t.isDefault() ? mojangActive() : (!mojangActive() && t.id().equals(activeId));
            boolean hover = mouseX >= x && mouseX < x + TILE && mouseY >= gridTop && mouseY < gridBottom
                    && mouseY >= y && mouseY < y + TILE;
            drawTile(g, t, x, y, active, hover);
            tiles.add(new Tile(x, y, t.id(), t.url(), t.slim(), t.isDefault()));
        }
        g.disableScissor();
    }

    private void drawTile(GuiGraphics g, Tile t, int x, int y, boolean active, boolean hover) {
        g.fill(x, y, x + TILE, y + TILE, active ? LanPlusUI.ACCENT_TINT : LanPlusUI.SLOT);
        ResourceLocation face = t.isDefault()
                ? DefaultPlayerSkin.get(uuid == null ? UUID.randomUUID() : uuid).texture()
                : SkinThumbnails.get(t.id(), t.url());
        if (face != null) {
            PlayerFaceRenderer.draw(g, face, x + 4, y + 4, TILE - 8);
        }
        LanPlusUI.outline1(g, x, y, x + TILE, y + TILE, active ? LanPlusUI.ACCENT : LanPlusUI.EDGE_DARK);
        if (!t.isDefault() && hover) {
            int dx = x + TILE - 9;
            g.fill(dx, y + 1, dx + 8, y + 9, 0xCCB00020);
            g.drawString(this.font, "x", dx + 2, y + 1, 0xFFFFFFFF, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Tile t : tiles) {
                if (!t.isDefault() && mouseX >= t.x() + TILE - 9 && mouseX < t.x() + TILE - 1
                        && mouseY >= t.y() + 1 && mouseY < t.y() + 9) {
                    deleteTile(t);
                    return true;
                }
                if (mouseX >= t.x() && mouseX < t.x() + TILE && mouseY >= t.y() && mouseY < t.y() + TILE
                        && mouseY >= gridTop && mouseY < gridBottom) {
                    selectTile(t);
                    return true;
                }
            }
            if (inModel(mouseX, mouseY)) {
                dragging = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectTile(Tile t) {
        if (t.isDefault()) {
            Config.setSkinCustomActive(false);
            activeId = null;
            applyLocalSkin();
            return;
        }
        if (LanPlusClient.skins() == null) {
            return;
        }
        setStatus(Component.translatable("gui.lanplus.profile.skin.applying"));
        LanPlusClient.skins().selectSkin(t.id()).whenComplete((result, ex) -> {
            boolean ok = ex == null && result != null && result.success();
            if (ok) {
                Config.setSkin(result.url(), t.slim());
                Config.setSkinCustomActive(true);
            }
            this.minecraft.execute(() -> {
                if (!ok) {
                    setStatus(Component.translatable("gui.lanplus.profile.err.offline"));
                    return;
                }
                slim = t.slim();
                activeId = t.id();
                applyLocalSkin();
                setStatus(Component.translatable("gui.lanplus.profile.skin.applied"));
            });
        });
    }

    private void deleteTile(Tile t) {
        if (t.isDefault() || t.id() == null || LanPlusClient.skins() == null) {
            return;
        }
        String id = t.id();
        LanPlusClient.skins().deleteLibrarySkin(id).whenComplete((deleted, ex) -> this.minecraft.execute(() -> {
            if (ex != null || !Boolean.TRUE.equals(deleted)) {
                setStatus(Component.translatable("gui.lanplus.profile.err.offline"));
                return;
            }
            List<LibrarySkin> next = new ArrayList<>();
            for (LibrarySkin s : library) {
                if (!s.id().equals(id)) {
                    next.add(s);
                }
            }
            library = next;
            SkinThumbnails.forget(id);
            if (id.equals(activeId)) {
                Config.setSkinCustomActive(false);
                activeId = null;
                applyLocalSkin();
            }
            setStatus(Component.translatable("gui.lanplus.profile.skin.removed"));
        }));
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
                ? new SkinRef(SkinType.CUSTOM, Config.skinUrl, null, Config.skinSlim ? "slim" : "classic")
                : new SkinRef(SkinType.MOJANG, self.toString(), null, null);
        LanPlusClient.skins().resolve(self, ref);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths == null) {
            return;
        }
        Path png = paths.stream()
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                .findFirst().orElse(null);
        if (png == null) {
            setStatus(Component.translatable("gui.lanplus.profile.skin.err.bad_png"));
            return;
        }
        uploadSkinFile(png);
    }

    private void openFilePicker() {
        String path;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(1);
            filters.put(stack.UTF8("*.png"));
            filters.flip();
            path = TinyFileDialogs.tinyfd_openFileDialog(
                    Component.translatable("gui.lanplus.profile.skin.browse.title").getString(),
                    "", filters, "PNG (*.png)", false);
        }
        if (path == null || path.isBlank()) {
            return;
        }
        if (!path.toLowerCase(Locale.ROOT).endsWith(".png")) {
            setStatus(Component.translatable("gui.lanplus.profile.skin.err.bad_png"));
            return;
        }
        uploadSkinFile(Path.of(path));
    }

    private void uploadSkinFile(Path png) {
        if (LanPlusClient.skins() == null) {
            return;
        }
        setStatus(Component.translatable("gui.lanplus.profile.skin.uploading"));
        boolean[] detected = {slim};
        CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return Files.size(png) > 32 * 1024 ? null : Files.readAllBytes(png);
                    } catch (IOException e) {
                        throw new CompletionException(e);
                    }
                }, Util.ioPool())
                .thenCompose(bytes -> {
                    if (bytes == null) {
                        return CompletableFuture.completedFuture(new SkinUploadResult(null, null, "too_large"));
                    }
                    detected[0] = SkinTextures.detectSlim(bytes);
                    return LanPlusClient.skins().addSkin(bytes, detected[0]);
                })
                .whenComplete((result, ex) -> {
                    boolean uploaded = ex == null && result != null && result.success();
                    if (uploaded) {
                        Config.setSkin(result.url(), detected[0]);
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
                        slim = detected[0];
                        activeId = result.hash();
                        applyLocalSkin();
                        loadLibrary();
                        setStatus(Component.translatable("gui.lanplus.profile.skin.uploaded"));
                    });
                });
    }

    private static String skinErrorKey(String error) {
        return switch (error == null ? "" : error) {
            case "too_large" -> "gui.lanplus.profile.skin.err.too_large";
            case "bad_dimensions" -> "gui.lanplus.profile.skin.err.bad_dimensions";
            case "bad_png" -> "gui.lanplus.profile.skin.err.bad_png";
            case "library_full" -> "gui.lanplus.profile.skin.err.library_full";
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
        if (mouseX >= gridX && mouseX < gridRight && mouseY >= gridTop && mouseY < gridBottom) {
            gridScroll -= (int) (delta * 16);
            return true;
        }
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
