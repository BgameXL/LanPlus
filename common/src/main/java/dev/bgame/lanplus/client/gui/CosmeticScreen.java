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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class CosmeticScreen extends LanPlusScreen {

    private static final int PAD = 12;
    private static final int SLOT = 28;
    private static final int SLOT_GAP = 8;
    private static final int LIST_W = 168;
    private static final int LIST_ROW = 26;
    private static final int LIST_GAP = 3;
    private static final CosmeticSlot[] SLOTS = CosmeticSlot.values();

    private final Screen parent;
    private final UUID uuid;
    private CosmeticSlot selected = CosmeticSlot.HEAD;
    private float modelYaw;
    private float modelPitch;
    private boolean dragging;
    private int catScroll;
    private List<Component> tip;
    private int tipX;
    private int tipY;

    private int cardX, cardY, cardW, cardH;
    private int contentTop;
    private int bandTop, bandBottom;
    private int leftColX;
    private int listX, listW;
    private int previewCx;
    private final List<Row> rows = new ArrayList<>();

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
        this.selected = selected;
    }

    private void layout() {
        cardW = Math.min(this.width - 60, 540);
        cardH = Math.min(this.height - 60, 340);
        cardX = (this.width - cardW) / 2;
        cardY = Math.max(20, (this.height - cardH) / 2);
        contentTop = cardY + 30;
        int doneY = cardY + cardH - PAD - 20;
        bandTop = contentTop;
        bandBottom = doneY - 8;
        leftColX = cardX + PAD + 4;
        listW = LIST_W;
        listX = cardX + cardW - PAD - listW;
        previewCx = (leftColX + SLOT + listX) / 2;
    }

    @Override
    protected void init() {
        layout();
        addRenderableWidget(LanplusButton.create(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(cardX + cardW - 90 - PAD, cardY + cardH - PAD - 20, 90, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
        layout();
        rows.clear();
        tip = null;

        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0x50000000);
        LanPlusUI.outline1(g, cardX, cardY, cardX + cardW, cardY + cardH, 0x66FFFFFF);
        LanPlusUI.sectionHeader(g, this.font, this.title, cardX + PAD, cardY + PAD, cardX + cardW - PAD);

        renderPreview(g, mouseX, mouseY);
        renderSlots(g, mouseX, mouseY);
        renderList(g, mouseX, mouseY);

        super.render(g, mouseX, mouseY, partialTick);

        if (tip != null) {
            drawTip(g, tip, tipX, tipY);
        }
    }

    private void renderPreview(GuiGraphics g, int mouseX, int mouseY) {
        int cx = previewCx;
        int feetY = bandBottom - 16;
        SkinTextures st = LanPlusClient.skinTextures();
        SkinTextures.Resolved res = st == null || uuid == null ? null : st.get(uuid);
        ResourceLocation skin = res != null ? res.texture()
                : DefaultPlayerSkin.get(uuid == null ? UUID.randomUUID() : uuid).texture();
        boolean slim = res != null && res.slim();

        int clipL = leftColX + SLOT + 6;
        int clipR = listX - 6;
        float scale = Math.min(100f, (bandBottom - bandTop) * 0.42f);
        g.enableScissor(clipL, bandTop, clipR, bandBottom);
        g.fill(cx - 30, feetY - 1, cx + 30, feetY, 0x44000000);
        g.fill(cx - 22, feetY - 2, cx + 22, feetY - 1, 0x33000000);
        PlayerPreview.render(g, cx, feetY, scale, modelYaw, modelPitch, skin, slim, uuid);
        g.disableScissor();
    }

    private void renderSlots(GuiGraphics g, int mouseX, int mouseY) {
        int total = SLOTS.length * SLOT + (SLOTS.length - 1) * SLOT_GAP;
        int y = bandTop + Math.max(0, (bandBottom - bandTop - total) / 2);
        for (CosmeticSlot slot : SLOTS) {
            drawSlotCell(g, mouseX, mouseY, leftColX, y, slot);
            y += SLOT + SLOT_GAP;
        }
    }

    private void drawSlotCell(GuiGraphics g, int mouseX, int mouseY, int x, int y, CosmeticSlot slot) {
        boolean sel = slot == selected;
        boolean hover = inside(mouseX, mouseY, x, y, SLOT, SLOT);
        String eq = LanPlusClient.cosmetics() == null ? null : LanPlusClient.cosmetics().equipped(uuid, slot);
        LanPlusUI.slot(g, x, y, x + SLOT, y + SLOT);
        CosmeticModel model = eq == null || LanPlusClient.cosmetics() == null ? null : LanPlusClient.cosmetics().model(eq);
        if (model != null) {
            int t = SLOT - 8;
            g.enableScissor(x + 3, y + 3, x + SLOT - 3, y + SLOT - 3);
            CosmeticGeoRender.renderThumb(g, model, LanPlusClient.cosmetics().bounds(eq), x + SLOT / 2, y + SLOT / 2, t, spin());
            g.disableScissor();
        }
        if (sel) {
            LanPlusUI.outline(g, x, y, x + SLOT, y + SLOT, LanPlusUI.ACCENT);
        } else if (hover) {
            LanPlusUI.outline(g, x, y, x + SLOT, y + SLOT, LanPlusUI.LAVENDER);
        }
        rows.add(new Row(x, y, SLOT, SLOT, () -> selected = slot));
        if (hover) {
            tip = List.of(slotName(slot).copy().withColor(LanPlusUI.TEXT & 0xFFFFFF));
            tipX = mouseX;
            tipY = mouseY;
        }
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        Component header = Component.translatable("gui.lanplus.cosmetics.slot", slotName(selected));
        g.drawString(this.font, header, listX, bandTop, LanPlusUI.TEXT, false);
        g.fill(listX, bandTop + 11, listX + listW, bandTop + 12, LanPlusUI.BORDER);
        int listTop = bandTop + 16;
        int listBottom = bandBottom;

        List<String> ids = LanPlusClient.cosmetics() == null ? List.of() : LanPlusClient.cosmetics().idsForSlot(selected);
        String current = LanPlusClient.cosmetics() == null ? null : LanPlusClient.cosmetics().equipped(uuid, selected);
        int count = ids.size() + 1;
        int totalH = count * (LIST_ROW + LIST_GAP) - LIST_GAP;
        int viewH = listBottom - listTop;
        catScroll = Math.max(0, Math.min(Math.max(0, totalH - viewH), catScroll));

        g.enableScissor(listX, listTop, listX + listW, listBottom);
        int y = listTop - catScroll;
        for (int i = 0; i < count; i++) {
            if (y + LIST_ROW >= listTop && y <= listBottom) {
                boolean clickable = y >= listTop && y + LIST_ROW <= listBottom;
                if (i == 0) {
                    drawListRow(g, mouseX, mouseY, listX, y, listW, null, current == null, clickable, this::unequipSelected);
                } else {
                    String id = ids.get(i - 1);
                    drawListRow(g, mouseX, mouseY, listX, y, listW, id, id.equals(current), clickable, () -> equipSelected(id));
                }
            }
            y += LIST_ROW + LIST_GAP;
        }
        g.disableScissor();
    }

    private void drawListRow(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, String id, boolean on,
                             boolean clickable, Runnable action) {
        boolean hover = clickable && inside(mouseX, mouseY, x, y, w, LIST_ROW);
        int fill = on ? LanPlusUI.ACCENT_TINT : hover ? LanPlusUI.SURFACE_HOVER : LanPlusUI.SURFACE_RAISED;
        LanPlusUI.button3d(g, x, y, x + w, y + LIST_ROW, fill);

        CosmeticMeta m = id == null || LanPlusClient.cosmetics() == null ? null : LanPlusClient.cosmetics().meta(id);
        g.fill(x + 2, y + 2, x + 4, y + LIST_ROW - 2, on ? LanPlusUI.LIME : rarityColor(m == null ? "common" : m.rarity()));

        int thumb = LIST_ROW - 6;
        int tx0 = x + 7;
        int ty0 = y + 3;
        int textX = tx0;
        CosmeticModel model = id == null || LanPlusClient.cosmetics() == null ? null : LanPlusClient.cosmetics().model(id);
        if (model != null) {
            g.enableScissor(tx0, ty0, tx0 + thumb, ty0 + thumb);
            CosmeticGeoRender.renderThumb(g, model, LanPlusClient.cosmetics().bounds(id), tx0 + thumb / 2, ty0 + thumb / 2, thumb, spin());
            g.disableScissor();
            textX = tx0 + thumb + 6;
        }

        String name = id == null ? Component.translatable("gui.lanplus.cosmetics.none").getString()
                : m != null ? m.name() : id;
        g.drawString(this.font, ellipsize(name, x + w - textX - 6), textX, y + (LIST_ROW - 8) / 2,
                on || hover ? LanPlusUI.TEXT : LanPlusUI.MUTED, false);

        if (clickable) {
            rows.add(new Row(x, y, w, LIST_ROW, action));
            if (hover && id != null) {
                buildTip(id, m, mouseX, mouseY);
            }
        }
    }

    private void buildTip(String id, CosmeticMeta m, int mx, int my) {
        List<Component> lines = new ArrayList<>();
        String rarity = m == null ? "common" : m.rarity();
        lines.add(Component.literal(m == null ? id : m.name()).withColor(rarityColor(rarity) & 0xFFFFFF));
        lines.add(Component.literal(capitalize(rarity)).withColor(rarityColor(rarity) & 0xFFFFFF));
        if (m != null && !m.artist().isEmpty()) {
            lines.add(Component.translatable("gui.lanplus.cosmetics.by", m.artist()).withColor(LanPlusUI.MUTED & 0xFFFFFF));
        }
        if (m != null && !m.description().isEmpty()) {
            lines.add(Component.literal(m.description()).withColor(LanPlusUI.MUTED & 0xFFFFFF));
        }
        if (m != null && !m.unlock().isEmpty()) {
            lines.add(Component.translatable("gui.lanplus.cosmetics.unlock", m.unlock()).withColor(LanPlusUI.AMBER & 0xFFFFFF));
        }
        tip = lines;
        tipX = mx;
        tipY = my;
    }

    private void drawTip(GuiGraphics g, List<Component> lines, int mx, int my) {
        int w = 0;
        for (Component c : lines) {
            w = Math.max(w, this.font.width(c));
        }
        int h = lines.size() * 10;
        int x = mx + 10;
        int y = my - 4;
        if (x + w + 6 > this.width) {
            x = mx - w - 12;
        }
        if (y + h + 6 > this.height) {
            y = this.height - h - 6;
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(x - 4, y - 4, x + w + 4, y + h + 2, 0xF00A0A10);
        LanPlusUI.outline1(g, x - 4, y - 4, x + w + 4, y + h + 2, LanPlusUI.ACCENT);
        int ly = y;
        for (Component c : lines) {
            g.drawString(this.font, c, x, ly, 0xFFFFFFFF, false);
            ly += 10;
        }
        g.pose().popPose();
    }

    private static float spin() {
        return (System.currentTimeMillis() / 40L) % 360L;
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

    private void equipSelected(String id) {
        if (LanPlusClient.cosmetics() != null && uuid != null) {
            LanPlusClient.cosmetics().equip(uuid, selected, id);
        }
    }

    private void unequipSelected() {
        if (LanPlusClient.cosmetics() != null && uuid != null) {
            LanPlusClient.cosmetics().unequip(uuid, selected);
        }
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
            int bx = leftColX + SLOT + 6;
            if (inside((int) mouseX, (int) mouseY, bx, bandTop, listX - 6 - bx, bandBottom - bandTop)) {
                dragging = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && button == 0) {
            modelYaw -= (float) dragX;
            modelPitch = Math.clamp(modelPitch - (float) dragY, -40f, 40f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        if (mouseX >= listX && mouseX <= listX + listW) {
            catScroll = Math.max(0, catScroll - (int) (dy * 20));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, dx, dy);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private Component slotName(CosmeticSlot slot) {
        return Component.translatable("gui.lanplus.menu.slot." + slot.name().toLowerCase(Locale.ROOT));
    }

    private String ellipsize(String text, int maxWidth) {
        if (maxWidth <= 0 || this.font.width(text) <= maxWidth) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, maxWidth - this.font.width("…")) + "…";
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
