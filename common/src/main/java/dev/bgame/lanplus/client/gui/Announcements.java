package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.announcements.AnnouncementsService;
import dev.bgame.lanplus.api.Announcement;
import dev.bgame.lanplus.client.LanPlusClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public final class Announcements extends Screen {

    private static final int MARGIN = 20;
    private static final int MAX_W = 360;
    private static final int PAD = 10;
    private static final int ENTRY_GAP = 8;
    private static final int LINE_H = 9;
    private final Screen parent;
    private int cardX, cardY, cardW, cardH;
    private int listTop, listBottom, contentW;
    private int scrollY;

    public Announcements(Screen parent) {
        super(Component.translatable("gui.lanplus.announcements.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout();
        addRenderableWidget(LanplusButton.create(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(cardX + cardW - 90, cardY + cardH + 6, 90, 20).build());
        markAllSeen();
    }

    private void layout() {
        cardW = Math.min(this.width - 2 * MARGIN, MAX_W);
        cardH = Math.min(this.height - 80, 260);
        cardX = (this.width - cardW) / 2;
        cardY = Math.max(20, (this.height - cardH) / 2 - 10);
        listTop = cardY + 30;
        listBottom = cardY + cardH - PAD;
        contentW = cardW - 2 * PAD;
    }

    private void markAllSeen() {
        AnnouncementsService svc = LanPlusClient.announcements();
        if (svc == null) {
            return;
        }
        List<Integer> ids = new ArrayList<>();
        for (Announcement a : svc.announcements()) {
            ids.add(a.id());
        }
        svc.markSeen(ids);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        LanPlusUI.backdrop(g, this.width, this.height);
        layout();

        LanPlusUI.panel(g, cardX, cardY, cardX + cardW, cardY + cardH);
        int wx = LanPlusUI.wordmark(g, this.font, cardX + PAD, cardY + PAD);
        g.drawString(this.font, this.title, wx + 6, cardY + PAD, LanPlusUI.MUTED, false);
        g.fill(cardX + PAD, cardY + 26, cardX + cardW - PAD, cardY + 27, LanPlusUI.DIVIDER);

        List<Announcement> list = LanPlusClient.announcements() == null
                ? List.of() : LanPlusClient.announcements().announcements();
        if (list.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.announcements.empty"),
                    cardX + cardW / 2, (listTop + listBottom) / 2 - 4, LanPlusUI.FAINT);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        int total = 0;
        for (Announcement a : list) {
            total += entryHeight(a) + ENTRY_GAP;
        }
        int maxScroll = Math.max(0, total - (listBottom - listTop));
        scrollY = Math.max(0, Math.min(maxScroll, scrollY));

        g.enableScissor(cardX + PAD, listTop, cardX + cardW - PAD, listBottom);
        int x = cardX + PAD;
        int y = listTop - scrollY;
        for (Announcement a : list) {
            renderEntry(g, x, y, a);
            y += entryHeight(a) + ENTRY_GAP;
            g.fill(x, y - ENTRY_GAP / 2, x + contentW, y - ENTRY_GAP / 2 + 1, LanPlusUI.DIVIDER);
        }
        g.disableScissor();

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderEntry(GuiGraphics g, int x, int y, Announcement a) {
        g.drawString(this.font, typeLabel(a.type()), x, y, typeColor(a.type()), false);
        g.drawString(this.font, Component.literal(a.title()), x, y + 11, LanPlusUI.TEXT, false);
        int by = y + 22;
        for (FormattedCharSequence line : bodyLines(a)) {
            g.drawString(this.font, line, x, by, LanPlusUI.MUTED, false);
            by += LINE_H;
        }
    }

    private int entryHeight(Announcement a) {
        return 22 + bodyLines(a).size() * LINE_H;
    }

    private List<FormattedCharSequence> bodyLines(Announcement a) {
        return this.font.split(Component.literal(a.body()), contentW);
    }

    private static String typeLabel(Announcement.Type type) {
        return "[" + type.name() + "]";
    }

    private static int typeColor(Announcement.Type type) {
        return switch (type) {
            case UPDATE -> 0xFF55FF55;
            case MAINTENANCE -> 0xFFFFFF55;
            case GENERAL -> 0xFF55FFFF;
            default -> 0xFFFFFFFF;
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= cardX && mouseX <= cardX + cardW && mouseY >= listTop && mouseY <= listBottom) {
            scrollY = Math.max(0, scrollY - (int) (delta * 16));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
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