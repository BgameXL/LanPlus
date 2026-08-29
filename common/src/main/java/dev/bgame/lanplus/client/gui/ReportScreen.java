package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.profiles.ProfilesService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.UUID;

public final class ReportScreen extends LanPlusScreen {

    private static final String[] REASONS =
            {"hate_speech", "harassment", "spam", "inappropriate", "other"};
    private static final int MARGIN = 20;
    private static final int MAX_W = 220;
    private static final int PAD = 10;
    private final Screen parent;
    private final UUID target;
    private final String targetName;
    private boolean sent;
    private int cardX, cardY, cardW, cardH;

    public ReportScreen(Screen parent, UUID target, String targetName) {
        super(Component.translatable("gui.lanplus.report.title", targetName == null ? "" : targetName));
        this.parent = parent;
        this.target = target;
        this.targetName = targetName == null ? "" : targetName;
    }

    private void layout() {
        cardW = Math.min(this.width - 2 * MARGIN, MAX_W);
        cardH = sent ? 78 : 34 + REASONS.length * 26 + 4 + 20 + PAD;
        cardX = (this.width - cardW) / 2;
        cardY = Math.max(20, (this.height - cardH) / 2 - 10);
    }

    @Override
    protected void init() {
        layout();
        if (sent) {
            addRenderableWidget(LanplusButton.create(CommonComponents.GUI_DONE, b -> onClose())
                    .bounds(cardX + cardW - 90, cardY + cardH + 6, 90, 20).build());
            return;
        }
        int bx = cardX + PAD;
        int bw = cardW - 2 * PAD;
        int y = cardY + 34;
        for (String reason : REASONS) {
            addRenderableWidget(LanplusButton.create(
                            Component.translatable("gui.lanplus.report.reason." + reason),
                            b -> send(reason))
                    .bounds(bx, y, bw, 20).build());
            y += 26;
        }
        addRenderableWidget(LanplusButton.create(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(bx, y + 4, bw, 20).build());
    }

    private void send(String reason) {
        ProfilesService svc = LanPlusClient.profiles();
        if (svc != null && target != null) {
            svc.reportUser(target, reason);
        }
        sent = true;
        clearWidgets();
        init();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (parent instanceof ProfileScreen ps) {
            ps.width = this.width;
            ps.height = this.height;
            ps.renderBackdrop(g);
        } else {
            drawBackdrop(g);
        }
        layout();

        LanPlusUI.panel(g, cardX, cardY, cardX + cardW, cardY + cardH);
        int wx = LanPlusUI.wordmark(g, this.font, cardX + PAD, cardY + PAD);
        g.drawString(this.font, this.title, wx + 6, cardY + PAD, LanPlusUI.MUTED, false);
        g.fill(cardX + PAD, cardY + 26, cardX + cardW - PAD, cardY + 27, LanPlusUI.DIVIDER);

        if (sent) {
            int y = cardY + 40;
            for (FormattedCharSequence line : this.font.split(
                    Component.translatable("gui.lanplus.report.sent"), cardW - 2 * PAD)) {
                g.drawString(this.font, line, cardX + PAD, y, LanPlusUI.TEXT, false);
                y += 11;
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
