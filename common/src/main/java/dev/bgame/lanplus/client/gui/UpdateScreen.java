package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.platform.PlatformHolder;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class UpdateScreen extends LanPlusScreen {

    private static final int W = 300;
    private static final int H = 150;
    private final Screen parent;
    private final String version;
    private final String url;
    private int boxX, boxY;

    public UpdateScreen(Screen parent, String version, String url) {
        super(Component.translatable("gui.lanplus.update.title"));
        this.parent = parent;
        this.version = version;
        this.url = url;
    }

    @Override
    protected void init() {
        boxX = (this.width - W) / 2;
        boxY = (this.height - H) / 2;
        int by = boxY + H - 32;
        if (url != null && !url.isBlank()) {
            addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.update.download"),
                            b -> openDownload())
                    .bounds(boxX + W - 12 - 120, by, 120, 20).primary().build());
            addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.update.later"), b -> onClose())
                    .bounds(boxX + 12, by, 90, 20).build());
        } else {
            addRenderableWidget(LanplusButton.create(CommonComponents.GUI_DONE, b -> onClose())
                    .bounds(boxX + W - 12 - 90, by, 90, 20).primary().build());
        }
    }

    private void openDownload() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ConfirmLinkScreen(yes -> {
            if (yes) {
                Util.getPlatform().openUri(url);
            }
            mc.setScreen(this);
        }, url, false));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (parent != null) {
            parent.render(g, -1, -1, partialTick);
            if (parent instanceof TitleScreen) {
                TitleScreenPanel.render(g, -1, -1);
            }
        } else {
            drawBackdrop(g);
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        LanPlusUI.panel(g, boxX, boxY, boxX + W, boxY + H);
        int wx = LanPlusUI.wordmark(g, this.font, boxX + 14, boxY + 14);
        g.drawString(this.font, this.title, wx + 6, boxY + 14, LanPlusUI.TEXT, false);
        g.fill(boxX + 12, boxY + 28, boxX + W - 12, boxY + 29, LanPlusUI.DIVIDER);

        g.drawString(this.font, Component.translatable("gui.lanplus.update.latest", version),
                boxX + 14, boxY + 40, LanPlusUI.LIME, false);
        g.drawString(this.font, Component.translatable("gui.lanplus.update.current", PlatformHolder.get().modVersion()),
                boxX + 14, boxY + 54, LanPlusUI.MUTED, false);
        int y = boxY + 74;
        for (FormattedCharSequence line : this.font.split(Component.translatable("gui.lanplus.update.body"), W - 28)) {
            g.drawString(this.font, line, boxX + 14, y, LanPlusUI.MUTED, false);
            y += 10;
        }
        super.render(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        if (parent != null) {
            parent.resize(minecraft, width, height);
        }
        super.resize(minecraft, width, height);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
