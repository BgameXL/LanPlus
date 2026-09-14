package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.profiles.ProfilesService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.Objects;
import java.util.UUID;

public final class ReportScreen extends LanPlusScreen {

    private static final String[] REASONS =
            {"hate_speech", "harassment", "spam", "inappropriate", "other"};
    private static final int MARGIN = 20;
    private static final int MAX_W = 220;
    private static final int PAD = 10;
    private final Screen parent;
    private final UUID target;
    private State state = State.CHOOSING;
    private String selectedReason;
    private int cardX, cardY, cardW, cardH;

    public ReportScreen(Screen parent, UUID target, String targetName) {
        super(Component.translatable("gui.lanplus.report.title", targetName == null ? "" : targetName));
        this.parent = parent;
        this.target = Objects.requireNonNull(target, "target");
    }

    private void layout() {
        cardW = Math.min(this.width - 2 * MARGIN, MAX_W);
        cardH = state == State.CHOOSING ? 34 + REASONS.length * 26 + 4 + 20 + PAD : 78;
        cardX = (this.width - cardW) / 2;
        cardY = Math.max(20, (this.height - cardH) / 2 - 10);
    }

    @Override
    protected void init() {
        layout();
        if (state != State.CHOOSING) {
            int buttonY = cardY + cardH + 6;
            if (state == State.FAILED) {
                addRenderableWidget(LanplusButton.create(CommonComponents.GUI_CANCEL, b -> onClose())
                        .bounds(cardX, buttonY, 90, 20).build());
                addRenderableWidget(LanplusButton.create(
                                Component.translatable("gui.lanplus.report.retry"), b -> send(selectedReason))
                        .bounds(cardX + cardW - 90, buttonY, 90, 20).build());
            } else {
                Component label = state == State.SENT ? CommonComponents.GUI_DONE : CommonComponents.GUI_CANCEL;
                addRenderableWidget(LanplusButton.create(label, b -> onClose())
                        .bounds(cardX + cardW - 90, buttonY, 90, 20).build());
            }
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
        selectedReason = reason;
        ProfilesService profiles = LanPlusClient.profiles();
        if (profiles == null) {
            state = State.FAILED;
            rebuildWidgets();
            return;
        }
        state = State.SENDING;
        rebuildWidgets();
        profiles.reportUser(target, reason).whenComplete((ignored, error) ->
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().screen != this) {
                        return;
                    }
                    state = error == null ? State.SENT : State.FAILED;
                    rebuildWidgets();
                }));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (parent instanceof ProfileScreen ps) {
            ps.renderBackdrop(g, this.width, this.height);
        } else {
            drawBackdrop(g);
        }

        LanPlusUI.panel(g, cardX, cardY, cardX + cardW, cardY + cardH);
        int wx = LanPlusUI.wordmark(g, this.font, cardX + PAD, cardY + PAD);
        g.drawString(this.font, this.title, wx + 6, cardY + PAD, LanPlusUI.MUTED, false);
        g.fill(cardX + PAD, cardY + 26, cardX + cardW - PAD, cardY + 27, LanPlusUI.DIVIDER);

        if (state != State.CHOOSING) {
            Component message = switch (state) {
                case SENDING -> Component.translatable("gui.lanplus.report.sending");
                case SENT -> Component.translatable("gui.lanplus.report.sent");
                case FAILED -> Component.translatable("gui.lanplus.report.failed");
                default -> throw new IllegalStateException("Unexpected report state: " + state);
            };
            int y = cardY + 40;
            for (FormattedCharSequence line : this.font.split(message, cardW - 2 * PAD)) {
                g.drawString(this.font, line, cardX + PAD, y, LanPlusUI.TEXT, false);
                y += 11;
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum State {
        CHOOSING,
        SENDING,
        SENT,
        FAILED
    }
}
