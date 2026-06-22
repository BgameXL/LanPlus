package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.api.Connectivity;
import dev.bgame.lanplus.api.Friend;
import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.ModpackRef;
import dev.bgame.lanplus.api.Profile;
import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.client.SkinTextures;
import dev.bgame.lanplus.client.gui.ProfilePromptCatalog.Prompt;
import dev.bgame.lanplus.friends.FriendsService;
import dev.bgame.lanplus.profiles.ProfilesService;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProfileScreen extends Screen {

    private static final int PANEL_BG = 0xC0101018;
    private static final int SIDEBAR_BG = 0xC00C0C12;
    private static final int BORDER = 0x33FFFFFF;
    private static final int DIVIDER = 0x22FFFFFF;
    private static final int HEADER_COLOR = 0xFFC2C7CE;
    private static final int SECTION_GAP = 12;
    private static final int MARGIN = 10;
    private static final int MAX_LAYOUT_W = 620;
    private static final int SIDEBAR_W = 250;
    private static final int GAP = 8;
    private static final int CONTENT_TOP = 32;
    private static final String[] PLATFORMS =
            {"discord", "instagram", "twitter", "youtube", "twitch", "tiktok", "paypal", "kofi"};
    private static final String[] PRONOUN_CYCLE = {null, "he/him", "she/her", "they/them"};
    private static final int MAX_SLOTS = 3;

    private final Screen parent;
    private final UUID uuid;
    private final boolean own;

    private Profile profile;
    private boolean loaded;
    private boolean editing;
    private Component status;

    // scroll + manual hit testing
    private int scrollY;
    private int maxScroll;
    private int panelX, panelTop, panelRight, panelBottom;
    private final List<Hit> hits = new ArrayList<>();

    // edit widgets
    private EditBox bioBox;
    private Button pronounButton;
    private Button invisibleButton;
    private final EditBox[] linkBoxes = new EditBox[PLATFORMS.length];
    private int pronounIndex;
    private boolean invisibleToggle;

    // edit: question slots
    private final String[] slotPromptId = new String[MAX_SLOTS];
    private final String[] slotFreeValue = new String[MAX_SLOTS];
    private final String[] slotChoiceToken = new String[MAX_SLOTS];
    private final EditBox[] slotFreeBox = new EditBox[MAX_SLOTS];

    // edit: modpack favorite + visibility toggles
    private Button favoriteButton;
    private Button favoriteVisibleButton;
    private Button playingVisibleButton;
    private List<ModpackRef> modpackCatalog = List.of();
    private boolean modpacksLoaded;
    private String editFavoriteId;
    private boolean favoriteVisibleToggle;
    private boolean playingVisibleToggle;

    public ProfileScreen(Screen parent, UUID uuid) {
        super(Component.translatable("gui.lanplus.profile.title"));
        this.parent = parent;
        this.uuid = uuid;
        this.own = isOwn(uuid);
    }

    private static boolean isOwn(UUID uuid) {
        try {
            return uuid != null && uuid.equals(Minecraft.getInstance().getUser().getProfileId());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean in(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private int layoutWidth() {
        return Math.min(this.width - 2 * MARGIN, MAX_LAYOUT_W);
    }

    private int layoutLeft() {
        return (this.width - layoutWidth()) / 2;
    }

    @Override
    protected void init() {
        if (!loaded) {
            loadProfile();
        }
        int left = layoutLeft();
        int right = left + layoutWidth();

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(right - 80, this.height - 28, 80, 20).build());

        if (loaded && profile != null) {
            if (editing) {
                buildEditWidgets();
                addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.profile.save"), b -> doSave())
                        .bounds(left, this.height - 28, 90, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.profile.cancel"),
                                b -> { editing = false; status = null; rebuildWidgets(); })
                        .bounds(left + 96, this.height - 28, 90, 20).build());
            } else {
                addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.profile.back"), b -> onClose())
                        .bounds(left, 6, 80, 20).build());
                if (own) {
                    addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.profile.edit"),
                                    b -> { editing = true; primeEdit(); rebuildWidgets(); })
                            .bounds(left, this.height - 28, 110, 20).build());
                }
            }
        }
    }

    private void buildEditWidgets() {
        int left = layoutLeft();
        int w = layoutWidth();
        bioBox = new EditBox(this.font, left, CONTENT_TOP + 12, w, 20, Component.translatable("gui.lanplus.profile.about"));
        bioBox.setMaxLength(300);
        bioBox.setHint(Component.translatable("gui.lanplus.profile.bio.hint"));
        bioBox.setValue(profile.bio() == null ? "" : profile.bio());
        addRenderableWidget(bioBox);

        pronounButton = Button.builder(pronounLabel(), b -> {
            pronounIndex = (pronounIndex + 1) % PRONOUN_CYCLE.length;
            pronounButton.setMessage(pronounLabel());
        }).bounds(left, CONTENT_TOP + 38, 150, 20).build();
        addRenderableWidget(pronounButton);

        invisibleButton = Button.builder(invisibleLabel(), b -> {
            invisibleToggle = !invisibleToggle;
            invisibleButton.setMessage(invisibleLabel());
        }).bounds(left + 160, CONTENT_TOP + 38, 170, 20).build();
        addRenderableWidget(invisibleButton);

        int cx = left + w / 2;
        int linksTop = CONTENT_TOP + 78;
        int colW = w / 2 - 6;
        int labelW = 58;
        for (int i = 0; i < PLATFORMS.length; i++) {
            int x = (i / 4 == 0 ? left : cx + 6) + labelW;
            int y = linksTop + (i % 4) * 22;
            EditBox box = new EditBox(this.font, x, y, colW - labelW, 18, Component.literal(PLATFORMS[i]));
            box.setMaxLength(40);
            String cur = profile.link(PLATFORMS[i]);
            box.setValue(cur == null ? "" : cur);
            linkBoxes[i] = box;
            addRenderableWidget(box);
        }

        buildSlotWidgets();
        buildModpackWidgets();
    }

    private void buildModpackWidgets() {
        int left = layoutLeft();
        int w = layoutWidth();
        int top = CONTENT_TOP + 264;
        favoriteButton = Button.builder(favoriteLabel(), b -> cycleFavorite())
                .bounds(left, top + 14, w, 20).build();
        addRenderableWidget(favoriteButton);

        int half = w / 2 - 3;
        favoriteVisibleButton = Button.builder(favoriteVisibleLabel(), b -> {
            favoriteVisibleToggle = !favoriteVisibleToggle;
            favoriteVisibleButton.setMessage(favoriteVisibleLabel());
        }).bounds(left, top + 38, half, 20).build();
        addRenderableWidget(favoriteVisibleButton);

        playingVisibleButton = Button.builder(playingVisibleLabel(), b -> {
            playingVisibleToggle = !playingVisibleToggle;
            playingVisibleButton.setMessage(playingVisibleLabel());
        }).bounds(left + w / 2 + 3, top + 38, half, 20).build();
        addRenderableWidget(playingVisibleButton);
    }

    private void cycleFavorite() {
        if (!modpacksLoaded) {
            return;
        }
        List<String> options = new ArrayList<>();
        options.add(null);
        for (ModpackRef m : modpackCatalog) {
            options.add(m.modpackId());
        }
        int cur = Math.max(0, options.indexOf(editFavoriteId));
        editFavoriteId = options.get((cur + 1) % options.size());
        favoriteButton.setMessage(favoriteLabel());
    }

    private void loadModpacks() {
        ProfilesService svc = LanPlusClient.profiles();
        if (svc == null) {
            modpacksLoaded = true;
            return;
        }
        svc.modpacks().whenComplete((list, ex) -> this.minecraft.execute(() -> {
            this.modpackCatalog = (ex == null && list != null) ? list : List.of();
            this.modpacksLoaded = true;
            if (this.minecraft.screen == this && editing) {
                rebuildWidgets();
            }
        }));
    }

    private void buildSlotWidgets() {
        int left = layoutLeft();
        int contentW = layoutWidth();
        int pickerW = pickerWidth();
        int answerX = left + pickerW + 6;
        int answerW = contentW - pickerW - 6;
        int slotsTop = CONTENT_TOP + 184;
        for (int i = 0; i < MAX_SLOTS; i++) {
            slotFreeBox[i] = null;
            int y = slotsTop + i * 24;
            final int slot = i;
            addRenderableWidget(Button.builder(pickerLabel(slot), b -> changeSlotPrompt(slot))
                    .bounds(left, y, pickerW, 20).build());

            Prompt p = ProfilePromptCatalog.byId(slotPromptId[i]);
            if (p == null) {
                continue;
            }
            if (p.type() == ProfilePromptCatalog.Type.FREE) {
                EditBox box = new EditBox(this.font, answerX, y, answerW, 20, Component.empty());
                box.setMaxLength(140);
                box.setHint(Component.translatable("gui.lanplus.profile.questions.hint"));
                box.setValue(slotFreeValue[i] == null ? "" : slotFreeValue[i]);
                slotFreeBox[i] = box;
                addRenderableWidget(box);
            } else {
                addRenderableWidget(Button.builder(choiceLabel(slot), b -> cycleChoice(slot))
                        .bounds(answerX, y, answerW, 20).build());
            }
        }
    }

    private void changeSlotPrompt(int slot) {
        captureFreeValues();
        String next = nextPrompt(slot);
        slotPromptId[slot] = next;
        Prompt p = ProfilePromptCatalog.byId(next);
        if (p == null) {
            slotFreeValue[slot] = null;
            slotChoiceToken[slot] = null;
        } else if (p.type() == ProfilePromptCatalog.Type.FREE) {
            slotFreeValue[slot] = "";
        } else {
            slotChoiceToken[slot] = p.choices().get(0);
        }
        rebuildWidgets();
    }

    private void cycleChoice(int slot) {
        Prompt p = ProfilePromptCatalog.byId(slotPromptId[slot]);
        if (p == null || p.choices().isEmpty()) {
            return;
        }
        int cur = p.choices().indexOf(slotChoiceToken[slot]);
        slotChoiceToken[slot] = p.choices().get((cur + 1) % p.choices().size());
        rebuildWidgets();
    }

    private String nextPrompt(int slot) {
        List<String> options = new ArrayList<>();
        options.add(null);
        for (Prompt p : ProfilePromptCatalog.PROMPTS) {
            if (!usedInOtherSlot(p.id(), slot)) {
                options.add(p.id());
            }
        }
        int cur = Math.max(0, options.indexOf(slotPromptId[slot]));
        return options.get((cur + 1) % options.size());
    }

    private boolean usedInOtherSlot(String id, int slot) {
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (i != slot && id.equals(slotPromptId[i])) {
                return true;
            }
        }
        return false;
    }

    private void captureFreeValues() {
        for (int i = 0; i < MAX_SLOTS; i++) {
            if (slotFreeBox[i] != null) {
                slotFreeValue[i] = slotFreeBox[i].getValue();
            }
        }
    }

    private void primeEdit() {
        pronounIndex = 0;
        invisibleToggle = profile.invisible();
        if (profile.pronouns() != null) {
            for (int i = 0; i < PRONOUN_CYCLE.length; i++) {
                if (profile.pronouns().equals(PRONOUN_CYCLE[i])) {
                    pronounIndex = i;
                    break;
                }
            }
        }
        for (int i = 0; i < MAX_SLOTS; i++) {
            slotPromptId[i] = null;
            slotFreeValue[i] = null;
            slotChoiceToken[i] = null;
        }
        int slot = 0;
        for (Prompt p : ProfilePromptCatalog.PROMPTS) {
            if (slot >= MAX_SLOTS) {
                break;
            }
            String answer = profile.prompt(p.id());
            if (answer == null) {
                continue;
            }
            slotPromptId[slot] = p.id();
            if (p.type() == ProfilePromptCatalog.Type.FREE) {
                slotFreeValue[slot] = answer;
            } else {
                slotChoiceToken[slot] = p.choices().contains(answer) ? answer : p.choices().get(0);
            }
            slot++;
        }
        editFavoriteId = profile.favorite() == null ? null : profile.favorite().modpackId();
        favoriteVisibleToggle = profile.favoriteVisible();
        playingVisibleToggle = profile.currentlyPlayingVisible();
        if (!modpacksLoaded) {
            loadModpacks();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFFFF);

        if (!loaded) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.profile.loading"),
                    this.width / 2, this.height / 2, 0xFF888888);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        if (profile == null) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.profile.unavailable"),
                    this.width / 2, this.height / 2, 0xFF888888);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        hits.clear();
        if (editing) {
            renderEditDecor(g);
        } else {
            renderSidebar(g);
            renderPanel(g, mouseX, mouseY);
        }

        if (status != null) {
            g.drawString(this.font, status, layoutLeft(), this.height - 42, 0xFFE0A030);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderEditDecor(GuiGraphics g) {
        int left = layoutLeft();
        int w = layoutWidth();
        int right = left + w;
        editHeader(g, Component.translatable("gui.lanplus.profile.about"), CONTENT_TOP, left, right);
        editHeader(g, Component.translatable("gui.lanplus.profile.links"), CONTENT_TOP + 64, left, right);
        int cx = left + w / 2;
        int linksTop = CONTENT_TOP + 78;
        for (int i = 0; i < PLATFORMS.length; i++) {
            int x = (i / 4 == 0 ? left : cx + 6);
            int y = linksTop + (i % 4) * 22;
            g.drawString(this.font, PLATFORMS[i], x, y + 5, 0xFF9AA0A6);
        }
        editHeader(g, Component.translatable("gui.lanplus.profile.questions"), CONTENT_TOP + 170, left, right);
        editHeader(g, Component.translatable("gui.lanplus.profile.modpack"), CONTENT_TOP + 264, left, right);
    }

    private void editHeader(GuiGraphics g, Component label, int y, int left, int right) {
        g.drawString(this.font, label, left, y, HEADER_COLOR);
        g.fill(left, y + 11, right, y + 12, DIVIDER);
    }

    private void renderSidebar(GuiGraphics g) {
        int left = layoutLeft();
        int top = CONTENT_TOP;
        int bottom = this.height - 34;
        g.fill(left, top, left + SIDEBAR_W, bottom, SIDEBAR_BG);
        drawBorder(g, left, top, left + SIDEBAR_W, bottom, BORDER);

        drawAvatar(g, uuid, left + 6, top + 6, 36);
        int hx = left + 48;
        g.drawString(this.font, profile.username() == null ? "?" : profile.username(), hx, top + 8, 0xFFFFFFFF);
        if (profile.pronouns() != null) {
            int pw = this.font.width(profile.pronouns()) + 6;
            g.fill(hx, top + 20, hx + pw, top + 31, 0x40FFFFFF);
            g.drawString(this.font, profile.pronouns(), hx + 3, top + 22, 0xFFB9C7FF);
        }

        int y = top + 50;
        if (profile.friendCode() != null) {
            g.drawString(this.font, profile.friendCode(), left + 6, y, 0xFF9AA0A6);
            int w = this.font.width(profile.friendCode());
            hits.add(new Hit(left + 6, y - 1, w, 10, () -> copyText(profile.friendCode())));
        }
        y += 14;
        int dotColor = profile.online() ? 0xFF43B581 : 0xFF747F8D;
        g.fill(left + 6, y + 1, left + 12, y + 7, dotColor);
        g.drawString(this.font, presenceLabel(), left + 18, y, profile.online() ? 0xFF55C46A : 0xFF9AA0A6);
        y += 18;

        ModpackRef playing = profile.currentlyPlaying();
        if (playing != null && playing.name() != null) {
            y = renderSidebarModpack(g, left, y, "gui.lanplus.profile.playing", playing);
        }
        ModpackRef favorite = profile.favorite();
        if (favorite != null && favorite.name() != null) {
            y = renderSidebarModpack(g, left, y, "gui.lanplus.profile.favorite.label", favorite);
        }

        renderSidebarFriends(g, left, y, bottom);
    }

    private int renderSidebarModpack(GuiGraphics g, int left, int y, String headerKey, ModpackRef ref) {
        g.drawString(this.font, Component.translatable(headerKey), left + 6, y, 0xFF7A7F86);
        y += 11;
        String name = ellipsize(ref.name(), SIDEBAR_W - 12);
        boolean link = ref.downloadUrl() != null && !ref.downloadUrl().isBlank();
        g.drawString(this.font, name, left + 6, y, link ? 0xFF6CA8FF : 0xFFE0E3E8);
        if (link) {
            hits.add(new Hit(left + 6, y - 1, this.font.width(name), 10, () -> openLink(ref.downloadUrl())));
        }
        return y + 18;
    }

    private void renderSidebarFriends(GuiGraphics g, int left, int y, int bottom) {
        FriendsService svc = LanPlusClient.friends();
        List<Friend> all = svc == null ? List.of() : svc.friends();
        int online = 0;
        for (Friend f : all) {
            if (f.connectivity() == Connectivity.ONLINE || f.connectivity() == Connectivity.STALE) {
                online++;
            }
        }
        Component header = Component.translatable("gui.lanplus.profile.friends");
        g.drawString(this.font, header, left + 6, y, 0xFF7A7F86);
        Component count = Component.translatable("gui.lanplus.profile.friends.count", online, all.size());
        g.drawString(this.font, count, left + SIDEBAR_W - 6 - this.font.width(count), y, 0xFF7A7F86);
        y += 14;

        int rowH = 22;
        int maxRows = Math.max(0, (bottom - y) / rowH);
        int shown = Math.min(all.size(), maxRows);
        for (int i = 0; i < shown; i++) {
            Friend f = all.get(i);
            if (i == maxRows - 1 && all.size() > maxRows) {
                g.drawString(this.font, Component.translatable("gui.lanplus.profile.friends.more", all.size() - i),
                        left + 6, y + 4, 0xFF7A7F86);
                break;
            }
            g.fill(left + 6, y + 5, left + 12, y + 11, friendDotColor(f.connectivity()));
            g.drawString(this.font, f.username(), left + 18, y + 2, 0xFFE0E3E8);
            g.drawString(this.font, friendStatus(f), left + 18, y + 12, 0xFF7A7F86);
            y += rowH;
        }
    }

    private void renderPanel(GuiGraphics g, int mouseX, int mouseY) {
        int left = layoutLeft();
        panelX = left + SIDEBAR_W + GAP;
        panelTop = CONTENT_TOP;
        panelRight = left + layoutWidth();
        panelBottom = this.height - 34;
        int textW = panelRight - panelX - 12;
        g.fill(panelX, panelTop, panelRight, panelBottom, PANEL_BG);
        drawBorder(g, panelX, panelTop, panelRight, panelBottom, BORDER);

        g.enableScissor(panelX, panelTop, panelRight, panelBottom);
        int x = panelX + 8;
        int y = panelTop + 8 - scrollY;

        y = sectionHeader(g, x, y, x + textW, Component.translatable("gui.lanplus.profile.about"));
        String bio = profile.bio();
        if (bio == null || bio.isBlank()) {
            g.drawString(this.font, Component.translatable("gui.lanplus.profile.nobio"), x, y, 0xFF6A6E74);
            y += 12;
        } else {
            for (FormattedCharSequence line : this.font.split(Component.literal(bio), textW)) {
                g.drawString(this.font, line, x, y, 0xFFD0D3D8);
                y += 11;
            }
        }
        y += SECTION_GAP;

        if (hasAnyLink()) {
            y = sectionHeader(g, x, y, x + textW, Component.translatable("gui.lanplus.profile.links"));
            int btnW = linkButtonWidth();
            for (String platform : PLATFORMS) {
                String handle = profile.link(platform);
                if (handle == null || handle.isBlank()) {
                    continue;
                }
                y = renderLinkRow(g, x, y, textW, btnW, platform, handle, mouseX, mouseY);
            }
            y += SECTION_GAP;
        }

        y = sectionHeader(g, x, y, x + textW, Component.translatable("gui.lanplus.profile.questions"));
        boolean anyPrompt = false;
        for (Prompt p : ProfilePromptCatalog.PROMPTS) {
            String answer = profile.prompt(p.id());
            if (answer == null) {
                continue;
            }
            anyPrompt = true;
            for (FormattedCharSequence line : this.font.split(p.question(), textW)) {
                g.drawString(this.font, line, x, y, 0xFF8A8F96);
                y += 10;
            }
            for (FormattedCharSequence line : this.font.split(ProfilePromptCatalog.answerText(p, answer), textW)) {
                g.drawString(this.font, line, x, y, 0xFFE0E3E8);
                y += 11;
            }
            y += 6;
        }
        if (!anyPrompt) {
            g.drawString(this.font, Component.translatable("gui.lanplus.profile.questions.empty"), x, y, 0xFF6A6E74);
            y += 12;
        }
        g.disableScissor();

        int viewport = panelBottom - panelTop;
        int contentHeight = (y + scrollY) - (panelTop + 8);
        maxScroll = Math.max(0, contentHeight - viewport + 12);
        if (scrollY > maxScroll) {
            scrollY = maxScroll;
        }
        if (maxScroll > 0) {
            int barH = Math.max(20, viewport * viewport / contentHeight);
            int barY = panelTop + (viewport - barH) * scrollY / maxScroll;
            g.fill(panelRight - 3, panelTop, panelRight, panelBottom, 0x40000000);
            g.fill(panelRight - 3, barY, panelRight, barY + barH, 0xFF555B66);
        }
    }

    private int sectionHeader(GuiGraphics g, int x, int y, int right, Component label) {
        g.drawString(this.font, label, x, y, HEADER_COLOR);
        g.fill(x, y + 11, right, y + 12, DIVIDER);
        return y + 17;
    }

    private int renderLinkRow(GuiGraphics g, int x, int y, int textW, int btnW, String platform, String handle, int mouseX, int mouseY) {
        boolean support = platform.equals("paypal") || platform.equals("kofi");
        boolean tag = platform.equals("discord");
        Component action = Component.translatable(tag ? "gui.lanplus.profile.link.tag"
                : support ? "gui.lanplus.profile.link.support" : "gui.lanplus.profile.link.open");
        int btnX = x + textW - btnW;
        int btnColor = support ? 0xFF9C6B12 : tag ? 0xFF3A3F4B : 0xFF2D4B82;
        boolean inView = y >= panelTop && y + 18 <= panelBottom;
        boolean hover = inView && mouseX >= btnX && mouseX < btnX + btnW && mouseY >= y - 2 && mouseY < y + 16;
        g.drawString(this.font, platformLabel(platform), x, y, 0xFFE0E3E8);
        g.drawString(this.font, "@" + handle, x, y + 10, 0xFF7A7F86);
        g.fill(btnX, y - 2, btnX + btnW, y + 16, hover ? brighten(btnColor) : btnColor);
        g.drawString(this.font, action, btnX + (btnW - this.font.width(action)) / 2, y + 3, 0xFFFFFFFF);
        if (inView) {
            String finalHandle = handle;
            Runnable act = tag ? () -> copyText(finalHandle) : () -> openLink(linkUrl(platform, finalHandle));
            hits.add(new Hit(btnX, y - 2, btnW, 18, act));
        }
        return y + 24;
    }

    private int linkButtonWidth() {
        int w = this.font.width(Component.translatable("gui.lanplus.profile.link.tag"));
        w = Math.max(w, this.font.width(Component.translatable("gui.lanplus.profile.link.open")));
        w = Math.max(w, this.font.width(Component.translatable("gui.lanplus.profile.link.support")));
        return w + 14;
    }

    private static int brighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 30);
        int gg = Math.min(255, ((color >> 8) & 0xFF) + 30);
        int b = Math.min(255, (color & 0xFF) + 30);
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }

    private void drawBorder(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(x1, y1, x2, y1 + 1, color);
        g.fill(x1, y2 - 1, x2, y2, color);
        g.fill(x1, y1, x1 + 1, y2, color);
        g.fill(x2 - 1, y1, x2, y2, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            for (Hit h : hits) {
                if (h.in(mouseX, mouseY)) {
                    h.action().run();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!editing && mouseX >= panelX && mouseX <= panelRight && mouseY >= panelTop && mouseY <= panelBottom) {
            scrollY = Math.max(0, Math.min(maxScroll, scrollY - (int) (delta * 16)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void doSave() {
        ProfilesService svc = LanPlusClient.profiles();
        if (svc == null || bioBox == null) {
            return;
        }
        captureFreeValues();
        String bio = bioBox.getValue();
        String pronouns = PRONOUN_CYCLE[pronounIndex];
        Map<String, String> links = new LinkedHashMap<>();
        for (int i = 0; i < PLATFORMS.length; i++) {
            String value = linkBoxes[i].getValue().trim();
            links.put(PLATFORMS[i], value.isEmpty() ? null : value);
        }
        Map<String, String> prompts = new LinkedHashMap<>();
        for (int i = 0; i < MAX_SLOTS; i++) {
            Prompt p = ProfilePromptCatalog.byId(slotPromptId[i]);
            if (p == null) {
                continue;
            }
            if (p.type() == ProfilePromptCatalog.Type.FREE) {
                String value = slotFreeValue[i] == null ? "" : slotFreeValue[i].trim();
                if (!value.isEmpty()) {
                    prompts.put(p.id(), value);
                }
            } else if (slotChoiceToken[i] != null) {
                prompts.put(p.id(), slotChoiceToken[i]);
            }
        }
        setStatus(Component.translatable("gui.lanplus.profile.saving"));
        svc.save(bio, pronouns, links, prompts, invisibleToggle, editFavoriteId, favoriteVisibleToggle,
                playingVisibleToggle).whenComplete((error, ex) -> this.minecraft.execute(() -> {
            if (ex == null && error == null) {
                editing = false;
                loaded = false;
                status = Component.translatable("gui.lanplus.profile.saved");
                loadProfile();
            } else {
                setStatus(Component.translatable(errorKey(error)));
            }
        }));
    }

    private void loadProfile() {
        ProfilesService svc = LanPlusClient.profiles();
        if (svc == null) {
            loaded = true;
            return;
        }
        svc.get(uuid).whenComplete((p, ex) -> this.minecraft.execute(() -> {
            this.profile = p;
            this.loaded = true;
            if (this.minecraft.screen == this) {
                rebuildWidgets();
            }
        }));
    }

    private boolean hasAnyLink() {
        for (String platform : PLATFORMS) {
            String h = profile.link(platform);
            if (h != null && !h.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private void drawAvatar(GuiGraphics g, UUID id, int x, int y, int size) {
        SkinTextures textures = LanPlusClient.skinTextures();
        SkinTextures.Resolved resolved = textures == null ? null : textures.get(id);
        ResourceLocation tex = resolved != null ? resolved.texture() : DefaultPlayerSkin.getDefaultSkin(id);
        PlayerFaceRenderer.draw(g, tex, x, y, size);
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

    private void copyText(String text) {
        if (text == null) {
            return;
        }
        this.minecraft.keyboardHandler.setClipboard(text);
        setStatus(Component.translatable("gui.lanplus.profile.copied"));
    }

    private Component pronounLabel() {
        String p = PRONOUN_CYCLE[pronounIndex];
        Component value = p == null ? Component.translatable("gui.lanplus.profile.pronouns.none") : Component.literal(p);
        return Component.translatable("gui.lanplus.profile.pronouns", value);
    }

    private Component invisibleLabel() {
        Component value = Component.translatable(invisibleToggle
                ? "gui.lanplus.profile.invisible.on" : "gui.lanplus.profile.invisible.off");
        return Component.translatable("gui.lanplus.profile.invisible", value);
    }

    private Component favoriteLabel() {
        if (!modpacksLoaded && editFavoriteId == null) {
            return Component.translatable("gui.lanplus.profile.favorite",
                    Component.translatable("gui.lanplus.profile.loading"));
        }
        String name = favoriteName(editFavoriteId);
        Component value = name == null
                ? Component.translatable("gui.lanplus.profile.favorite.none")
                : Component.literal(ellipsize(name, layoutWidth() - this.font.width(
                        Component.translatable("gui.lanplus.profile.favorite", "").getString()) - 16));
        return Component.translatable("gui.lanplus.profile.favorite", value);
    }

    private String favoriteName(String id) {
        if (id == null) {
            return null;
        }
        for (ModpackRef m : modpackCatalog) {
            if (id.equals(m.modpackId())) {
                return m.name();
            }
        }
        ModpackRef fav = profile.favorite();
        return fav != null && id.equals(fav.modpackId()) ? fav.name() : id;
    }

    private Component favoriteVisibleLabel() {
        Component value = Component.translatable(favoriteVisibleToggle
                ? "gui.lanplus.profile.invisible.on" : "gui.lanplus.profile.invisible.off");
        return Component.translatable("gui.lanplus.profile.favvisible", value);
    }

    private Component playingVisibleLabel() {
        Component value = Component.translatable(playingVisibleToggle
                ? "gui.lanplus.profile.invisible.on" : "gui.lanplus.profile.invisible.off");
        return Component.translatable("gui.lanplus.profile.playvisible", value);
    }

    private int pickerWidth() {
        return layoutWidth() / 2 - 3;
    }

    private Component pickerLabel(int slot) {
        Prompt p = ProfilePromptCatalog.byId(slotPromptId[slot]);
        if (p == null) {
            return Component.translatable("gui.lanplus.profile.questions.pick");
        }
        return Component.literal(ellipsize(p.question().getString(), pickerWidth() - 8));
    }

    private String ellipsize(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, maxWidth - this.font.width("…")) + "…";
    }

    private Component choiceLabel(int slot) {
        Prompt p = ProfilePromptCatalog.byId(slotPromptId[slot]);
        if (p == null || slotChoiceToken[slot] == null) {
            return Component.empty();
        }
        return ProfilePromptCatalog.choiceLabel(p, slotChoiceToken[slot]);
    }

    private Component presenceLabel() {
        if (profile.online()) {
            return Component.translatable("gui.lanplus.profile.online");
        }
        if (profile.lastSeen() <= 0) {
            return Component.translatable("gui.lanplus.profile.offline");
        }
        return Component.translatable("gui.lanplus.profile.lastseen", relativeTime(profile.lastSeen()));
    }

    private Component friendStatus(Friend f) {
        if (f.connectivity() == Connectivity.OFFLINE) {
            return Component.translatable("gui.lanplus.state.offline");
        }
        GameplayState state = f.state();
        if (state == null) {
            return Component.translatable("gui.lanplus.state.online");
        }
        String world = f.worldName() == null ? "?" : f.worldName();
        return switch (state) {
            case HOSTING -> Component.translatable("gui.lanplus.state.hosting", world);
            case MULTIPLAYER -> Component.translatable("gui.lanplus.state.playing", world);
            case SINGLEPLAYER -> Component.translatable("gui.lanplus.state.singleplayer");
            case MENU -> Component.translatable("gui.lanplus.state.online");
        };
    }

    private static int friendDotColor(Connectivity connectivity) {
        return switch (connectivity) {
            case ONLINE -> 0xFF43B581;
            case STALE -> 0xFFFAA61A;
            case OFFLINE -> 0xFF747F8D;
            case UNKNOWN -> 0xFF4F545C;
        };
    }

    private static String relativeTime(long epochMillis) {
        long seconds = Math.max(0, (System.currentTimeMillis() - epochMillis) / 1000L);
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m";
        }
        if (seconds < 86400) {
            return (seconds / 3600) + "h";
        }
        return (seconds / 86400) + "d";
    }

    private void setStatus(Component message) {
        this.status = message;
    }

    private static String platformLabel(String platform) {
        return Character.toUpperCase(platform.charAt(0)) + platform.substring(1);
    }

    private static String linkUrl(String platform, String handle) {
        return switch (platform) {
            case "instagram" -> "https://instagram.com/" + handle;
            case "twitter" -> "https://x.com/" + handle;
            case "youtube" -> "https://youtube.com/@" + handle;
            case "twitch" -> "https://twitch.tv/" + handle;
            case "tiktok" -> "https://tiktok.com/@" + handle;
            case "paypal" -> "https://paypal.me/" + handle;
            case "kofi" -> "https://ko-fi.com/" + handle;
            default -> null;
        };
    }

    private static String errorKey(String error) {
        return switch (error == null ? "" : error) {
            case "bio_link" -> "gui.lanplus.profile.err.bio_link";
            case "bio_too_long" -> "gui.lanplus.profile.err.bio_too_long";
            case "bad_pronouns" -> "gui.lanplus.profile.err.bad_pronouns";
            case "bad_link" -> "gui.lanplus.profile.err.bad_link";
            case "bad_prompt" -> "gui.lanplus.profile.err.bad_prompt";
            case "prompt_too_long" -> "gui.lanplus.profile.err.prompt_too_long";
            case "prompt_link" -> "gui.lanplus.profile.err.prompt_link";
            case "too_many_prompts" -> "gui.lanplus.profile.err.too_many_prompts";
            case "bad_modpack" -> "gui.lanplus.profile.err.bad_modpack";
            case "offline" -> "gui.lanplus.profile.err.offline";
            default -> "gui.lanplus.profile.err.generic";
        };
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
