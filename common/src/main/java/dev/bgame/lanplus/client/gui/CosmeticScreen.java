package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.client.CosmeticGeoRender;
import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.client.SkinTextures;
import dev.bgame.lanplus.cosmetics.CosmeticMeta;
import dev.bgame.lanplus.cosmetics.CosmeticModel;
import dev.bgame.lanplus.cosmetics.CosmeticSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CosmeticScreen extends LanPlusScreen {

    private static final int PAD = 12;
    private static final int TAB_H = 22;
    private static final int TAB_GAP = 6;
    private static final int TAB_PADX = 9;
    private static final int RIGHT_W = 224;
    private static final int COL_GAP = 12;
    private static final int CARD_MIN_W = 148;
    private static final int CARD_H = 152;
    private static final int CARD_GAP = 12;
    private static final int CTRL_W = 52;
    private static final int CTRL_H = 18;
    private static final int CTRL_GAP = 6;
    private static final CosmeticSlot[] SLOTS = CosmeticSlot.values();

    private final Screen parent;
    private final UUID uuid;
    private CosmeticSlot filter;
    private String selectedId;
    private final ModelView view = new ModelView(40f, 0.5f, 3f);
    private int gridScroll;
    private final List<Row> rows = new ArrayList<>();

    private int cardX, cardY, cardW, cardH;
    private int tabsY, contentTop, doneY;
    private int gridX, gridRight, gridTop, gridBottom, gridW, cols, cardWidth;
    private int rightX, rightTop, rightBottom, previewBottom, detailTop;

    private record Row(int x, int y, int w, int h, Runnable action) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public CosmeticScreen(Screen parent) {
        super(Component.translatable("gui.lanplus.menu.cosmetics"));
        this.parent = parent;
        UUID id = LanPlusClient.selfUuid();
        User user = Minecraft.getInstance().getUser();
        this.uuid = id != null ? id : (user != null ? user.getProfileId() : null);
    }

    public CosmeticScreen(Screen parent, CosmeticSlot selected) {
        this(parent);
        this.filter = selected;
    }

    private void layout() {
        cardW = fitWidth(660);
        cardH = fitHeight(432);
        cardX = centerX(cardW);
        cardY = centerY(cardH);
        tabsY = cardY + 32;
        contentTop = tabsY + TAB_H + 8;
        doneY = cardY + cardH - PAD - 20;
        rightX = cardX + cardW - PAD - RIGHT_W;
        rightTop = contentTop;
        rightBottom = doneY - 8;
        previewBottom = rightTop + Math.round((rightBottom - rightTop) * 0.56f);
        detailTop = previewBottom + 8;
        gridX = cardX + PAD;
        gridRight = rightX - COL_GAP;
        gridTop = contentTop;
        gridBottom = doneY - 4;
        gridW = gridRight - gridX;
        cols = Math.max(1, (gridW + CARD_GAP) / (CARD_MIN_W + CARD_GAP));
        cardWidth = (gridW - (cols - 1) * CARD_GAP) / cols;
    }

    @Override
    protected void init() {
        layout();
        LanPlusClient.ensureCosmeticShop();
        addRenderableWidget(LanplusButton.create(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(cardX + cardW - 90 - PAD, doneY, 90, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawBackdrop(g);
        layout();
        rows.clear();

        LanPlusUI.panel(g, cardX, cardY, cardX + cardW, cardY + cardH);
        LanPlusUI.sectionHeader(g, this.font, this.title, cardX + PAD, cardY + PAD, cardX + cardW - PAD);
        renderWallet(g);
        renderTabs(g, mouseX, mouseY);
        renderGrid(g, mouseX, mouseY);
        renderPreview(g, mouseX, mouseY);
        renderDetail(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderWallet(GuiGraphics g) {
        int balance = LanPlusClient.cosmetics() == null ? 0 : LanPlusClient.cosmetics().wallet();
        Component label = Component.translatable("gui.lanplus.cosmetics.balance", format(balance));
        int w = this.font.width(label) + 22;
        int x = cardX + cardW - PAD - w;
        int y = cardY + PAD - 8;
        LanPlusUI.button3d(g, x, y, x + w, y + 18, LanPlusUI.SURFACE_RAISED);
        g.fill(x + 8, y + 7, x + 12, y + 11, LanPlusUI.ACCENT);
        g.drawString(this.font, label, x + 16, y + 5, LanPlusUI.TEXT, false);
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        int x = gridX;
        for (int i = 0; i <= SLOTS.length; i++) {
            CosmeticSlot slot = i == 0 ? null : SLOTS[i - 1];
            Component label = i == 0 ? Component.translatable("gui.lanplus.cosmetics.all") : slotName(slot);
            int w = this.font.width(label) + TAB_PADX * 2;
            boolean active = filter == slot;
            boolean hover = inside(mouseX, mouseY, x, tabsY, w, TAB_H);
            int fill = active ? LanPlusUI.ACCENT_TINT : hover ? LanPlusUI.SURFACE_HOVER : LanPlusUI.SURFACE_RAISED;
            LanPlusUI.button3d(g, x, tabsY, x + w, tabsY + TAB_H, fill);
            if (active) {
                LanPlusUI.outline1(g, x, tabsY, x + w, tabsY + TAB_H, LanPlusUI.ACCENT);
            }
            g.drawString(this.font, label, x + TAB_PADX, tabsY + (TAB_H - 8) / 2,
                    active || hover ? LanPlusUI.TEXT : LanPlusUI.MUTED, false);
            CosmeticSlot target = slot;
            rows.add(new Row(x, tabsY, w, TAB_H, () -> {
                filter = target;
                gridScroll = 0;
            }));
            x += w + TAB_GAP;
        }
        g.fill(gridX, contentTop - 5, gridRight, contentTop - 4, LanPlusUI.DIVIDER);
    }

    private void renderGrid(GuiGraphics g, int mouseX, int mouseY) {
        List<String> ids = ids();
        if (ids.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.cosmetics.empty"),
                    (gridX + gridRight) / 2, gridTop + 24, LanPlusUI.FAINT);
            return;
        }
        int rowCount = (ids.size() + cols - 1) / cols;
        int totalH = rowCount * (CARD_H + CARD_GAP) - CARD_GAP;
        int viewH = gridBottom - gridTop;
        gridScroll = Math.clamp(gridScroll, 0, Math.max(0, totalH - viewH));

        g.enableScissor(gridX, gridTop, gridRight, gridBottom);
        for (int i = 0; i < ids.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = gridX + col * (cardWidth + CARD_GAP);
            int y = gridTop + row * (CARD_H + CARD_GAP) - gridScroll;
            if (y + CARD_H < gridTop || y > gridBottom) {
                continue;
            }
            boolean clickable = y >= gridTop && y + CARD_H <= gridBottom;
            renderCard(g, mouseX, mouseY, x, y, ids.get(i), clickable);
        }
        g.disableScissor();
    }

    private void renderCard(GuiGraphics g, int mouseX, int mouseY, int x, int y, String id, boolean clickable) {
        boolean sel = id.equals(selectedId);
        boolean hover = clickable && inside(mouseX, mouseY, x, y, cardWidth, CARD_H);
        int fill = sel ? LanPlusUI.ACCENT_TINT : hover ? LanPlusUI.SURFACE_HOVER : LanPlusUI.SURFACE_RAISED;
        LanPlusUI.button3d(g, x, y, x + cardWidth, y + CARD_H, fill);

        CosmeticMeta m = LanPlusClient.cosmetics() == null ? null : LanPlusClient.cosmetics().meta(id);
        String rarity = m == null ? "common" : m.rarity();
        g.fill(x + 3, y + 3, x + cardWidth - 3, y + 6, rarityColor(rarity));

        int thumbBottom = y + CARD_H - 46;
        int thumb = Math.min(cardWidth - 24, thumbBottom - (y + 10));
        if (thumb > 8) {
            LanPlusClient.ensureCosmeticModel(id);
            CosmeticModel model = LanPlusClient.cosmetics() == null ? null : LanPlusClient.cosmetics().model(id);
            if (model != null) {
                int tcx = x + cardWidth / 2;
                int tcy = (y + 12 + thumbBottom) / 2;
                g.enableScissor(x + 4, y + 8, x + cardWidth - 4, thumbBottom);
                CosmeticGeoRender.renderThumb(g, model, LanPlusClient.cosmetics().bounds(id), tcx, tcy, thumb, spin());
                g.disableScissor();
            }
        }

        String name = m != null ? m.name() : id;
        g.drawString(this.font, ellipsize(name, cardWidth - 16), x + 8, y + CARD_H - 40,
                sel || hover ? LanPlusUI.TEXT : LanPlusUI.MUTED, false);
        g.drawString(this.font, capitalize(rarity), x + 8, y + CARD_H - 28, rarityColor(rarity), false);
        drawState(g, x + 8, y + CARD_H - 20, cardWidth - 16, id, m);

        if (sel) {
            LanPlusUI.outline(g, x, y, x + cardWidth, y + CARD_H, LanPlusUI.ACCENT);
        }
        if (clickable) {
            rows.add(new Row(x, y, cardWidth, CARD_H, () -> selectedId = id));
        }
    }

    private void drawState(GuiGraphics g, int x, int y, int w, String id, CosmeticMeta m) {
        boolean equipped = isEquipped(id);
        boolean owned = owns(id);
        if (equipped) {
            g.fill(x, y, x + w, y + 10, 0x2257C07A);
            drawCenteredIn(g, Component.translatable("gui.lanplus.cosmetics.equipped"), x, y, w, 0xFF8EE06A);
            return;
        }
        if (owned) {
            g.fill(x, y, x + w, y + 14, LanPlusUI.SURFACE_RAISED);
            drawCenteredIn(g, Component.translatable("gui.lanplus.cosmetics.equip"), x, y, w, LanPlusUI.MUTED);
            return;
        }
        int price = m == null ? 0 : m.price();
        boolean afford = canAfford(price);
        g.fill(x, y, x + w, y + 14, 0x22101018);
        Component label = price <= 0 ? Component.translatable("gui.lanplus.cosmetics.free")
                : Component.literal(format(price));
        int color = afford ? LanPlusUI.ACCENT : LanPlusUI.FAINT;
        int textW = this.font.width(label) + (price <= 0 ? 0 : 8);
        int tx = x + (w - textW) / 2;
        if (price > 0) {
            g.fill(tx, y + 5, tx + 4, y + 9, color);
            tx += 8;
        }
        g.drawString(this.font, label, tx, y + 3, color, false);
    }

    private void renderPreview(GuiGraphics g, int mouseX, int mouseY) {
        int clipL = rightX + 4;
        int clipR = rightX + RIGHT_W - 4;
        int clipTop = rightTop + 4;
        int controlsY = previewBottom - CTRL_H - 4;
        int clipBottom = controlsY - 2;
        int cx = (rightX + rightX + RIGHT_W) / 2;

        g.fill(rightX, rightTop, rightX + RIGHT_W, previewBottom, 0x33101018);
        LanPlusUI.outline1(g, rightX, rightTop, rightX + RIGHT_W, previewBottom, LanPlusUI.EDGE_DARK);

        SkinTextures st = LanPlusClient.skinTextures();
        SkinTextures.Resolved res = st == null || uuid == null ? null : st.get(uuid);
        ResourceLocation skin = res != null ? res.texture()
                : DefaultPlayerSkin.get(uuid == null ? UUID.randomUUID() : uuid).texture();
        boolean slim = res != null && res.slim();

        float base = Math.min(80f, (clipBottom - clipTop) * 0.32f);
        float scale = base * view.zoom();
        int bodyCenterY = (clipBottom - 10) - Math.round(base * 0.92f);
        int feetY = bodyCenterY + Math.round(scale * 0.92f);
        g.enableScissor(clipL, clipTop, clipR, clipBottom);
        g.fill(cx - 28, feetY - 1, cx + 28, feetY, 0x44000000);
        PlayerPreview.render(g, cx, feetY, scale, view.yaw(), view.pitch(), skin, slim, uuid, previewOverride());
        g.disableScissor();

        int total = CTRL_W * 2 + CTRL_GAP;
        int bx = cx - total / 2;
        drawControl(g, mouseX, mouseY, bx, controlsY, Component.translatable("gui.lanplus.cosmetics.rotate"), this::rotate);
        drawControl(g, mouseX, mouseY, bx + CTRL_W + CTRL_GAP, controlsY,
                Component.translatable("gui.lanplus.cosmetics.reset"), this::resetView);
    }

    private void drawControl(GuiGraphics g, int mouseX, int mouseY, int x, int y, Component label, Runnable action) {
        boolean hover = inside(mouseX, mouseY, x, y, CTRL_W, CTRL_H);
        LanPlusUI.button3d(g, x, y, x + CTRL_W, y + CTRL_H, hover ? LanPlusUI.SURFACE_HOVER : LanPlusUI.SURFACE_RAISED);
        int tx = x + (CTRL_W - this.font.width(label)) / 2;
        g.drawString(this.font, label, tx, y + (CTRL_H - 8) / 2, hover ? LanPlusUI.TEXT : LanPlusUI.MUTED, false);
        rows.add(new Row(x, y, CTRL_W, CTRL_H, action));
    }

    private void renderDetail(GuiGraphics g, int mouseX, int mouseY) {
        LanPlusUI.panel(g, rightX, detailTop, rightX + RIGHT_W, rightBottom);
        int x = rightX + 10;
        int right = rightX + RIGHT_W - 10;
        if (selectedId == null) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.cosmetics.pick"),
                    (rightX + rightX + RIGHT_W) / 2, detailTop + (rightBottom - detailTop) / 2 - 4, LanPlusUI.FAINT);
            return;
        }
        CosmeticMeta m = LanPlusClient.cosmetics() == null ? null : LanPlusClient.cosmetics().meta(selectedId);
        String rarity = m == null ? "common" : m.rarity();
        String name = m != null ? m.name() : selectedId;
        g.drawString(this.font, ellipsize(name, right - x), x, detailTop + 10, LanPlusUI.TEXT, false);
        g.drawString(this.font, capitalize(rarity), x, detailTop + 24, rarityColor(rarity), false);
        if (m != null && !m.artist().isEmpty()) {
            g.drawString(this.font, Component.translatable("gui.lanplus.cosmetics.by", m.artist()), x, detailTop + 38,
                    LanPlusUI.MUTED, false);
        }
        if (m != null && !m.description().isEmpty()) {
            g.drawString(this.font, ellipsize(m.description(), right - x), x, detailTop + 52, LanPlusUI.MUTED, false);
        }
        renderAction(g, mouseX, mouseY, x, right, m);
    }

    private void renderAction(GuiGraphics g, int mouseX, int mouseY, int x, int right, CosmeticMeta m) {
        int by = rightBottom - 10 - 24;
        int w = right - x;
        boolean equipped = isEquipped(selectedId);
        boolean owned = owns(selectedId);
        boolean hover = inside(mouseX, mouseY, x, by, w, 24);

        if (equipped) {
            LanPlusUI.button3d(g, x, by, x + w, by + 24, hover ? LanPlusUI.SURFACE_HOVER : LanPlusUI.SURFACE_RAISED);
            drawCenteredIn(g, Component.translatable("gui.lanplus.cosmetics.unequip"), x, by + 8, w,
                    hover ? LanPlusUI.TEXT : LanPlusUI.MUTED);
            rows.add(new Row(x, by, w, 24, this::unequipSelected));
            return;
        }
        if (owned) {
            LanPlusUI.button3d(g, x, by, x + w, by + 24, hover ? LanPlusUI.ACCENT_HOVER : LanPlusUI.ACCENT_TINT);
            drawCenteredIn(g, Component.translatable("gui.lanplus.cosmetics.equip"), x, by + 8, w, LanPlusUI.TEXT);
            rows.add(new Row(x, by, w, 24, this::equipSelected));
            return;
        }
        int price = m == null ? 0 : m.price();
        boolean afford = canAfford(price);
        if (afford) {
            g.fill(x, by, x + w, by + 24, LanPlusUI.ACCENT);
            LanPlusUI.outline1(g, x, by, x + w, by + 24, LanPlusUI.ACCENT_HOVER);
            drawCenteredIn(g, Component.translatable("gui.lanplus.cosmetics.buy", format(price)), x, by + 8, w, 0xFF0C0A12);
            rows.add(new Row(x, by, w, 24, this::buySelected));
        } else {
            g.fill(x, by, x + w, by + 24, LanPlusUI.SURFACE_DISABLED);
            drawCenteredIn(g, Component.translatable("gui.lanplus.cosmetics.buy", format(price)), x, by + 8, w, LanPlusUI.FAINT);
            int wallet = LanPlusClient.cosmetics() == null ? 0 : LanPlusClient.cosmetics().wallet();
            g.drawString(this.font, Component.translatable("gui.lanplus.cosmetics.short", format(price - wallet)),
                    x, by - 12, LanPlusUI.AMBER, false);
        }
    }

    private void drawCenteredIn(GuiGraphics g, Component label, int x, int y, int w, int color) {
        g.drawString(this.font, label, x + (w - this.font.width(label)) / 2, y, color, false);
    }

    private Map<CosmeticSlot, String> previewOverride() {
        if (selectedId == null || isEquipped(selectedId)) {
            return null;
        }
        Map<CosmeticSlot, String> map = new EnumMap<>(CosmeticSlot.class);
        map.put(LanPlusClient.cosmetics().slotOf(selectedId), selectedId);
        return map;
    }

    private List<String> ids() {
        if (LanPlusClient.cosmetics() == null) {
            return List.of();
        }
        return filter == null ? LanPlusClient.cosmetics().allIds() : LanPlusClient.cosmetics().idsForSlot(filter);
    }

    private boolean isEquipped(String id) {
        if (id == null || LanPlusClient.cosmetics() == null || uuid == null) {
            return false;
        }
        return id.equals(LanPlusClient.cosmetics().equipped(uuid, LanPlusClient.cosmetics().slotOf(id)));
    }

    private boolean owns(String id) {
        return isEquipped(id) || (LanPlusClient.cosmetics() != null && LanPlusClient.cosmetics().owns(id));
    }

    private boolean canAfford(int price) {
        return LanPlusClient.cosmetics() != null && LanPlusClient.cosmetics().wallet() >= price;
    }

    private void equipSelected() {
        if (selectedId == null || LanPlusClient.cosmetics() == null || uuid == null) {
            return;
        }
        CosmeticSlot slot = LanPlusClient.cosmetics().slotOf(selectedId);
        LanPlusClient.cosmetics().equip(uuid, slot, selectedId);
        if (LanPlusClient.network() != null) {
            LanPlusClient.network().equipCosmetic(slot.name(), selectedId);
        }
    }

    private void unequipSelected() {
        if (selectedId == null || LanPlusClient.cosmetics() == null || uuid == null) {
            return;
        }
        CosmeticSlot slot = LanPlusClient.cosmetics().slotOf(selectedId);
        LanPlusClient.cosmetics().unequip(uuid, slot);
        if (LanPlusClient.network() != null) {
            LanPlusClient.network().equipCosmetic(slot.name(), null);
        }
    }

    private void buySelected() {
        if (selectedId == null || LanPlusClient.cosmetics() == null || uuid == null) {
            return;
        }
        CosmeticMeta m = LanPlusClient.cosmetics().meta(selectedId);
        int price = m == null ? 0 : m.price();
        if (!LanPlusClient.cosmetics().spend(price)) {
            return;
        }
        LanPlusClient.cosmetics().markOwned(selectedId);
        LanPlusClient.cosmetics().equip(uuid, LanPlusClient.cosmetics().slotOf(selectedId), selectedId);
        LanPlusClient.completePurchase(uuid, selectedId);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Row r : rows) {
                if (r.in(mouseX, mouseY)) {
                    r.action().run();
                    return true;
                }
            }
            if (inside((int) mouseX, (int) mouseY, rightX + 4, rightTop + 4, RIGHT_W - 8, previewBottom - rightTop - 8)) {
                view.beginDrag();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && view.drag(dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        view.endDrag();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (mouseX >= rightX && mouseX <= rightX + RIGHT_W && mouseY >= rightTop && mouseY <= previewBottom) {
            view.zoomBy(dy);
            return true;
        }
        if (mouseX >= gridX && mouseX <= gridRight && mouseY >= gridTop && mouseY <= gridBottom) {
            gridScroll = Math.max(0, gridScroll - (int) (dy * 24));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private Component slotName(CosmeticSlot slot) {
        return Component.translatable("gui.lanplus.menu.slot." + slot.name().toLowerCase(Locale.ROOT));
    }

    private String format(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private String ellipsize(String text, int maxWidth) {
        if (maxWidth <= 0 || this.font.width(text) <= maxWidth) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, maxWidth - this.font.width("…")) + "…";
    }

    private static float spin() {
        return ((float) System.currentTimeMillis() / 40L) % 360L;
    }

    private static int rarityColor(String rarity) {
        return switch (rarity == null ? "" : rarity.toLowerCase(Locale.ROOT)) {
            case "uncommon" -> 0xFF57C07A;
            case "rare" -> 0xFF5B8CFF;
            case "epic" -> 0xFFB36AF0;
            case "legendary" -> 0xFFF0B84A;
            default -> 0xFF8B909A;
        };
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void rotate() {
        view.rotateBy(90f);
    }

    private void resetView() {
        view.reset();
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
