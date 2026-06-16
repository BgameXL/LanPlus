package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.api.Connectivity;
import dev.bgame.lanplus.api.Friend;
import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.UserProfile;
import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.friends.FriendsService;
import dev.bgame.lanplus.invites.InviteService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class FriendsScreen extends Screen {

    private enum Tab { FRIENDS, BLOCKED, ADD }

    private static final int PANEL_BG = 0xC0101018;
    private static final int LEFT_X = 20;
    private static final int LEFT_W = 160;
    private static final int CONTENT_TOP = 44;
    private static final int ROW_H = 24;

    private final Screen parent;
    private Tab tab = Tab.FRIENDS;
    private UUID selectedUuid;
    private EditBox addBox;
    private Component status;
    private boolean primed;

    public FriendsScreen(Screen parent) {
        super(Component.translatable("gui.lanplus.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(tabButton("gui.lanplus.tab.friends", Tab.FRIENDS, LEFT_X));
        addRenderableWidget(tabButton("gui.lanplus.tab.blocked", Tab.BLOCKED, LEFT_X + 84));
        addRenderableWidget(tabButton("gui.lanplus.tab.add", Tab.ADD, LEFT_X + 168));

        addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.refresh"), b -> doRefresh())
                .bounds(this.width - 200, this.height - 28, 90, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(this.width - 104, this.height - 28, 84, 20).build());

        int rightX = LEFT_X + LEFT_W + 10;
        int rightW = this.width - 20 - rightX;
        if (tab == Tab.ADD) {
            addBox = new EditBox(this.font, rightX + 6, CONTENT_TOP + 22, rightW - 84, 20,
                    Component.translatable("gui.lanplus.add.hint"));
            addBox.setHint(Component.translatable("gui.lanplus.add.hint"));
            addRenderableWidget(addBox);
            addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.add.button"), b -> doAdd())
                    .bounds(rightX + rightW - 72, CONTENT_TOP + 22, 66, 20).build());
        } else if (tab == Tab.FRIENDS && selectedFriend() != null) {
            int by = this.height - 92;
            Friend selected = selectedFriend();
            Button join = Button.builder(Component.translatable("gui.lanplus.action.join"), b -> doJoin())
                    .bounds(rightX + 6, by, 90, 20).build();
            join.active = selected.state() == GameplayState.HOSTING && selected.joinCode() != null;
            addRenderableWidget(join);
            addRenderableWidget(placeholderButton("gui.lanplus.action.message", rightX + 102, by));
            addRenderableWidget(placeholderButton("gui.lanplus.action.block", rightX + 6, by + 24));
            addRenderableWidget(placeholderButton("gui.lanplus.action.remove", rightX + 102, by + 24));
        }

        if (!primed) {
            primed = true;
            doRefresh();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        int rightX = LEFT_X + LEFT_W + 10;
        int rightW = this.width - 20 - rightX;
        int paneBottom = this.height - 36;

        g.drawString(this.font, this.title, LEFT_X, 10, 0xFFFFFFFF);
        boolean online = isOnline();
        Component conn = online ? Component.translatable("gui.lanplus.status.connected")
                : Component.translatable("gui.lanplus.status.local");
        g.drawString(this.font, conn, this.width - 20 - this.font.width(conn), 12,
                online ? 0xFF43B581 : 0xFF747F8D);

        g.fill(LEFT_X, CONTENT_TOP, LEFT_X + LEFT_W, paneBottom, PANEL_BG);
        g.fill(rightX, CONTENT_TOP, rightX + rightW, paneBottom, PANEL_BG);

        switch (tab) {
            case FRIENDS -> renderFriendList(g, mouseX, mouseY, paneBottom);
            case BLOCKED -> g.drawCenteredString(this.font, Component.translatable("gui.lanplus.blocked.empty"),
                    LEFT_X + LEFT_W / 2, CONTENT_TOP + 30, 0xFF888888);
            case ADD -> { /* list pane unused in add tab */ }
        }
        renderDetail(g, rightX, rightW, paneBottom);

        if (status != null) {
            g.drawString(this.font, status, LEFT_X, this.height - 34, 0xFFAAAAAA);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderFriendList(GuiGraphics g, int mouseX, int mouseY, int paneBottom) {
        List<Friend> list = friends();
        if (list.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.friends.empty"),
                    LEFT_X + LEFT_W / 2, CONTENT_TOP + 30, 0xFF888888);
            return;
        }
        int y = CONTENT_TOP + 2;
        for (Friend f : list) {
            if (y + ROW_H > paneBottom) {
                break;
            }
            boolean selected = f.uuid().equals(selectedUuid);
            boolean hover = mouseX >= LEFT_X && mouseX <= LEFT_X + LEFT_W && mouseY >= y && mouseY < y + ROW_H;
            if (selected || hover) {
                g.fill(LEFT_X, y, LEFT_X + LEFT_W, y + ROW_H, selected ? 0x40FFFFFF : 0x20FFFFFF);
            }
            g.fill(LEFT_X + 6, y + ROW_H / 2 - 3, LEFT_X + 12, y + ROW_H / 2 + 3, statusColor(f.connectivity()));
            g.drawString(this.font, f.username(), LEFT_X + 18, y + 4, 0xFFFFFFFF);
            g.drawString(this.font, secondaryText(f), LEFT_X + 18, y + 14, 0xFF9AA0A6);
            y += ROW_H;
        }
    }

    private void renderDetail(GuiGraphics g, int x, int w, int paneBottom) {
        if (tab == Tab.ADD) {
            g.drawString(this.font, Component.translatable("gui.lanplus.add.title"), x + 6, CONTENT_TOP + 6, 0xFFFFFFFF);
            g.drawString(this.font, Component.translatable("gui.lanplus.add.note"), x + 6, CONTENT_TOP + 48, 0xFF888888);
            UserProfile self = localProfile();
            Component code = self != null && self.friendCode() != null
                    ? Component.translatable("gui.lanplus.add.yourcode", self.friendCode())
                    : Component.translatable("gui.lanplus.add.yourcode.unknown");
            g.drawString(this.font, code, x + 6, CONTENT_TOP + 64, 0xFF43B581);
            return;
        }
        Friend f = selectedFriend();
        if (f == null) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.detail.none"),
                    x + w / 2, CONTENT_TOP + 30, 0xFF888888);
            return;
        }
        g.drawString(this.font, f.username(), x + 8, CONTENT_TOP + 8, 0xFFFFFFFF);
        g.fill(x + 8, CONTENT_TOP + 20, x + 14, CONTENT_TOP + 26, statusColor(f.connectivity()));
        g.drawString(this.font, connectivityText(f), x + 18, CONTENT_TOP + 20, 0xFF9AA0A6);
        g.drawString(this.font, secondaryText(f), x + 8, CONTENT_TOP + 36, 0xFFB9BDC2);

        int chatTop = CONTENT_TOP + 54;
        int chatBottom = paneBottom - 60;
        g.fill(x + 8, chatTop, x + w - 8, chatBottom, 0x80000000);
        g.drawCenteredString(this.font, Component.translatable("gui.lanplus.chat.placeholder"),
                x + w / 2, (chatTop + chatBottom) / 2 - 4, 0xFF666666);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.FRIENDS && button == 0) {
            int y = CONTENT_TOP + 2;
            for (Friend f : friends()) {
                if (mouseX >= LEFT_X && mouseX <= LEFT_X + LEFT_W && mouseY >= y && mouseY < y + ROW_H) {
                    selectedUuid = f.uuid();
                    rebuildWidgets();
                    return true;
                }
                y += ROW_H;
                if (y + ROW_H > this.height - 36) {
                    break;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- actions ---------------------------------------------------------------------------------

    private void doRefresh() {
        FriendsService friends = LanPlusClient.friends();
        if (friends != null) {
            friends.refresh();
            setStatus(Component.translatable("gui.lanplus.status.refreshing"));
        }
    }

    private void doAdd() {
        FriendsService friends = LanPlusClient.friends();
        if (friends == null || addBox == null) {
            return;
        }
        String text = addBox.getValue().trim();
        if (text.isEmpty()) {
            return;
        }
        // Resolve username/friend code → uuid → friend request (PROTOCOL.md § Users). Runs off-thread.
        setStatus(Component.translatable("gui.lanplus.add.searching", text));
        friends.addByQuery(text).whenComplete((ok, err) -> this.minecraft.execute(() -> {
            if (err == null && Boolean.TRUE.equals(ok)) {
                setStatus(Component.translatable("gui.lanplus.add.sent", text));
                if (addBox != null) {
                    addBox.setValue("");
                }
            } else {
                setStatus(Component.translatable("gui.lanplus.add.notfound", text));
            }
        }));
    }

    private void doJoin() {
        Friend friend = selectedFriend();
        InviteService invites = LanPlusClient.invites();
        if (friend == null || invites == null || friend.joinCode() == null) {
            return;
        }
        // Click JOIN → resolve join code to an address → connect directly (PROTOCOL.md § Invites).
        setStatus(Component.translatable("gui.lanplus.join.connecting", friend.username()));
        invites.resolve(friend.joinCode()).whenComplete((invite, err) -> this.minecraft.execute(() -> {
            if (err == null && invite != null && invite.address() != null) {
                connectTo(invite.address());
            } else {
                setStatus(Component.translatable("gui.lanplus.join.failed"));
            }
        }));
    }

    private void connectTo(String address) {
        ServerData serverData = new ServerData("LAN+", address, false);
        ConnectScreen.startConnecting(this, this.minecraft, ServerAddress.parseString(address), serverData, false);
    }

    private void setStatus(Component message) {
        this.status = message;
    }

    private UserProfile localProfile() {
        FriendsService friends = LanPlusClient.friends();
        return friends == null ? null : friends.localProfile();
    }

    // --- helpers ---------------------------------------------------------------------------------

    private Button tabButton(String key, Tab target, int x) {
        return Button.builder(Component.translatable(key), b -> {
            this.tab = target;
            rebuildWidgets();
        }).bounds(x, 24, 80, 16).build();
    }

    private Button placeholderButton(String key, int x, int y) {
        return Button.builder(Component.translatable(key),
                        b -> setStatus(Component.translatable("gui.lanplus.placeholder")))
                .bounds(x, y, 90, 20).build();
    }

    private List<Friend> friends() {
        FriendsService friends = LanPlusClient.friends();
        return friends == null ? List.of() : friends.friends();
    }

    private Friend selectedFriend() {
        if (selectedUuid == null) {
            return null;
        }
        for (Friend f : friends()) {
            if (f.uuid().equals(selectedUuid)) {
                return f;
            }
        }
        return null;
    }

    private boolean isOnline() {
        return LanPlusClient.network() != null && LanPlusClient.network().isConnected();
    }

    private Component secondaryText(Friend f) {
        if (f.connectivity() == Connectivity.OFFLINE) {
            return Component.translatable("gui.lanplus.state.offline");
        }
        GameplayState state = f.state();
        if (state == null) {
            return Component.translatable("gui.lanplus.state.online");
        }
        return switch (state) {
            case HOSTING -> Component.translatable("gui.lanplus.state.hosting", world(f));
            case MULTIPLAYER -> Component.translatable("gui.lanplus.state.playing", world(f));
            case SINGLEPLAYER -> Component.translatable("gui.lanplus.state.singleplayer");
            case MENU -> Component.translatable("gui.lanplus.state.online");
        };
    }

    private Component connectivityText(Friend f) {
        return Component.translatable("gui.lanplus.conn." + f.connectivity().name().toLowerCase(Locale.ROOT));
    }

    private String world(Friend f) {
        return f.worldName() == null ? "?" : f.worldName();
    }

    private int statusColor(Connectivity connectivity) {
        return switch (connectivity) {
            case ONLINE -> 0xFF43B581;
            case STALE -> 0xFFFAA61A;
            case OFFLINE -> 0xFF747F8D;
            case UNKNOWN -> 0xFF4F545C;
        };
    }
}
