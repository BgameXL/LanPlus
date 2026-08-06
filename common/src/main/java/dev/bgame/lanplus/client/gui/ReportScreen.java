package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.profiles.ProfilesService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.UUID;

public final class ReportScreen extends Screen {

    private static final String[] REASONS =
            {"hate_speech", "harassment", "spam", "inappropriate", "other"};

    private final Screen parent;
    private final UUID target;
    private final String targetName;
    private boolean sent;

    public ReportScreen(Screen parent, UUID target, String targetName) {
        super(Component.translatable("gui.lanplus.report.title", targetName == null ? "" : targetName));
        this.parent = parent;
        this.target = target;
        this.targetName = targetName == null ? "" : targetName;
    }

    @Override
    protected void init() {
        if (sent) {
            addRenderableWidget(LanPlusButton.create(CommonComponents.GUI_DONE, b -> onClose())
                    .bounds(this.width / 2 - 100, this.height / 2 + 10, 200, 20).build());
            return;
        }
        int x = this.width / 2 - 100;
        int y = this.height / 2 - (REASONS.length * 24 + 24) / 2;
        for (String reason : REASONS) {
            addRenderableWidget(LanPlusButton.create(
                            Component.translatable("gui.lanplus.report.reason." + reason),
                            b -> send(reason))
                    .bounds(x, y, 200, 20).build());
            y += 26;
        }
        addRenderableWidget(LanPlusButton.create(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(x, y + 4, 200, 20).build());
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
        this.renderBackground(g);
        Component heading = sent
                ? Component.translatable("gui.lanplus.report.sent")
                : Component.translatable("gui.lanplus.report.title", targetName);
        g.drawCenteredString(this.font, heading, this.width / 2,
                this.height / 2 - (sent ? 20 : 100), 0xFFFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
