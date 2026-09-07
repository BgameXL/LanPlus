package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.announcements.AnnouncementsService;
import dev.bgame.lanplus.api.Announcement;
import dev.bgame.lanplus.client.LanPlusClient;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Announcements extends LanPlusScreen {

    private enum Filter {ALL, UPDATE, MAINTENANCE, GENERAL, FREE}

    private static final Filter[] FILTERS = Filter.values();
    private static final int MARGIN = 20;
    private static final int MAX_W = 580;
    private static final int MAX_H = 380;
    private static final int PAD = 10;
    private static final int FOOTER_H = 30;
    private static final int SIDEBAR_W = 92;
    private static final int SB_ROW_H = 20;
    private static final int ENTRY_GAP = 10;
    private static final int LINE_H = 13;
    private static final int TITLE_DY = 15;
    private static final int BODY_DY = 27;
    private static final int IMG_GAP = 6;
    private static final int MAX_IMG_H = 160;
    private static final int MIN_IMG_H = 42;
    private static final int MD_LINK = 0xFF6AA9FF;
    private static final int MD_CODE = 0xFFB9C4E0;
    private static final DateTimeFormatter STAMP = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());
    private final Screen parent;
    private final List<BodyHit> bodyHits = new ArrayList<>();
    private Filter selected = Filter.ALL;
    private int cardX, cardY, cardW, cardH;
    private int sidebarX, sidebarTop, listX, listTop, listBottom, contentW;
    private int scrollY;
    private Set<Integer> newIds = Set.of();
    private Component hoverTip;

    private record Line(FormattedCharSequence seq, int indent, int gapAbove) {
    }

    private record BodyHit(int x, int y, FormattedCharSequence seq) {
    }

    public Announcements(Screen parent) {
        super(Component.translatable("gui.lanplus.announcements.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout();
        addRenderableWidget(LanplusButton.create(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(cardX + cardW - 90 - PAD, cardY + cardH - 22, 90, 20).build());
        AnnouncementsService svc = LanPlusClient.announcements();
        if (svc != null && newIds.isEmpty()) {
            newIds = new HashSet<>(svc.unseenIds());
        }
        markSeen();
    }

    private void layout() {
        cardW = Math.min(this.width - 2 * MARGIN, MAX_W);
        cardH = Math.min(this.height - 60, MAX_H);
        cardX = (this.width - cardW) / 2;
        cardY = Math.max(20, (this.height - cardH) / 2 - 10);
        sidebarX = cardX + PAD;
        sidebarTop = cardY + 34;
        listX = sidebarX + SIDEBAR_W + 12;
        listTop = cardY + 34;
        listBottom = cardY + cardH - FOOTER_H;
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
        drawBackdrop(g);
        layout();
        hoverTip = null;
        bodyHits.clear();

        LanPlusUI.panel(g, cardX, cardY, cardX + cardW, cardY + cardH);
        int wx = LanPlusUI.wordmark(g, this.font, cardX + PAD, cardY + PAD);
        g.drawString(this.font, this.title, wx + 6, cardY + PAD, LanPlusUI.MUTED, false);
        g.fill(cardX + PAD, cardY + 26, cardX + cardW - PAD, cardY + 27, LanPlusUI.DIVIDER);
        g.fill(listX - 7, sidebarTop, listX - 6, listBottom, LanPlusUI.DIVIDER);
        g.fill(cardX + PAD, cardY + cardH - FOOTER_H + 4, cardX + cardW - PAD, cardY + cardH - FOOTER_H + 5,
                LanPlusUI.DIVIDER);

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
        scrollY = Math.clamp(scrollY, 0, maxScroll);

        g.enableScissor(listX, listTop, cardX + cardW - PAD, listBottom);
        int y = listTop - scrollY;
        for (Announcement a : list) {
            renderEntry(g, listX, y, a, mouseX, mouseY);
            y += entryHeight(a) + ENTRY_GAP;
            g.fill(listX, y - ENTRY_GAP / 2, listX + contentW, y - ENTRY_GAP / 2 + 1, LanPlusUI.DIVIDER);
        }
        g.disableScissor();

        super.render(g, mouseX, mouseY, partialTick);

        if (hoverTip != null) {
            g.renderTooltip(this.font, hoverTip, mouseX, mouseY);
        }
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        AnnouncementsService svc = LanPlusClient.announcements();
        List<Announcement> all = svc == null ? List.of() : svc.announcements();
        for (int i = 0; i < FILTERS.length; i++) {
            Filter f = FILTERS[i];
            int y = sidebarTop + i * SB_ROW_H;
            int rowBottom = y + SB_ROW_H - 2;
            boolean sel = f == selected;
            boolean hover = mouseX >= sidebarX && mouseX < sidebarX + SIDEBAR_W
                    && mouseY >= y && mouseY < y + SB_ROW_H;
            if (sel) {
                LanPlusUI.button3d(g, sidebarX, y, sidebarX + SIDEBAR_W, rowBottom, LanPlusUI.SURFACE_RAISED);
                g.fill(sidebarX + 2, y + 2, sidebarX + 4, rowBottom - 2, LanPlusUI.LIME);
            } else if (hover) {
                g.fill(sidebarX, y, sidebarX + SIDEBAR_W, rowBottom, LanPlusUI.SURFACE_HOVER);
            }
            g.drawString(this.font, filterLabel(f), sidebarX + 9, y + 6,
                    sel || hover ? LanPlusUI.TEXT : LanPlusUI.MUTED, false);
            String count = String.valueOf(countFor(f, all));
            g.drawString(this.font, count, sidebarX + SIDEBAR_W - 8 - this.font.width(count), y + 6,
                    sel ? LanPlusUI.LIME : LanPlusUI.FAINT, false);
        }
    }

    private int countFor(Filter f, List<Announcement> all) {
        if (f == Filter.ALL) {
            return all.size();
        }
        Announcement.Type type = typeOf(f);
        int n = 0;
        for (Announcement a : all) {
            if (a.type() == type) {
                n++;
            }
        }
        return n;
    }

    private void renderEntry(GuiGraphics g, int x, int y, Announcement a, int mouseX, int mouseY) {
        g.drawString(this.font, typeLabel(a.type()), x, y, typeColor(a.type()), false);
        int rightX = x + contentW;
        if (a.createdAt() > 0) {
            String rel = LanPlusUI.relativeTime(a.createdAt());
            int relW = this.font.width(rel);
            g.drawString(this.font, rel, rightX - relW, y, LanPlusUI.FAINT, false);
            rightX -= relW + 6;
            if (mouseY >= listTop && mouseY < listBottom
                    && mouseX >= x && mouseX <= x + contentW && mouseY >= y && mouseY < y + 9) {
                hoverTip = Component.literal(STAMP.format(Instant.ofEpochMilli(a.createdAt())));
            }
        }
        if (newIds.contains(a.id())) {
            int tagW = this.font.width("NEW");
            g.drawString(this.font, "NEW", rightX - tagW, y, LanPlusUI.LIME, false);
        }
        FormattedCharSequence titleSeq = MarkdownText.line(a.title(), MD_CODE, MD_LINK).getVisualOrderText();
        g.drawString(this.font, titleSeq, x, y + TITLE_DY, LanPlusUI.TEXT, false);
        registerLine(titleSeq, x, y + TITLE_DY, mouseX, mouseY);
        int by = y + BODY_DY;
        for (Line ln : bodyLines(a)) {
            by += ln.gapAbove();
            int lx = x + ln.indent();
            g.drawString(this.font, ln.seq(), lx, by, LanPlusUI.MUTED, false);
            registerLine(ln.seq(), lx, by, mouseX, mouseY);
            by += LINE_H;
        }
        int ih = imageHeight(a);
        if (ih > 0) {
            int iy = by + IMG_GAP;
            g.fill(x, iy, x + contentW, iy + ih, LanPlusUI.SLOT);
            LanPlusUI.outline1(g, x, iy, x + contentW, iy + ih, LanPlusUI.BORDER);
            ProfileImages.Tex tex = ProfileImages.get(a.image());
            if (tex != null) {
                ProfileImages.blitContain(g, tex, x + 1, iy + 1, contentW - 2, ih - 2);
            }
        }
    }

    private void registerLine(FormattedCharSequence seq, int lx, int ly, int mouseX, int mouseY) {
        if (ly < listTop - LINE_H || ly >= listBottom) {
            return;
        }
        bodyHits.add(new BodyHit(lx, ly, seq));
        if (mouseY >= ly && mouseY < ly + LINE_H && mouseX >= lx && mouseX <= listX + contentW) {
            Style hovered = this.font.getSplitter().componentStyleAtWidth(seq, mouseX - lx);
            if (hovered != null && hovered.getClickEvent() != null
                    && hovered.getClickEvent().getAction() == ClickEvent.Action.OPEN_URL) {
                hoverTip = Component.literal(hovered.getClickEvent().getValue());
            }
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
        return Math.clamp(natural, MIN_IMG_H, MAX_IMG_H);
    }

    private int entryHeight(Announcement a) {
        int ih = imageHeight(a);
        int h = BODY_DY;
        for (Line ln : bodyLines(a)) {
            h += ln.gapAbove() + LINE_H;
        }
        return h + (ih > 0 ? IMG_GAP + ih : 0);
    }

    private List<Line> bodyLines(Announcement a) {
        List<Line> out = new ArrayList<>();
        for (MarkdownText.Block b : MarkdownText.parse(a.body(), LanPlusUI.TEXT, MD_CODE, MD_LINK)) {
            List<FormattedCharSequence> wrapped = this.font.split(b.text(), contentW - b.indent());
            if (wrapped.isEmpty()) {
                out.add(new Line(FormattedCharSequence.EMPTY, b.indent(), b.gapAbove()));
            } else {
                for (int i = 0; i < wrapped.size(); i++) {
                    out.add(new Line(wrapped.get(i), b.indent(), i == 0 ? b.gapAbove() : 0));
                }
            }
        }
        return out;
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
        if (button == 0) {
            for (BodyHit h : bodyHits) {
                if (mouseY >= h.y() && mouseY < h.y() + LINE_H
                        && mouseX >= h.x() && mouseX <= listX + contentW) {
                    Style style = this.font.getSplitter()
                            .componentStyleAtWidth(h.seq(), (int) (mouseX - h.x()));
                    if (style != null && style.getClickEvent() != null
                            && style.getClickEvent().getAction() == ClickEvent.Action.OPEN_URL) {
                        openLink(style.getClickEvent().getValue());
                        return true;
                    }
                }
            }
        }
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        if (mouseX >= listX && mouseX <= cardX + cardW && mouseY >= listTop && mouseY <= listBottom) {
            scrollY = Math.max(0, scrollY - (int) (delta * 16));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
    }

    private void openLink(String url) {
        if (url == null) {
            return;
        }
        this.minecraft.setScreen(new ConfirmLinkScreen(yes -> {
            if (yes) {
                Util.getPlatform().openUri(url);
            }
            this.minecraft.setScreen(this);
        }, url, false));
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
