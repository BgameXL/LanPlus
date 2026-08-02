package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.api.Connectivity;
import dev.bgame.lanplus.api.Friend;
import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.HostAccessMode;
import dev.bgame.lanplus.api.PresenceSnapshot;
import dev.bgame.lanplus.api.ResolvedUser;
import dev.bgame.lanplus.api.UserProfile;
import dev.bgame.lanplus.client.JoinHelper;
import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.client.SkinTextures;
import dev.bgame.lanplus.friends.FriendsService;
import dev.bgame.lanplus.invites.HostAccessControl;
import dev.bgame.lanplus.invites.InviteService;
import dev.bgame.lanplus.presence.PresenceManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class FriendsScreen extends Screen {

    private enum Tab { FRIENDS, JOIN, ADD, DETAILS }

    private static final Tab[] TABS = Tab.values();
    private static final int MARGIN = 20;
    private static final int MAX_W = 540;
    private static final int LEFT_W = 160;
    private static final int GAP = 10;
    private static final int PANE_H = 320;
    private static final int HEADER_H = 34;
    private static final int FOOTER_H = 28;
    private static final int ROW_H = 24;
    private static final int MENU_ROW_H = 14;

    private int leftX;
    private int rightX;
    private int rightW;
    private int contentRight;
    private int headerTop;
    private int tabsTop;
    private int paneTop;
    private int paneBottom;

    private final Screen parent;
    private Tab tab = Tab.FRIENDS;
    private UUID selectedUuid;
    private EditBox addBox;
    private EditBox joinBox;
    private static final long STATUS_DISPLAY_MS = 4000;
    private Component status;
    private long statusUntil;
    private boolean primed;
    private boolean showAddress;
    private boolean showCode;

    private UUID contextUuid;
    private int contextX;
    private int contextY;
    private List<ContextEntry> contextEntries = List.of();

    private record ContextEntry(Component label, Runnable action, boolean enabled) {}

    public FriendsScreen(Screen parent) {
        super(Component.translatable("gui.lanplus.title"));
        this.parent = parent;
    }

    private void layout() {
        int contentW = Math.min(this.width - 2 * MARGIN, MAX_W);
        leftX = (this.width - contentW) / 2;
        contentRight = leftX + contentW;
        rightX = leftX + LEFT_W + GAP;
        rightW = contentRight - rightX;
        int paneH = Math.min(PANE_H, Math.max(120, this.height - 2 * MARGIN - HEADER_H - FOOTER_H));
        int blockH = HEADER_H + paneH + FOOTER_H;
        headerTop = Math.max(MARGIN, (this.height - blockH) / 2);
        tabsTop = headerTop + 14;
        paneTop = headerTop + HEADER_H;
        paneBottom = paneTop + paneH;
    }

    @Override
    protected void init() {
        layout();

        // "My Profile" sits in the top bar (right-aligned, beside the tabs) so it reads as a primary action
        addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.profile.mine"), b -> doMyProfile())
                .bounds(contentRight - 96, headerTop + 13, 96, 18).build());

        int btnY = paneBottom + 8;
        addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.refresh"), b -> doRefresh())
                .bounds(contentRight - 180, btnY, 88, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(contentRight - 88, btnY, 88, 20).build());

        if (tab == Tab.ADD) {
            addBox = new EditBox(this.font, rightX + 6, paneTop + 22, rightW - 84, 20,
                    Component.translatable("gui.lanplus.add.hint"));
            addBox.setHint(Component.translatable("gui.lanplus.add.hint"));
            addRenderableWidget(addBox);
            addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.add.button"), b -> doAdd())
                    .bounds(rightX + rightW - 72, paneTop + 22, 66, 20).build());
        } else if (tab == Tab.JOIN) {
            joinBox = new EditBox(this.font, rightX + 6, paneTop + 22, rightW - 84, 20,
                    Component.translatable("gui.lanplus.join.hint"));
            joinBox.setHint(Component.translatable("gui.lanplus.join.hint"));
            addRenderableWidget(joinBox);
            addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.join.button"), b -> doJoinByCode())
                    .bounds(rightX + rightW - 72, paneTop + 22, 66, 20).build());
        } else if (tab == Tab.DETAILS) {
            HostInfo info = hostInfo();
            if (info != null) {
                int bx = rightX + rightW - 116;
                addRenderableWidget(Button.builder(showLabel(showAddress), b -> { showAddress = !showAddress; rebuildWidgets(); })
                        .bounds(bx, paneTop + 62, 52, 18).build());
                addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.details.copy"), b -> copyToClipboard(info.address()))
                        .bounds(bx + 56, paneTop + 62, 52, 18).build());
                addRenderableWidget(Button.builder(showLabel(showCode), b -> { showCode = !showCode; rebuildWidgets(); })
                        .bounds(bx, paneTop + 94, 52, 18).build());
                addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.details.copy"), b -> copyToClipboard(info.code()))
                        .bounds(bx + 56, paneTop + 94, 52, 18).build());
            }
        }

        if (!primed) {
            primed = true;
            doRefresh();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        LanPlusUi.backdrop(g, this.width, this.height);
        layout();

        g.drawString(this.font, this.title, leftX, headerTop, LanPlusUi.TEXT);
        boolean online = isOnline();
        Component conn = online ? Component.translatable("gui.lanplus.status.connected")
                : Component.translatable("gui.lanplus.status.local");
        g.drawString(this.font, conn, contentRight - this.font.width(conn), headerTop + 2,
                online ? LanPlusUi.GREEN : LanPlusUi.FAINT);

        renderTabs(g, mouseX, mouseY);
        LanPlusUi.panel(g, leftX, paneTop, leftX + LEFT_W, paneBottom);
        LanPlusUi.panel(g, rightX, paneTop, rightX + rightW, paneBottom);

        switch (tab) {
            case FRIENDS -> renderFriendList(g, mouseX, mouseY, paneBottom);
            case ADD -> renderRequests(g, paneBottom);
            case JOIN, DETAILS -> { /* list pane unused on these tabs */ }
        }
        renderDetail(g, rightX, rightW);

        if (status != null) {
            if (System.currentTimeMillis() < statusUntil) {
                g.drawString(this.font, status, leftX, paneBottom + 13, LanPlusUi.MUTED);
            } else {
                status = null;
            }
        }
        super.render(g, mouseX, mouseY, partialTick);
        renderContextMenu(g, mouseX, mouseY);
    }

    private Component tabLabel(Tab t) {
        return Component.translatable("gui.lanplus.tab." + t.name().toLowerCase(Locale.ROOT));
    }

    private int tabX(int index) {
        int x = leftX;
        for (int i = 0; i < index; i++) {
            x += this.font.width(tabLabel(TABS[i])) + 18;
        }
        return x;
    }

    private void renderTabs(GuiGraphics g, int mouseX, int mouseY) {
        for (int i = 0; i < TABS.length; i++) {
            Component label = tabLabel(TABS[i]);
            int x = tabX(i);
            int w = this.font.width(label);
            boolean active = tab == TABS[i];
            boolean hover = mouseX >= x - 2 && mouseX < x + w + 4 && mouseY >= tabsTop - 3 && mouseY < tabsTop + 12;
            g.drawString(this.font, label, x, tabsTop, active ? LanPlusUi.TEXT
                    : hover ? LanPlusUi.MUTED : LanPlusUi.FAINT);
            if (active) {
                g.fill(x, tabsTop + 11, x + w, tabsTop + 12, LanPlusUi.BLURPLE);
            }
        }
    }

    private Tab tabAt(double mouseX, double mouseY) {
        if (mouseY < tabsTop - 3 || mouseY >= tabsTop + 12) {
            return null;
        }
        for (int i = 0; i < TABS.length; i++) {
            int x = tabX(i);
            int w = this.font.width(tabLabel(TABS[i]));
            if (mouseX >= x - 2 && mouseX < x + w + 4) {
                return TABS[i];
            }
        }
        return null;
    }

    private void renderFriendList(GuiGraphics g, int mouseX, int mouseY, int paneBottom) {
        List<Friend> list = friends();
        if (list.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.friends.empty"),
                    leftX + LEFT_W / 2, paneTop + 30, LanPlusUi.FAINT);
            return;
        }
        int y = paneTop + 2;
        for (Friend f : list) {
            if (y + ROW_H > paneBottom) {
                break;
            }
            boolean selected = f.uuid().equals(selectedUuid);
            boolean hover = mouseX >= leftX && mouseX <= leftX + LEFT_W && mouseY >= y && mouseY < y + ROW_H;
            if (selected || hover) {
                g.fill(leftX + 1, y, leftX + LEFT_W - 1, y + ROW_H,
                        selected ? LanPlusUi.BLURPLE_TINT : 0x14FFFFFF);
            }
            if (selected) {
                g.fill(leftX + 1, y, leftX + 3, y + ROW_H, LanPlusUi.BLURPLE);
            }
            drawAvatar(g, f.uuid(), leftX + 6, y + 3, 18);
            g.fill(leftX + 28, y + ROW_H / 2 - 3, leftX + 34, y + ROW_H / 2 + 3, statusColor(f.connectivity()));
            g.drawString(this.font, f.username(), leftX + 40, y + 4, LanPlusUi.TEXT, false);
            g.drawString(this.font, secondaryText(f), leftX + 40, y + 14, LanPlusUi.MUTED, false);
            y += ROW_H;
        }
    }

    private void drawAvatar(GuiGraphics g, UUID uuid, int x, int y, int size) {
        SkinTextures textures = LanPlusClient.skinTextures();
        SkinTextures.Resolved resolved = textures == null ? null : textures.get(uuid);
        ResourceLocation tex = resolved != null ? resolved.texture() : DefaultPlayerSkin.getDefaultSkin(uuid);
        PlayerFaceRenderer.draw(g, tex, x, y, size);
    }

    private void renderRequests(GuiGraphics g, int paneBottom) {
        g.drawString(this.font, Component.translatable("gui.lanplus.requests.title"),
                leftX + 6, paneTop + 4, LanPlusUi.TEXT, false);
        List<ResolvedUser> reqs = requests();
        if (reqs.isEmpty()) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.requests.empty"),
                    leftX + LEFT_W / 2, paneTop + 34, LanPlusUi.FAINT);
            return;
        }
        int y = paneTop + 18;
        for (ResolvedUser r : reqs) {
            if (y + 20 > paneBottom) {
                break;
            }
            g.drawString(this.font, r.username(), leftX + 6, y + 5, LanPlusUi.TEXT, false);
            int ax = leftX + LEFT_W - 42;
            int dx = leftX + LEFT_W - 20;
            g.fill(ax, y + 2, ax + 18, y + 16, LanPlusUi.GREEN);
            g.drawString(this.font, "+", ax + 6, y + 5, LanPlusUi.TEXT, false);
            g.fill(dx, y + 2, dx + 18, y + 16, LanPlusUi.RED);
            g.drawString(this.font, "x", dx + 6, y + 5, LanPlusUi.TEXT, false);
            y += 20;
        }
    }

    private void renderDetail(GuiGraphics g, int x, int w) {
        if (tab == Tab.ADD) {
            LanPlusUi.header(g, this.font, Component.translatable("gui.lanplus.add.title"), x + 6, paneTop + 6, w - 12);
            g.drawString(this.font, Component.translatable("gui.lanplus.add.note"), x + 6, paneTop + 48, LanPlusUi.FAINT, false);
            UserProfile self = localProfile();
            Component code = self != null && self.friendCode() != null
                    ? Component.translatable("gui.lanplus.add.yourcode", self.friendCode())
                    : Component.translatable("gui.lanplus.add.yourcode.unknown");
            g.drawString(this.font, code, x + 6, paneTop + 64, LanPlusUi.GREEN, false);
            return;
        }
        if (tab == Tab.JOIN) {
            LanPlusUi.header(g, this.font, Component.translatable("gui.lanplus.join.title"), x + 6, paneTop + 6, w - 12);
            g.drawString(this.font, Component.translatable("gui.lanplus.join.note"), x + 6, paneTop + 48, LanPlusUi.FAINT, false);
            return;
        }
        if (tab == Tab.DETAILS) {
            renderDetails(g, x, w);
            return;
        }
        Friend f = selectedFriend();
        if (f == null) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.detail.none"),
                    x + w / 2, paneTop + 30, LanPlusUi.FAINT);
            return;
        }
        drawAvatar(g, f.uuid(), x + 8, paneTop + 8, 24);
        g.drawString(this.font, f.username(), x + 40, paneTop + 10, LanPlusUi.TEXT, false);
        g.fill(x + 40, paneTop + 22, x + 46, paneTop + 28, statusColor(f.connectivity()));
        g.drawString(this.font, connectivityText(f), x + 50, paneTop + 22, LanPlusUi.MUTED, false);
        g.fill(x + 8, paneTop + 38, x + w - 8, paneTop + 39, LanPlusUi.DIVIDER);
        g.drawString(this.font, secondaryText(f), x + 8, paneTop + 46, LanPlusUi.MUTED, false);
    }

    private void renderDetails(GuiGraphics g, int x, int w) {
        LanPlusUi.header(g, this.font, Component.translatable("gui.lanplus.details.title"), x + 6, paneTop + 6, w - 12);
        HostInfo info = hostInfo();
        if (info == null) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.details.nothosting"),
                    x + w / 2, paneTop + 40, LanPlusUi.FAINT);
            return;
        }
        g.drawString(this.font, Component.translatable("gui.lanplus.details.world", safe(info.world())),
                x + 6, paneTop + 26, LanPlusUi.MUTED, false);
        g.drawString(this.font, Component.translatable("gui.lanplus.details.access", modeName(info.mode())),
                x + 6, paneTop + 38, LanPlusUi.MUTED, false);
        g.drawString(this.font, Component.translatable("gui.lanplus.details.address"), x + 6, paneTop + 54, LanPlusUi.FAINT, false);
        g.drawString(this.font, showAddress ? safe(info.address()) : mask(info.address()), x + 6, paneTop + 66, LanPlusUi.GREEN, false);
        g.drawString(this.font, Component.translatable("gui.lanplus.details.code"), x + 6, paneTop + 86, LanPlusUi.FAINT, false);
        g.drawString(this.font, showCode ? safe(info.code()) : mask(info.code()), x + 6, paneTop + 98, LanPlusUi.GREEN, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contextUuid != null) {
            if (button == 0 && handleContextClick(mouseX, mouseY)) {
                return true;
            }
            closeContextMenu();
        }
        if (button == 0) {
            Tab clicked = tabAt(mouseX, mouseY);
            if (clicked != null) {
                this.tab = clicked;
                rebuildWidgets();
                return true;
            }
        }
        if (tab == Tab.FRIENDS && button == 1) {
            Friend f = friendAt(mouseX, mouseY);
            if (f != null) {
                openContextMenu(f, (int) mouseX, (int) mouseY);
                return true;
            }
        }
        if (tab == Tab.FRIENDS && button == 0) {
            Friend f = friendAt(mouseX, mouseY);
            if (f != null) {
                selectedUuid = f.uuid();
                rebuildWidgets();
                return true;
            }
        }
        if (tab == Tab.ADD && button == 0) {
            int y = paneTop + 18;
            for (ResolvedUser r : requests()) {
                if (y + 20 > paneBottom) {
                    break;
                }
                int ax = leftX + LEFT_W - 42;
                int dx = leftX + LEFT_W - 20;
                if (mouseY >= y + 2 && mouseY <= y + 16) {
                    if (mouseX >= ax && mouseX <= ax + 18) {
                        doAccept(r.uuid());
                        return true;
                    }
                    if (mouseX >= dx && mouseX <= dx + 18) {
                        doDecline(r.uuid());
                        return true;
                    }
                }
                y += 20;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (contextUuid != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeContextMenu();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    private void openContextMenu(Friend f, int x, int y) {
        selectedUuid = f.uuid();
        contextUuid = f.uuid();
        contextX = x;
        contextY = y;
        List<ContextEntry> entries = new ArrayList<>();
        entries.add(new ContextEntry(Component.translatable("gui.lanplus.action.viewprofile"), this::doViewProfile, true));
        boolean canJoin = f.state() == GameplayState.HOSTING && f.joinCode() != null;
        entries.add(new ContextEntry(Component.translatable("gui.lanplus.action.join"), this::doJoin, canJoin));
        entries.add(new ContextEntry(
                Component.translatable(f.muted() ? "gui.lanplus.action.unmute" : "gui.lanplus.action.mute"),
                this::doToggleMute, true));
        entries.add(new ContextEntry(
                Component.translatable(f.blocked() ? "gui.lanplus.action.unblock" : "gui.lanplus.action.block"),
                this::doToggleBlock, true));
        entries.add(new ContextEntry(Component.translatable("gui.lanplus.action.remove"), this::doRemove, true));
        contextEntries = entries;
    }

    private void closeContextMenu() {
        contextUuid = null;
        contextEntries = List.of();
    }

    private boolean handleContextClick(double mouseX, double mouseY) {
        if (contextEntries.isEmpty()) {
            return false;
        }
        int x = menuX();
        int w = menuWidth();
        if (mouseX < x || mouseX > x + w) {
            return false;
        }
        int ey = menuY() + 2;
        for (ContextEntry e : contextEntries) {
            if (mouseY >= ey && mouseY < ey + MENU_ROW_H) {
                closeContextMenu();
                if (e.enabled()) {
                    e.action().run();
                }
                return true;
            }
            ey += MENU_ROW_H;
        }
        return false;
    }

    private Friend friendAt(double mouseX, double mouseY) {
        if (mouseX < leftX || mouseX > leftX + LEFT_W) {
            return null;
        }
        int y = paneTop + 2;
        for (Friend f : friends()) {
            if (mouseY >= y && mouseY < y + ROW_H) {
                return f;
            }
            y += ROW_H;
            if (y + ROW_H > paneBottom) {
                break;
            }
        }
        return null;
    }

    private int menuWidth() {
        int w = 0;
        for (ContextEntry e : contextEntries) {
            w = Math.max(w, this.font.width(e.label()));
        }
        return w + 16;
    }

    private int menuHeight() {
        return contextEntries.size() * MENU_ROW_H + 4;
    }

    private int menuX() {
        return Math.min(contextX, this.width - menuWidth() - 2);
    }

    private int menuY() {
        return Math.min(contextY, this.height - menuHeight() - 2);
    }

    private void renderContextMenu(GuiGraphics g, int mouseX, int mouseY) {
        if (contextUuid == null || contextEntries.isEmpty()) {
            return;
        }
        int x = menuX();
        int y = menuY();
        int w = menuWidth();
        int h = menuHeight();
        g.fill(x, y, x + w, y + h, LanPlusUi.SURFACE_RAISED);
        LanPlusUi.border(g, x, y, x + w, y + h);
        int ey = y + 2;
        for (ContextEntry e : contextEntries) {
            boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= ey && mouseY < ey + MENU_ROW_H;
            if (hover && e.enabled()) {
                g.fill(x + 1, ey, x + w - 1, ey + MENU_ROW_H, LanPlusUi.BLURPLE_TINT);
            }
            int color = !e.enabled() ? LanPlusUi.FAINT : (hover ? LanPlusUi.TEXT : LanPlusUi.MUTED);
            g.drawString(this.font, e.label(), x + 6, ey + 3, color, false);
            ey += MENU_ROW_H;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // Actions
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
        setStatus(Component.translatable("gui.lanplus.join.connecting", friend.username()));
        invites.resolve(friend.joinCode()).whenComplete((invite, err) -> this.minecraft.execute(() -> {
            if (err == null && invite != null && invite.address() != null) {
                connectTo(invite.address());
            } else {
                setStatus(Component.translatable("gui.lanplus.join.failed"));
            }
        }));
    }

    private void doJoinByCode() {
        InviteService invites = LanPlusClient.invites();
        if (invites == null || joinBox == null) {
            return;
        }
        String code = joinBox.getValue().trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            return;
        }
        setStatus(Component.translatable("gui.lanplus.join.connecting", code));
        invites.resolve(code).whenComplete((invite, err) -> this.minecraft.execute(() -> {
            if (err == null && invite != null && invite.address() != null) {
                connectTo(invite.address());
            } else {
                setStatus(Component.translatable("gui.lanplus.join.failed"));
            }
        }));
    }

    private void doRemove() {
        Friend friend = selectedFriend();
        FriendsService friends = LanPlusClient.friends();
        if (friend == null || friends == null) {
            return;
        }
        setStatus(Component.translatable("gui.lanplus.removed", friend.username()));
        friends.remove(friend.uuid()).whenComplete((ok, err) -> this.minecraft.execute(() -> {
            selectedUuid = null;
            rebuildWidgets();
        }));
    }

    private void doToggleMute() {
        Friend friend = selectedFriend();
        FriendsService friends = LanPlusClient.friends();
        if (friend == null || friends == null) {
            return;
        }
        boolean muted = friend.muted();
        (muted ? friends.unmute(friend.uuid()) : friends.mute(friend.uuid()))
                .whenComplete((ok, err) -> this.minecraft.execute(() -> {
                    setStatus(Component.translatable(
                            muted ? "gui.lanplus.muted.off" : "gui.lanplus.muted.on", friend.username()));
                    rebuildWidgets();
                }));
    }

    private void doToggleBlock() {
        Friend friend = selectedFriend();
        FriendsService friends = LanPlusClient.friends();
        if (friend == null || friends == null) {
            return;
        }
        boolean blocked = friend.blocked();
        (blocked ? friends.unblock(friend.uuid()) : friends.block(friend.uuid()))
                .whenComplete((ok, err) -> this.minecraft.execute(() -> {
                    setStatus(Component.translatable(
                            blocked ? "gui.lanplus.blocked.off" : "gui.lanplus.blocked.on", friend.username()));
                    rebuildWidgets();
                }));
    }

    private void doViewProfile() {
        Friend friend = selectedFriend();
        if (friend != null) {
            this.minecraft.setScreen(new ProfileScreen(this, friend.uuid()));
        }
    }

    private void doMyProfile() {
        UUID id = LanPlusClient.selfUuid();
        if (id != null) {
            this.minecraft.setScreen(new ProfileScreen(this, id));
        }
    }

    private void doAccept(UUID requesterUuid) {
        FriendsService friends = LanPlusClient.friends();
        if (friends != null) {
            friends.accept(requesterUuid);
            setStatus(Component.translatable("gui.lanplus.requests.accepted"));
        }
    }

    private void doDecline(UUID requesterUuid) {
        FriendsService friends = LanPlusClient.friends();
        if (friends != null) {
            friends.decline(requesterUuid);
            setStatus(Component.translatable("gui.lanplus.requests.declined"));
        }
    }

    private void connectTo(String address) {
        JoinHelper.connect(this.minecraft, address);
    }

    private void setStatus(Component message) {
        this.status = message;
        this.statusUntil = System.currentTimeMillis() + STATUS_DISPLAY_MS;
    }

    private UserProfile localProfile() {
        FriendsService friends = LanPlusClient.friends();
        return friends == null ? null : friends.localProfile();
    }

    private record HostInfo(String world, HostAccessMode mode, String address, String code) {}

    private HostInfo hostInfo() {
        PresenceManager presence = LanPlusClient.presence();
        if (presence == null) {
            return null;
        }
        PresenceSnapshot s = presence.current();
        if (s == null || s.state() != GameplayState.HOSTING || s.address() == null) {
            return null;
        }
        return new HostInfo(s.worldName(), HostAccessControl.mode(), s.address(), s.joinCode());
    }

    private Component modeName(HostAccessMode mode) {
        return Component.translatable("gui.lanplus.host.access." + mode.name().toLowerCase(Locale.ROOT));
    }

    private void copyToClipboard(String value) {
        if (value != null && !value.isEmpty()) {
            this.minecraft.keyboardHandler.setClipboard(value);
            setStatus(Component.translatable("gui.lanplus.details.copied"));
        }
    }

    private Component showLabel(boolean shown) {
        return Component.translatable(shown ? "gui.lanplus.details.hide" : "gui.lanplus.details.show");
    }

    private static String safe(String s) {
        return s == null || s.isEmpty() ? "—" : s;
    }

    private static String mask(String s) {
        return s == null || s.isEmpty() ? "—" : "*".repeat(Math.min(s.length(), 24));
    }

    // helpers
    private List<Friend> friends() {
        FriendsService friends = LanPlusClient.friends();
        return friends == null ? List.of() : friends.friends();
    }

    private List<ResolvedUser> requests() {
        FriendsService friends = LanPlusClient.friends();
        return friends == null ? List.of() : friends.requests();
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
        if (f.blocked()) {
            return Component.translatable("gui.lanplus.rel.blocked");
        }
        if (f.muted()) {
            return Component.translatable("gui.lanplus.rel.muted");
        }
        if (f.connectivity() != Connectivity.ONLINE && f.connectivity() != Connectivity.STALE) {
            // OFFLINE and UNKNOWN (no presence record) both read as offline; only ONLINE/STALE are live.
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
            case ONLINE -> LanPlusUi.GREEN;
            case STALE -> LanPlusUi.AMBER;
            case OFFLINE -> LanPlusUi.FAINT;
            case UNKNOWN -> 0xFF4F545C;
        };
    }
}
