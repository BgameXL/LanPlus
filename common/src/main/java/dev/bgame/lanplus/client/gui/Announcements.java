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
import java.util.Locale;

public final class Announcements extends Screen {

    private enum Filter {ALL, UPDATE, MAINTENANCE, GENERAL, FREE}

    private static final Filter[] FILTERS = Filter.values();
    private static final int MARGIN = 20;
    private static final int MAX_W = 420;
    private static final int PAD = 10;
    private static final int SIDEBAR_W = 84;
    private static final int SB_ROW_H = 18;
    private static final int ENTRY_GAP = 8;
    private static final int LINE_H = 9;
    private static final int MAX_IMG_H = 120;
    private static final int MIN_IMG_H = 42;
    private final Screen parent;
    private Filter selected = Filter.ALL;
    private int cardX, cardY, cardW, cardH;
    private int sidebarX, sidebarTop, listX, listTop, listBottom, contentW;
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
        markSeen();
    }

    private void layout() {
        cardW = Math.min(this.width - 2 * MARGIN, MAX_W);
        cardH = Math.min(this.height - 80, 260);
        cardX = (this.width - cardW) / 2;
        cardY = Math.max(20, (this.height - cardH) / 2 - 10);
        sidebarX = cardX + PAD;
        sidebarTop = cardY + 34;
        listX = sidebarX + SIDEBAR_W + 12;
        listTop = cardY + 34;
        listBottom = cardY + cardH - PAD;
        contentW = cardX + cardW - PAD - listX;
    }

    private void markSeen() {
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

    private List<Announcement> filtered() {
        AnnouncementsService svc = LanPlusClient.announcements();
        List<Announcement> all = svc == null ? List.of() : svc.announcements();
        if (selected == Filter.ALL) {
            return all;
        }
        Announcement.Type type = typeOf(selected);
        List<Announcement> out = new ArrayList<>();
        for (Announcement a : all) {
            if (a.type() == type) {
                out.add(a);
            }
        }
        return out;
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
        g.fill(listX - 7, sidebarTop, listX - 6, listBottom, LanPlusUI.DIVIDER);

        renderSidebar(g, mouseX, mouseY);

        List<Announcement> list = filtered();
        if (list.isEmpty()) {
            Component empty = selected == Filter.ALL
                    ? Component.translatable("gui.lanplus.announcements.empty")
                    : Component.translatable("gui.lanplus.announcements.filter.empty");
            g.drawCenteredString(this.font, empty, listX + contentW / 2,
                    (listTop + listBottom) / 2 - 4, LanPlusUI.FAINT);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        int total = 0;
        for (Announcement a : list) {
            total += entryHeight(a) + ENTRY_GAP;
        }
        int maxScroll = Math.max(0, total - (listBottom - listTop));
        scrollY = Math.max(0, Math.min(maxScroll, scrollY));

        g.enableScissor(listX, listTop, cardX + cardW - PAD, listBottom);
        int y = listTop - scrollY;
        for (Announcement a : list) {
            renderEntry(g, listX, y, a);
            y += entryHeight(a) + ENTRY_GAP;
            g.fill(listX, y - ENTRY_GAP / 2, listX + contentW, y - ENTRY_GAP / 2 + 1, LanPlusUI.DIVIDER);
        }
        g.disableScissor();

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < FILTERS.length; i++) {
            Filter f = FILTERS[i];
            int y = sidebarTop + i * SB_ROW_H;
            boolean sel = f == selected;
            boolean hover = mouseX >= sidebarX && mouseX < sidebarX + SIDEBAR_W
                    && mouseY >= y && mouseY < y + SB_ROW_H;
            if (sel) {
                g.drawString(this.font, "+", sidebarX, y + 5, LanPlusUI.LIME, false);
            }
            g.drawString(this.font, filterLabel(f), sidebarX + 10, y + 5,
                    sel || hover ? LanPlusUI.TEXT : LanPlusUI.MUTED, false);
        }
    }

    private void renderEntry(GuiGraphics g, int x, int y, Announcement a) {
        g.drawString(this.font, typeLabel(a.type()), x, y, typeColor(a.type()), false);
        g.drawString(this.font, Component.literal(a.title()), x, y + 11, LanPlusUI.TEXT, false);
        int by = y + 22;
        int ih = imageHeight(a);
        if (ih > 0) {
            g.fill(x, by, x + contentW, by + ih, LanPlusUI.SLOT);
            LanPlusUI.bevelI(g, x, by, x + contentW, by + ih);
            ProfileImages.Tex tex = ProfileImages.get(a.image());
            if (tex != null) {
                ProfileImages.blitContain(g, tex, x + 1, by + 1, contentW - 2, ih - 2);
            }
            by += ih + 6;
        }
        for (FormattedCharSequence line : bodyLines(a)) {
            g.drawString(this.font, line, x, by, LanPlusUI.MUTED, false);
            by += LINE_H;
        }
    }

    private int imageHeight(Announcement a) {
        if (a.image() == null) {
            return 0;
        }
        ProfileImages.Tex tex = ProfileImages.get(a.image());
        if (tex == null || tex.width() <= 0) {
            return MAX_IMG_H;
        }
        int natural = contentW * tex.height() / tex.width();
        return Math.max(MIN_IMG_H, Math.min(MAX_IMG_H, natural));
    }

    private int entryHeight(Announcement a) {
        int ih = imageHeight(a);
        return 22 + (ih > 0 ? ih + 6 : 0) + bodyLines(a).size() * LINE_H;
    }

    private List<FormattedCharSequence> bodyLines(Announcement a) {
        return this.font.split(Component.literal(a.body()), contentW);
    }

    private static Component filterLabel(Filter f) {
        return Component.translatable("gui.lanplus.announcements.filter." + f.name().toLowerCase(Locale.ROOT));
    }

    private static Announcement.Type typeOf(Filter f) {
        return switch (f) {
            case UPDATE -> Announcement.Type.UPDATE;
            case MAINTENANCE -> Announcement.Type.MAINTENANCE;
            case FREE -> Announcement.Type.FREE;
            default -> Announcement.Type.GENERAL;
        };
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= sidebarX && mouseX < sidebarX + SIDEBAR_W
                && mouseY >= sidebarTop && mouseY < sidebarTop + FILTERS.length * SB_ROW_H) {
            int idx = (int) ((mouseY - sidebarTop) / SB_ROW_H);
            if (idx >= 0 && idx < FILTERS.length) {
                if (FILTERS[idx] != selected) {
                    selected = FILTERS[idx];
                    scrollY = 0;
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= listX && mouseX <= cardX + cardW && mouseY >= listTop && mouseY <= listBottom) {
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
