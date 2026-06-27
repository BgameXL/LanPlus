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
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProfileScreen extends Screen {

    private static final int SURFACE = 0xF21E1F22;
    private static final int SURFACE_RAISED = 0xFF2B2D31;
    private static final int PANEL_BG = SURFACE;
    private static final int SIDEBAR_BG = SURFACE_RAISED;
    private static final int BLURPLE = 0xFF5865F2;
    private static final int BLURPLE_HOVER = 0xFF6B77F5;
    private static final int GREEN = 0xFF3BA55D;
    private static final int AMBER = 0xFFE8A317;
    private static final int BORDER = 0x33FFFFFF;
    private static final int DIVIDER = 0x18FFFFFF;
    private static final int ACCENT_LINE = 0x804752C4;
    private static final int HEADER_COLOR = 0xFFF2F3F5;
    private static final int TEXT = 0xFFF2F3F5;
    private static final int MUTED = 0xFFB5BAC1;
    private static final int FAINT = 0xFF80848E;
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
    private static final long[] TIER_THRESHOLDS = {150, 450, 1000, 2000};

    private static final int BG_DARK = 0;
    private static final int BG_SOLID = 1;
    private static final int BG_MINECRAFT = 2;
    private int bgStyle = BG_DARK;
    private int bgColor = 0x0A0C10;
    private int bgOpacity = 92;

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
    private int sbScrollY, sbMaxScroll, sbLeft, sbTop, sbBottom;
    private final List<Hit> hits = new ArrayList<>();

    private float modelYaw;
    private float modelPitch;
    private boolean draggingModel;
    private int cmBoxX, cmBoxY, cmBoxW, cmBoxH;
    private PlayerModel<LivingEntity> wideModel;
    private PlayerModel<LivingEntity> slimModel;

    // edit widgets
    private EditBox bioBox;
    private Button pronounButton;
    private Button invisibleButton;
    private int pronounIndex;
    private boolean invisibleToggle;

    private static final class LinkRow {
        int platform;
        String value = "";
        EditBox box;
        LinkRow(int platform) {
            this.platform = platform;
        }
    }
    private final List<LinkRow> linkRows = new ArrayList<>();

    private int eL, eW, eR;
    private int aAboutHdrY, aBioY, aIdentityY, aLinksHdrY, aLinksRowsY, aAddLinkY,
            aQHdrY, aQRowsY, aMpHdrY, aMpRowY;

    // edit: question slots
    private final String[] slotPromptId = new String[MAX_SLOTS];
    private final String[] slotFreeValue = new String[MAX_SLOTS];
    private final String[] slotChoiceToken = new String[MAX_SLOTS];
    private final EditBox[] slotFreeBox = new EditBox[MAX_SLOTS];

    private boolean favoriteVisibleToggle;
    private boolean playingVisibleToggle;
    private boolean recentlyPlayedVisibleToggle;

    private int editTop;

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

    private int linkRowCount() {
        return Math.max(1, linkRows.size());
    }

    private int formContentHeight() {
        int n = linkRowCount();
        boolean addRow = linkRows.size() < PLATFORMS.length;
        return 8
                + 16 + 18
                + 10 + 20
                + 12 + 16 + n * 24 + (addRow ? 20 : 0)
                + 12 + 16 + MAX_SLOTS * 24
                + 12 + 16 + 18
                + 8;
    }

    private int computeEditTop() {
        return Math.max(CONTENT_TOP, (this.height - (formContentHeight() + 32)) / 2 + 8);
    }

    private int editFormBottom() {
        return editTop + formContentHeight();
    }

    private void layoutEditAnchors() {
        eW = Math.min(this.width - 2 * MARGIN, 380);
        eL = (this.width - eW) / 2;
        eR = eL + eW;
        int n = linkRowCount();
        boolean addRow = linkRows.size() < PLATFORMS.length;
        int y = editTop + 8;
        aAboutHdrY = y;       y += 16;
        aBioY = y;            y += 18 + 10;
        aIdentityY = y;       y += 20 + 12;
        aLinksHdrY = y;       y += 16;
        aLinksRowsY = y;      y += n * 24;
        aAddLinkY = y;        y += (addRow ? 20 : 0) + 12;
        aQHdrY = y;           y += 16;
        aQRowsY = y;          y += MAX_SLOTS * 24 + 12;
        aMpHdrY = y;          y += 16;
        aMpRowY = y;
    }

    @Override
    protected void init() {
        if (!loaded) {
            loadProfile();
        }
        int left = layoutLeft();
        int right = left + layoutWidth();

        if (loaded && profile != null && editing) {
            editTop = computeEditTop();
            buildEditWidgets();

            int by = Math.min(editFormBottom() + 10, this.height - 28);
            addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.profile.save"), b -> doSave())
                    .bounds(eL, by, 90, 20).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.profile.cancel"),
                            b -> { editing = false; status = null; rebuildWidgets(); })
                    .bounds(eL + 96, by, 90, 20).build());
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                    .bounds(eR - 80, by, 80, 20).build());
        } else {
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                    .bounds(right - 80, this.height - 28, 80, 20).build());
            if (loaded && profile != null) {
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
        layoutEditAnchors();
        bioBox = new EditBox(this.font, eL, aBioY, eW, 18, Component.translatable("gui.lanplus.profile.about"));
        bioBox.setMaxLength(300);
        bioBox.setHint(Component.translatable("gui.lanplus.profile.bio.hint"));
        bioBox.setValue(profile.bio() == null ? "" : profile.bio());
        addRenderableWidget(bioBox);

        int half = (eW - 6) / 2;
        pronounButton = Button.builder(pronounLabel(), b -> {
            pronounIndex = (pronounIndex + 1) % PRONOUN_CYCLE.length;
            pronounButton.setMessage(pronounLabel());
        }).bounds(eL, aIdentityY, half, 20).build();
        addRenderableWidget(pronounButton);

        invisibleButton = Button.builder(invisibleLabel(), b -> {
            invisibleToggle = !invisibleToggle;
            invisibleButton.setMessage(invisibleLabel());
        }).bounds(eL + half + 6, aIdentityY, eW - half - 6, 20).build();
        addRenderableWidget(invisibleButton);

        buildLinkWidgets();
        buildSlotWidgets();
    }

    private void buildLinkWidgets() {
        if (linkRows.isEmpty()) {
            linkRows.add(new LinkRow(firstUnusedPlatform()));
        }
        int pickW = 92;
        int rmW = 16;
        int boxX = eL + pickW + 6;
        int boxW = eW - pickW - 6 - rmW - 4;
        for (int i = 0; i < linkRows.size(); i++) {
            LinkRow r = linkRows.get(i);
            int y = aLinksRowsY + i * 24;
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal(platformLabel(PLATFORMS[r.platform])),
                    b -> cycleLinkPlatform(idx)).bounds(eL, y, pickW, 20).build());
            EditBox box = new EditBox(this.font, boxX, y + 1, boxW, 18, Component.literal(PLATFORMS[r.platform]));
            box.setMaxLength(40);
            box.setHint(Component.translatable("gui.lanplus.profile.link.hint"));
            box.setValue(r.value == null ? "" : r.value);
            r.box = box;
            addRenderableWidget(box);
            addRenderableWidget(Button.builder(Component.literal("x"), b -> removeLinkRow(idx))
                    .bounds(eL + eW - rmW, y, rmW, 20).build());
        }
        if (linkRows.size() < PLATFORMS.length) {
            addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.profile.link.add"),
                    b -> addLinkRow()).bounds(eL, aAddLinkY, eW, 18).build());
        }
    }

    private void captureLinkValues() {
        for (LinkRow r : linkRows) {
            if (r.box != null) {
                r.value = r.box.getValue();
            }
        }
    }

    private void cycleLinkPlatform(int idx) {
        captureLinkValues();
        captureFreeValues();
        LinkRow r = linkRows.get(idx);
        r.platform = nextUnusedPlatform(r.platform, idx);
        rebuildWidgets();
    }

    private void addLinkRow() {
        captureLinkValues();
        captureFreeValues();
        int p = firstUnusedPlatform();
        if (p >= 0 && linkRows.size() < PLATFORMS.length) {
            linkRows.add(new LinkRow(p));
        }
        rebuildWidgets();
    }

    private void removeLinkRow(int idx) {
        captureLinkValues();
        captureFreeValues();
        if (idx >= 0 && idx < linkRows.size()) {
            linkRows.remove(idx);
        }
        rebuildWidgets();
    }

    private int firstUnusedPlatform() {
        for (int p = 0; p < PLATFORMS.length; p++) {
            if (!platformUsed(p, -1)) {
                return p;
            }
        }
        return 0;
    }

    private int nextUnusedPlatform(int cur, int exceptRow) {
        for (int step = 1; step <= PLATFORMS.length; step++) {
            int p = (cur + step) % PLATFORMS.length;
            if (!platformUsed(p, exceptRow)) {
                return p;
            }
        }
        return cur;
    }

    private boolean platformUsed(int p, int exceptRow) {
        for (int i = 0; i < linkRows.size(); i++) {
            if (i != exceptRow && linkRows.get(i).platform == p) {
                return true;
            }
        }
        return false;
    }

    private void buildSlotWidgets() {
        int pickerW = pickerWidth();
        int answerX = eL + pickerW + 6;
        int answerW = eW - pickerW - 6;
        for (int i = 0; i < MAX_SLOTS; i++) {
            slotFreeBox[i] = null;
            int y = aQRowsY + i * 24;
            final int slot = i;
            addRenderableWidget(Button.builder(pickerLabel(slot), b -> changeSlotPrompt(slot))
                    .bounds(eL, y, pickerW, 20).build());

            Prompt p = ProfilePromptCatalog.byId(slotPromptId[i]);
            if (p == null) {
                continue;
            }
            if (p.type() == ProfilePromptCatalog.Type.FREE) {
                EditBox box = new EditBox(this.font, answerX, y + 1, answerW, 18, Component.empty());
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
        linkRows.clear();
        for (int p = 0; p < PLATFORMS.length; p++) {
            String v = profile.link(PLATFORMS[p]);
            if (v != null && !v.isBlank()) {
                LinkRow r = new LinkRow(p);
                r.value = v;
                linkRows.add(r);
            }
        }
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
        favoriteVisibleToggle = profile.favoriteVisible();
        playingVisibleToggle = profile.currentlyPlayingVisible();
        recentlyPlayedVisibleToggle = profile.recentlyPlayedVisible();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackdrop(g);
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
            editTop = computeEditTop();
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

    private void renderBackdrop(GuiGraphics g) {
        renderBackground(g);
        if (bgStyle == BG_MINECRAFT) {
            return;
        }
        int rgb = bgStyle == BG_SOLID ? (bgColor & 0xFFFFFF) : 0x05070B;
        g.fill(0, 0, this.width, this.height, alpha(bgOpacity) | rgb);
    }

    private static int alpha(int opacity0to100) {
        return (Math.max(0, Math.min(255, opacity0to100 * 255 / 100))) << 24;
    }

    private void renderEditDecor(GuiGraphics g) {
        layoutEditAnchors();
        int cl = eL - 10;
        int cr = eR + 10;
        int ctop = editTop - 8;
        int cbot = editFormBottom() + 6;
        g.fill(cl, ctop, cr, cbot, PANEL_BG);
        drawBorder(g, cl, ctop, cr, cbot, BORDER);

        editHeader(g, Component.translatable("gui.lanplus.profile.about"), aAboutHdrY);
        editHeader(g, Component.translatable("gui.lanplus.profile.links"), aLinksHdrY);
        editHeader(g, Component.translatable("gui.lanplus.profile.questions"), aQHdrY);
        editHeader(g, Component.translatable("gui.lanplus.profile.modpack"), aMpHdrY);

        int gap = 6;
        int chipW = (eW - 2 * gap) / 3;
        modpackChip(g, eL, aMpRowY, chipW, "gui.lanplus.profile.mp.favorite", favoriteVisibleToggle,
                () -> favoriteVisibleToggle = !favoriteVisibleToggle);
        modpackChip(g, eL + chipW + gap, aMpRowY, chipW, "gui.lanplus.profile.mp.playing", playingVisibleToggle,
                () -> playingVisibleToggle = !playingVisibleToggle);
        modpackChip(g, eL + 2 * (chipW + gap), aMpRowY, eW - 2 * (chipW + gap), "gui.lanplus.profile.mp.recent",
                recentlyPlayedVisibleToggle, () -> recentlyPlayedVisibleToggle = !recentlyPlayedVisibleToggle);
    }

    private void editHeader(GuiGraphics g, Component label, int y) {
        g.fill(eL, y + 1, eL + 2, y + 9, BLURPLE);
        g.drawString(this.font, label, eL + 6, y, HEADER_COLOR);
        g.fill(eL, y + 12, eR, y + 13, DIVIDER);
    }

    private void modpackChip(GuiGraphics g, int x, int y, int w, String key, boolean on, Runnable toggle) {
        int h = 18;
        g.fill(x, y, x + w, y + h, on ? BLURPLE : SURFACE_RAISED);
        if (on) {
            g.fill(x, y, x + w, y + 1, BLURPLE_HOVER);
        }
        Component label = Component.translatable(key);
        g.drawString(this.font, label, x + (w - this.font.width(label)) / 2, y + 5, on ? 0xFFFFFFFF : MUTED);
        hits.add(new Hit(x, y, w, h, toggle));
    }

    private void renderSidebar(GuiGraphics g) {
        int left = layoutLeft();
        int top = CONTENT_TOP;
        int bottom = this.height - 34;
        sbLeft = left;
        sbTop = top;
        sbBottom = bottom;
        g.fill(left, top, left + SIDEBAR_W, bottom, SIDEBAR_BG);
        drawBorder(g, left, top, left + SIDEBAR_W, bottom, BORDER);

        g.enableScissor(left + 1, top + 1, left + SIDEBAR_W - 1, bottom - 1);
        int l = left + 8;
        int r = left + SIDEBAR_W - 8;
        int base = top + 6 - sbScrollY;

        drawAvatar(g, uuid, l, base, 36);
        int hx = left + 50;
        g.drawString(this.font, profile.username() == null ? "?" : profile.username(), hx, base + 2, 0xFFFFFFFF);
        if (profile.pronouns() != null) {
            int pw = this.font.width(profile.pronouns()) + 6;
            g.fill(hx, base + 14, hx + pw, base + 25, 0x405865F2);
            g.drawString(this.font, profile.pronouns(), hx + 3, base + 16, 0xFFB9C7FF);
        }
        drawTierChip(g, r, base + 2);

        int y = renderSidebarProgression(g, l, r, base + 38);

        if (profile.friendCode() != null) {
            g.drawString(this.font, profile.friendCode(), l, y, 0xFF9AA0A6);
            hits.add(new Hit(l, y - 1, this.font.width(profile.friendCode()), 10, () -> copyText(profile.friendCode())));
        }
        int presX = r - this.font.width(presenceLabel());
        g.fill(presX - 10, y + 1, presX - 4, y + 7, profile.online() ? 0xFF43B581 : 0xFF747F8D);
        g.drawString(this.font, presenceLabel(), presX, y, profile.online() ? 0xFF55C46A : 0xFF9AA0A6);
        y += 18;

        y = renderSidebarAbout(g, l, r, y);

        ModpackRef playing = profile.currentlyPlaying();
        ModpackRef lastPlayed = profile.lastPlayed();
        ModpackRef recent = profile.recentlyPlayed();
        ModpackRef favorite = profile.favorite();
        String favId = favorite == null ? null : favorite.modpackId();

        ModpackRef headline;
        boolean headlineLive;
        if (playing != null && playing.name() != null) {
            headline = playing;
            headlineLive = true;
        } else if (lastPlayed != null && lastPlayed.name() != null) {
            headline = lastPlayed;
            headlineLive = false;
        } else {
            headline = null;
            headlineLive = false;
        }
        if (headline != null) {
            String key = headlineLive ? "gui.lanplus.profile.playing" : "gui.lanplus.profile.lastplayed.label";
            y = renderSidebarModpack(g, left, y, key, headline, own, isFav(favId, headline));
        }
        boolean recentDup = headline != null && recent != null && recent.modpackId().equals(headline.modpackId());
        if (recent != null && recent.name() != null && !recentDup) {
            y = renderSidebarModpack(g, left, y, "gui.lanplus.profile.recentplayed.label", recent, own, isFav(favId, recent));
        }
        boolean favShownAbove = (headline != null && isFav(favId, headline))
                || (recent != null && !recentDup && isFav(favId, recent));
        if (favorite != null && favorite.name() != null && !favShownAbove) {
            y = renderSidebarModpack(g, left, y, "gui.lanplus.profile.favorite.label", favorite, own, true);
        }

        y = renderSidebarLinks(g, l, r, y);

        y = renderSidebarFriends(g, left, y + 2);
        g.disableScissor();

        int viewport = bottom - top;
        int contentH = (y + sbScrollY) - (top + 6);
        sbMaxScroll = Math.max(0, contentH - viewport + 10);
        if (sbScrollY > sbMaxScroll) {
            sbScrollY = sbMaxScroll;
        }
        if (sbMaxScroll > 0) {
            int barH = Math.max(20, viewport * viewport / contentH);
            int barY = top + (viewport - barH) * sbScrollY / sbMaxScroll;
            g.fill(left + SIDEBAR_W - 3, top, left + SIDEBAR_W, bottom, 0x40000000);
            g.fill(left + SIDEBAR_W - 3, barY, left + SIDEBAR_W, barY + barH, 0xFF555B66);
        }
    }

    private void drawTierChip(GuiGraphics g, int rightX, int y) {
        int tier = profile.tier();
        if (tier <= 0) {
            return; // no chip until the player reaches tier 1; the XP line still shows progress
        }
        Component label = Component.translatable("gui.lanplus.profile.tier", tier);
        int w = this.font.width(label) + 10;
        int x = rightX - w;
        g.fill(x, y, x + w, y + 13, BLURPLE);
        g.fill(x, y, x + w, y + 1, BLURPLE_HOVER);
        g.drawString(this.font, label, x + 5, y + 3, 0xFFFFFFFF);
    }

    private int renderSidebarProgression(GuiGraphics g, int l, int r, int y) {
        int tier = profile.tier();
        if (profile.hasXp()) {
            int xp = profile.xp();
            int barW = r - l;
            int prev = tier <= 0 ? 0 : (int) TIER_THRESHOLDS[Math.min(tier, TIER_THRESHOLDS.length) - 1];
            int next = tier >= TIER_THRESHOLDS.length ? xp : (int) TIER_THRESHOLDS[tier];
            float frac = next <= prev ? 1f : Math.max(0f, Math.min(1f, (xp - prev) / (float) (next - prev)));
            g.fill(l, y, l + barW, y + 4, 0xFF15161A);
            g.fill(l, y, l + (int) (barW * frac), y + 4, BLURPLE);
            y += 7;
            Component line = tier >= TIER_THRESHOLDS.length
                    ? Component.translatable("gui.lanplus.profile.xp.max", xp)
                    : Component.translatable("gui.lanplus.profile.xp.next", xp,
                            Math.max(0, (int) TIER_THRESHOLDS[tier] - xp), tier + 1);
            g.drawString(this.font, line, l, y, 0xFF7A7F86);
            y += 13;
        } else {
            g.drawString(this.font, Component.translatable("gui.lanplus.profile.advancements", profile.advancements()),
                    l, y, 0xFF7A7F86);
            y += 13;
        }
        return y;
    }

    private int renderSidebarAbout(GuiGraphics g, int l, int r, int y) {
        y = sectionHeader(g, l, y, r, Component.translatable("gui.lanplus.profile.about"));
        String bio = profile.bio();
        if (bio == null || bio.isBlank()) {
            g.drawString(this.font, Component.translatable("gui.lanplus.profile.nobio"), l, y, 0xFF6A6E74);
            y += 12;
        } else {
            for (FormattedCharSequence line : this.font.split(Component.literal(bio), r - l)) {
                g.drawString(this.font, line, l, y, 0xFFD0D3D8);
                y += 11;
            }
        }
        return y + 8;
    }

    private int renderSidebarLinks(GuiGraphics g, int l, int r, int y) {
        if (!hasAnyLink()) {
            return y;
        }
        y = sectionHeader(g, l, y, r, Component.translatable("gui.lanplus.profile.links"));
        for (String platform : PLATFORMS) {
            String handle = profile.link(platform);
            if (handle == null || handle.isBlank()) {
                continue;
            }
            boolean tag = platform.equals("discord");
            g.drawString(this.font, platformLabel(platform), l, y, 0xFFC0C4CA);
            String at = "@" + handle;
            g.drawString(this.font, at, r - this.font.width(at), y, tag ? 0xFF9AA0A6 : 0xFF6CA8FF);
            String finalHandle = handle;
            Runnable act = tag ? () -> copyText(finalHandle) : () -> openLink(linkUrl(platform, finalHandle));
            hits.add(new Hit(l, y - 1, r - l, 11, act));
            y += 12;
        }
        return y + 8;
    }

    private static boolean isFav(String favId, ModpackRef ref) {
        return favId != null && ref != null && favId.equals(ref.modpackId());
    }

    private int renderSidebarModpack(GuiGraphics g, int left, int y, String headerKey, ModpackRef ref,
                                     boolean showStar, boolean starred) {
        g.drawString(this.font, Component.translatable(headerKey), left + 6, y, 0xFF7A7F86);
        y += 12;
        int icon = 32;
        drawModpackIcon(g, left + 6, y, icon, ref);
        int nameX = left + 6 + icon + 6;
        int nameY = y + (icon - 8) / 2;
        int reserve = showStar ? 18 : 0;
        String name = ellipsize(ref.name(), left + SIDEBAR_W - nameX - 6 - reserve);
        boolean link = ref.downloadUrl() != null && !ref.downloadUrl().isBlank();
        g.drawString(this.font, name, nameX, nameY, link ? 0xFF6CA8FF : 0xFFE0E3E8);
        if (link) {
            hits.add(new Hit(nameX, nameY - 1, this.font.width(name), 10, () -> openLink(ref.downloadUrl())));
        }
        if (showStar) {
            String star = String.valueOf((char) (starred ? 0x2605 : 0x2606));
            int sx = left + SIDEBAR_W - 15;
            g.drawString(this.font, star, sx, nameY, starred ? 0xFFE8C84A : 0xFF7A7F86);
            String id = ref.modpackId();
            hits.add(new Hit(sx - 2, nameY - 3, 16, 14, () -> toggleFavorite(starred ? null : id)));
        }
        return y + icon + 6;
    }

    private void drawModpackIcon(GuiGraphics g, int x, int y, int size, ModpackRef ref) {
        g.fill(x, y, x + size, y + size, 0xFF15161A);
        drawBorder(g, x, y, x + size, y + size, 0x40FFFFFF);
        int strip = Math.max(2, size / 9);
        g.fill(x, y, x + size, y + strip, BLURPLE);
        String name = ref.name() == null ? "" : ref.name().trim();
        String letter = name.isEmpty() ? "?" : String.valueOf(Character.toUpperCase(name.charAt(0)));
        float scale = size >= 28 ? 2.0f : 1.0f;
        g.pose().pushPose();
        g.pose().translate(x + size / 2f, y + (size + strip) / 2f, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(this.font, letter, -this.font.width(letter) / 2, -this.font.lineHeight / 2, 0xFFEDEFF3, false);
        g.pose().popPose();
    }

    private void toggleFavorite(String modpackId) {
        ProfilesService svc = LanPlusClient.profiles();
        if (svc == null) {
            return;
        }
        setStatus(Component.translatable("gui.lanplus.profile.saving"));
        svc.setFavoriteModpack(modpackId).whenComplete((error, ex) -> this.minecraft.execute(() -> {
            if (ex == null && error == null) {
                loaded = false;
                status = Component.translatable("gui.lanplus.profile.saved");
                loadProfile();
            } else {
                setStatus(Component.translatable(errorKey(error)));
            }
        }));
    }

    private int renderSidebarFriends(GuiGraphics g, int left, int y) {
        FriendsService svc = LanPlusClient.friends();
        List<Friend> all = svc == null ? new ArrayList<>() : new ArrayList<>(svc.friends());
        int online = 0;
        for (Friend f : all) {
            if (isLive(f)) {
                online++;
            }
        }

        all.sort(Comparator.comparingInt(Friend::tier).reversed()
                .thenComparingInt(f -> isLive(f) ? 0 : 1)
                .thenComparing(Friend::username, String.CASE_INSENSITIVE_ORDER));

        int l = left + 8;
        int r = left + SIDEBAR_W - 8;
        g.drawString(this.font, Component.translatable("gui.lanplus.profile.friends"), l, y, 0xFF7A7F86);
        Component count = Component.translatable("gui.lanplus.profile.friends.count", online, all.size());
        g.drawString(this.font, count, r - this.font.width(count), y, 0xFF7A7F86);
        y += 14;

        int max = 5;
        int shown = Math.min(all.size(), max);
        for (int i = 0; i < shown; i++) {
            Friend f = all.get(i);
            int av = 16;
            drawAvatar(g, f.uuid(), l, y + 2, av);
            int dx = l + av - 4;
            int dy = y + 2 + av - 4;
            g.fill(dx - 1, dy - 1, dx + 5, dy + 5, SIDEBAR_BG);
            g.fill(dx, dy, dx + 4, dy + 4, friendDotColor(f.connectivity()));
            int tx = l + av + 6;
            int nameRight = r;
            if (f.tier() > 0) {

                Component lv = Component.translatable("gui.lanplus.profile.friends.level", f.tier());
                int lw = this.font.width(lv) + 8;
                int bx = r - lw;
                g.fill(bx, y + 2, bx + lw, y + 13, 0x405865F2);
                g.drawString(this.font, lv, bx + 4, y + 4, 0xFFB9C7FF);
                nameRight = bx - 4;
            }
            g.drawString(this.font, ellipsize(f.username(), nameRight - tx), tx, y + 2, 0xFFE0E3E8);
            g.drawString(this.font, friendStatus(f), tx, y + 12, 0xFF7A7F86);
            y += 22;
        }
        if (all.size() > max) {
            g.drawString(this.font, Component.translatable("gui.lanplus.profile.friends.more", all.size() - max),
                    l, y + 2, 0xFF7A7F86);
            y += 14;
        }
        return y;
    }

    private static boolean isLive(Friend f) {
        return f.connectivity() == Connectivity.ONLINE || f.connectivity() == Connectivity.STALE;
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

        y = sectionHeader(g, x, y, x + textW, Component.translatable("gui.lanplus.profile.cosmetics"));
        y = renderCosmeticsShowcase(g, x, y, textW);
        y += SECTION_GAP;

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
        int lw = this.font.width(label);
        g.drawString(this.font, label, x, y, HEADER_COLOR);
        g.fill(x, y + 11, x + lw, y + 12, ACCENT_LINE);
        g.fill(x + lw, y + 11, right, y + 12, DIVIDER);
        return y + 17;
    }

    private static final String[] COSMETIC_SLOTS = {"background", "badge", "banner", "effect"};

    private int renderCosmeticsShowcase(GuiGraphics g, int x, int y, int textW) {
        int renderW = 104;
        int renderH = 150;
        cmBoxX = x;
        cmBoxY = y;
        cmBoxW = renderW;
        cmBoxH = renderH;

        g.fill(x, y, x + renderW, y + renderH, 0xFF15161A);
        drawBorder(g, x, y, x + renderW, y + renderH, 0x33FFFFFF);
        g.fill(x, y, x + renderW, y + 2, BLURPLE);
        g.flush();
        g.enableScissor(x + 1, y + 2, x + renderW - 1, y + renderH - 1);
        drawPlayerModel(g, x + renderW / 2, y + renderH - 16, 52);
        g.disableScissor();
        g.drawString(this.font, Component.translatable("gui.lanplus.profile.cosmetics.rotate"),
                x + 4, y + renderH - 11, FAINT);
        int sx = x + renderW + 8;
        int sw = textW - renderW - 8;
        int slotH = 26;
        int slotGap = 4;
        Component soon = Component.translatable("gui.lanplus.profile.cosmetics.soon");
        for (int i = 0; i < COSMETIC_SLOTS.length; i++) {
            int sy = y + i * (slotH + slotGap);
            g.fill(sx, sy, sx + sw, sy + slotH, SURFACE_RAISED);
            drawBorder(g, sx, sy, sx + sw, sy + slotH, 0x33FFFFFF);
            g.fill(sx, sy, sx + 2, sy + slotH, BLURPLE);
            g.drawString(this.font, Component.translatable("gui.lanplus.profile.cosmetics." + COSMETIC_SLOTS[i]),
                    sx + 7, sy + 5, 0xFFE0E3E8);
            g.drawString(this.font, soon, sx + 7, sy + 15, FAINT);
        }
        int slotsH = COSMETIC_SLOTS.length * (slotH + slotGap) - slotGap;
        return y + Math.max(renderH, slotsH);
    }

    private PlayerModel<LivingEntity> playerModel(boolean slim) {
        var models = Minecraft.getInstance().getEntityModels();
        if (slim) {
            if (slimModel == null) {
                slimModel = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
            }
            return slimModel;
        }
        if (wideModel == null) {
            wideModel = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER), false);
        }
        return wideModel;
    }

    private void drawPlayerModel(GuiGraphics g, int cx, int feetY, int scale) {
        SkinTextures st = LanPlusClient.skinTextures();
        SkinTextures.Resolved res = st == null ? null : st.get(uuid);
        ResourceLocation skin = res != null ? res.texture() : DefaultPlayerSkin.getDefaultSkin(uuid);
        boolean slim = res != null && res.slim();
        PlayerModel<LivingEntity> model = playerModel(slim);
        model.setAllVisible(true);
        model.young = false;
        model.crouching = false;
        model.attackTime = 0f;
        model.riding = false;

        g.flush();

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, feetY, 50.0);
        ps.mulPoseMatrix(new Matrix4f().scaling((float) scale, (float) scale, (float) -scale));
        ps.mulPose(Axis.ZP.rotationDegrees(180f));
        ps.mulPose(Axis.XP.rotationDegrees(modelPitch));
        ps.mulPose(Axis.YP.rotationDegrees(-modelYaw));
        Lighting.setupForEntityInInventory();
        ps.scale(-1f, -1f, 1f);
        ps.scale(0.9375f, 0.9375f, 0.9375f);
        ps.translate(0f, -1.501f, 0f);
        MultiBufferSource.BufferSource buffers = g.bufferSource();

        RenderSystem.enableDepthTest();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(skin));
        model.renderToBuffer(ps, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f);
        buffers.endBatch();
        Lighting.setupFor3DItems();
        ps.popPose();
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
        if (button == 0 && !editing && inCmBox(mouseX, mouseY)) {
            draggingModel = true;
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingModel && button == 0) {
            modelYaw += (float) dragX;
            modelPitch = Math.max(-35f, Math.min(35f, modelPitch - (float) dragY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingModel) {
            draggingModel = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean inCmBox(double mx, double my) {
        return mx >= cmBoxX && mx < cmBoxX + cmBoxW && my >= cmBoxY && my < cmBoxY + cmBoxH;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!editing) {
            if (mouseX >= sbLeft && mouseX <= sbLeft + SIDEBAR_W && mouseY >= sbTop && mouseY <= sbBottom) {
                sbScrollY = Math.max(0, Math.min(sbMaxScroll, sbScrollY - (int) (delta * 16)));
                return true;
            }
            if (mouseX >= panelX && mouseX <= panelRight && mouseY >= panelTop && mouseY <= panelBottom) {
                scrollY = Math.max(0, Math.min(maxScroll, scrollY - (int) (delta * 16)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void doSave() {
        ProfilesService svc = LanPlusClient.profiles();
        if (svc == null || bioBox == null) {
            return;
        }
        captureFreeValues();
        captureLinkValues();
        String bio = bioBox.getValue();
        String pronouns = PRONOUN_CYCLE[pronounIndex];
        Map<String, String> links = new LinkedHashMap<>();
        for (String pf : PLATFORMS) {
            links.put(pf, null);
        }
        for (LinkRow r : linkRows) {
            String value = r.value == null ? "" : r.value.trim();
            links.put(PLATFORMS[r.platform], value.isEmpty() ? null : value);
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
        svc.save(bio, pronouns, links, prompts, invisibleToggle, favoriteVisibleToggle,
                playingVisibleToggle, recentlyPlayedVisibleToggle).whenComplete((error, ex) -> this.minecraft.execute(() -> {
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

    private int pickerWidth() {
        return eW / 2 - 3;
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
        if (f.blocked()) {
            return Component.translatable("gui.lanplus.rel.blocked");
        }
        if (f.muted()) {
            return Component.translatable("gui.lanplus.rel.muted");
        }
        if (!isLive(f)) {
            // only ONLINE/STALE are "live"; OFFLINE and UNKNOWN (no presence record) both read as offline,
            // matching the "X/Y online" count which uses the same isLive() gate.
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
