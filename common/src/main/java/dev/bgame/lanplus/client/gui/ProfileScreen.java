package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.api.CatalogImage;
import dev.bgame.lanplus.api.Connectivity;
import dev.bgame.lanplus.api.Friend;
import dev.bgame.lanplus.api.GameplayState;
import dev.bgame.lanplus.api.ModpackRef;
import dev.bgame.lanplus.api.PlayedTogether;
import dev.bgame.lanplus.api.Profile;
import dev.bgame.lanplus.api.ProfileBackground;
import dev.bgame.lanplus.Config;
import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.client.SkinTextures;
import dev.bgame.lanplus.client.gui.ProfilePrompt.Prompt;
import dev.bgame.lanplus.friends.FriendsService;
import dev.bgame.lanplus.network.LanPlusNetwork;
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
import java.util.concurrent.CompletableFuture;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProfileScreen extends LanPlusScreen {

    private static int SURFACE = LanPlusUI.SURFACE;
    private static int SURFACE_RAISED = LanPlusUI.SURFACE_RAISED;
    private static int SLOT = LanPlusUI.SLOT;
    private static int PANEL_BG = LanPlusUI.SURFACE;
    private static int SIDEBAR_BG = LanPlusUI.SURFACE_RAISED;
    private static int ACCENT = LanPlusUI.ACCENT;
    private static int ACCENT_HOVER = LanPlusUI.ACCENT_HOVER;
    private static int ACCENT_TINT = LanPlusUI.ACCENT_TINT;
    private static int ONLINE = LanPlusUI.ONLINE;
    private static int AMBER = LanPlusUI.AMBER;
    private static int BORDER = LanPlusUI.BORDER;
    private static int DIVIDER = LanPlusUI.DIVIDER;
    private static int ACCENT_LINE = LanPlusUI.ACCENT_LINE;
    private static int LINK = LanPlusUI.LINK;
    private static int HEADER_COLOR = LanPlusUI.TEXT;
    private static int TEXT = LanPlusUI.TEXT;
    private static int MUTED = LanPlusUI.MUTED;
    private static int FAINT = LanPlusUI.FAINT;

    private static void refreshTheme() {
        SURFACE = LanPlusUI.SURFACE;
        SURFACE_RAISED = LanPlusUI.SURFACE_RAISED;
        SLOT = LanPlusUI.SLOT;
        PANEL_BG = LanPlusUI.SURFACE;
        SIDEBAR_BG = LanPlusUI.SURFACE_RAISED;
        ACCENT = LanPlusUI.ACCENT;
        ACCENT_HOVER = LanPlusUI.ACCENT_HOVER;
        ACCENT_TINT = LanPlusUI.ACCENT_TINT;
        ONLINE = LanPlusUI.ONLINE;
        AMBER = LanPlusUI.AMBER;
        BORDER = LanPlusUI.BORDER;
        DIVIDER = LanPlusUI.DIVIDER;
        ACCENT_LINE = LanPlusUI.ACCENT_LINE;
        LINK = LanPlusUI.LINK;
        HEADER_COLOR = LanPlusUI.TEXT;
        TEXT = LanPlusUI.TEXT;
        MUTED = LanPlusUI.MUTED;
        FAINT = LanPlusUI.FAINT;
    }

    private static final int SECTION_GAP = 12;
    private static final int MARGIN = 10;
    private static final int MAX_LAYOUT_W = 620;
    private static final int SIDEBAR_W = 250;
    private static final int GAP = 0;
    private static final int CONTENT_TOP = 32;
    private static final String[] PLATFORMS =
            {"discord", "instagram", "twitter", "youtube", "twitch", "tiktok", "paypal", "kofi"};
    private static final String[] PRONOUN_CYCLE = {null, "he/him", "she/her", "they/them"};
    private static final int MAX_SLOTS = 3;
    private static final long[] TIER_THRESHOLDS = {150, 450, 1000, 2000};
    private static final int BG_DARK = 0;
    private static final int BG_SOLID = 1;
    private static final int BG_MINECRAFT = 2;
    private static final int BG_IMAGE = 3;
    private static final int BG_NONE = 4;
    private static final int[] BG_PALETTE = {
            0x101216, 0x1A1C22, 0x262A33, 0x3A3F4E, 0x5B6472, 0x8B909A,
            0x7B8CFF, 0x5B8CFF, 0x3BA55D, 0x2FBFA0, 0xE74C3C, 0xF25C7A,
            0x9B59B6, 0xB36AF0, 0xF1A33C, 0xF0C24A};
    private int bgStyle = BG_DARK;
    private int bgColor = 0x101216;
    private int bgOpacity = 92;
    private String bgImageId;
    private CatalogImage bgImage;
    private CatalogImage banner;
    private List<CatalogImage> bgCatalog = List.of();
    private List<CatalogImage> bannerCatalog = List.of();
    private final Screen parent;
    private final UUID uuid;
    private final boolean own;
    private static final long STATUS_DISPLAY_MS = 4000;
    private Profile profile;
    private boolean loaded;
    private boolean editing;
    private Component status;
    private long statusUntil;
    private List<Friend> profileFriends = List.of();
    private int scrollY;
    private int maxScroll;
    private int panelX, panelTop, panelRight, panelBottom;
    private int sbScrollY, sbMaxScroll, sbLeft, sbTop, sbBottom;
    private final List<Hit> hits = new ArrayList<>();
    private float modelYaw;
    private float modelPitch;
    private float modelZoom = 1f;
    private boolean draggingModel;
    private int cmBoxX, cmBoxY, cmBoxW, cmBoxH;
    private EditBox bioBox;
    private Button pronounButton;
    private Button bannerButton;
    private int pronounIndex;
    private boolean invisibleToggle;
    private Component hoverTip;

    private static final class LinkRow {
        int platform;
        String value = "";
        EditBox box;

        LinkRow(int platform) {
            this.platform = platform;
        }
    }

    private final List<LinkRow> linkRows = new ArrayList<>();
    private static final int LINK_PICK_W = 76;
    private int linkPickerOpen = -1;
    private final List<int[]> linkPickerCells = new ArrayList<>();
    private static final int EDIT_SECTION_GAP = 18;
    private static final int EDIT_W = 440;
    private static final int EDIT_W_WIDE = 660;
    private static final int APP_FORM_W = 250;
    private static final int APP_ROW_H = 44;
    private static final int APP_ROW_GAP = 8;
    private static final int EDIT_TAB_Y = 30;
    private static final int EDIT_FRAME_TOP = 46;
    private static final String[] EDIT_TABS = {"about", "identity", "appearance"};
    private int eL, eW, eR;
    private int colLX, colRX, colW;
    private int editTab;
    private int editBodyBottom;
    private int aAboutHdrY, aBioY, aBioToolbarY, aBioPreviewY, aLinksHdrY, aLinksRowsY, aAddLinkY,
            aQHdrY, aQRowsY, aIdentityHdrY, aIdProfileHdrY, aIdPronounY, aIdStatusY,
            aIdActivityHdrY, aIdInvisibleY, aIdCurrentY, aIdRecentY, aIdFavoriteY, aIdLastSeenY,
            aBgRowY, aBannerRowY, aSkinRowY;
    private int pvX, pvY, pvW, pvH;

    private static final java.util.regex.Pattern AMP_CODE =
            java.util.regex.Pattern.compile("&([0-9a-fk-orA-FK-OR])");
    private static final String[][] BIO_FMT = {
            {"&l", "§lB"}, {"&o", "§oI"}, {"&n", "§nU"}, {"&m", "§mS"}, {"&r", "§rR"}
    };
    private static final char[] MC_COLOR_CODES =
            {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    private static final int[] MC_COLOR_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

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
        if (this.own) {
            FriendsService svc = LanPlusClient.friends();
            if (svc != null) {
                this.profileFriends = new ArrayList<>(svc.friends());
            }
        }
    }

    private static boolean isOwn(UUID uuid) {
        return uuid != null && uuid.equals(LanPlusClient.selfUuid());
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

    private boolean hasCustomSkin() {
        return !Config.skinUrl.isBlank();
    }

    private int tabContentHeight(int tab) {
        return switch (tab) {
            case 0 -> {
                int n = linkRowCount();
                boolean addRow = linkRows.size() < PLATFORMS.length;
                yield 16 + 6 + 16 + 6 + 18 + 6 + 22 + EDIT_SECTION_GAP
                        + 16 + n * 24 + (addRow ? 20 : 0) + EDIT_SECTION_GAP
                        + 16 + MAX_SLOTS * 24;
            }
            case 1 -> 22 + 26 + 24 + 32 + 10 + 26 + 26 + 4 * 22;
            default -> 3 * APP_ROW_H + 2 * APP_ROW_GAP;
        };
    }

    private int maxTabContentHeight() {
        int m = 0;
        for (int t = 0; t < EDIT_TABS.length; t++) {
            m = Math.max(m, tabContentHeight(t));
        }
        return m;
    }

    private int computeEditTop() {
        return EDIT_FRAME_TOP + 8;
    }

    private int editFormBottom() {
        return editBodyBottom;
    }

    private void layoutEditAnchors() {
        boolean wide = editTab == 2;
        eW = Math.min(this.width - 2 * MARGIN, wide ? EDIT_W_WIDE : EDIT_W);
        eL = (this.width - eW) / 2;
        eR = eL + eW;
        colLX = eL + 12;
        colW = wide ? APP_FORM_W : eW - 24;
        colRX = colLX;
        editTop = EDIT_FRAME_TOP + 8;
        int target = maxTabContentHeight() + 16;
        if (wide) {
            target = Math.max(target, 380);
        }
        int bodyH = Math.min(target, this.height - EDIT_FRAME_TOP - 44);
        editBodyBottom = EDIT_FRAME_TOP + bodyH;
        if (wide) {
            pvX = colLX + colW + 16;
            pvY = editTop;
            pvW = eR - 12 - pvX;
            pvH = editBodyBottom - editTop;
        }
        stackTabAnchors(editTab);
    }

    private void stackTabAnchors(int tab) {
        int n = linkRowCount();
        boolean addRow = linkRows.size() < PLATFORMS.length;
        int y = editTop;
        switch (tab) {
            case 0 -> {
                aAboutHdrY = y;
                y += 16 + 6;
                aBioToolbarY = y;
                y += 16 + 6;
                aBioY = y;
                y += 18 + 6;
                aBioPreviewY = y;
                y += 22 + EDIT_SECTION_GAP;
                aLinksHdrY = y;
                y += 16;
                aLinksRowsY = y;
                y += n * 24;
                aAddLinkY = y;
                y += (addRow ? 20 : 0) + EDIT_SECTION_GAP;
                aQHdrY = y;
                y += 16;
                aQRowsY = y;
            }
            case 1 -> {
                aIdentityHdrY = y;
                y += 22;
                aIdProfileHdrY = y;
                y += 26;
                aIdPronounY = y;
                y += 24;
                aIdStatusY = y;
                y += 32 + 10;
                aIdActivityHdrY = y;
                y += 26;
                aIdInvisibleY = y;
                y += 26;
                aIdCurrentY = y;
                y += 22;
                aIdRecentY = y;
                y += 22;
                aIdFavoriteY = y;
                y += 22;
                aIdLastSeenY = y;
            }
            default -> {
                aBgRowY = y;
                y += APP_ROW_H + APP_ROW_GAP;
                aBannerRowY = y;
                y += APP_ROW_H + APP_ROW_GAP;
                aSkinRowY = y;
            }
        }
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
            buildEdit();

            int by = Math.min(editFormBottom() + 10, this.height - 28);
            int bx = eL - 10;
            addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.profile.save"), b -> doSave())
                    .bounds(bx, by, 90, 20).primary().build());
            addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.profile.cancel"),
                            b -> {
                                editing = false;
                                status = null;
                                applyBackground(profile.background());
                                banner = profile.banner();
                                rebuildWidgets();
                            })
                    .bounds(bx + 96, by, 90, 20).build());
        } else {
            addRenderableWidget(LanplusButton.create(CommonComponents.GUI_DONE, b -> onClose())
                    .bounds(right - 80, this.height - 28, 80, 20).build());
            if (loaded && profile != null) {
                addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.profile.back"), b -> onClose())
                        .bounds(left, 6, 80, 20).build());
                if (own) {
                    addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.profile.edit"),
                                    b -> {
                                        editing = true;
                                        primeEdit();
                                        rebuildWidgets();
                                    })
                            .bounds(left, this.height - 28, 110, 20).build());
                } else {
                    addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.profile.report"),
                                    b -> this.minecraft.setScreen(new ReportScreen(this, uuid,
                                            profile.username() == null ? "" : profile.username())))
                            .bounds(left, this.height - 28, 110, 20).build());
                }
            }
        }
    }

    private void buildEdit() {
        layoutEditAnchors();
        switch (editTab) {
            case 0 -> {
                bioBox = new EditBox(this.font, colLX, aBioY, colW, 18,
                        Component.translatable("gui.lanplus.profile.about"));
                bioBox.setMaxLength(300);
                bioBox.setHint(Component.translatable("gui.lanplus.profile.bio.hint"));
                bioBox.setValue(sectionToAmp(profile.bio()));
                addRenderableWidget(bioBox);
                buildLinkWidgets();
                buildSlotWidgets();
            }
            case 1 -> {
                pronounButton = LanplusButton.create(pronounValueLabel(), b -> {
                    pronounIndex = (pronounIndex + 1) % PRONOUN_CYCLE.length;
                    pronounButton.setMessage(pronounValueLabel());
                }).bounds(colLX + colW - 150, aIdPronounY, 150, 20).build();
                addRenderableWidget(pronounButton);
            }
            default -> {
                buildBackgroundWidgets();
                buildBannerWidgets();
                buildSkinWidgets();
            }
        }
    }

    private void buildSkinWidgets() {
        int bx = colLX + colW - 72;
        addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.profile.change"),
                        b -> this.minecraft.setScreen(new SkinScreen(this)))
                .bounds(bx, aSkinRowY + 12, 66, 20).primary().build());
    }

    private Component skinStateSubtitle() {
        boolean showingCustom = Config.skinCustomActive && hasCustomSkin();
        Component src = Component.translatable(showingCustom
                ? "gui.lanplus.profile.skin.source.custom.short" : "gui.lanplus.profile.skin.source.mojang.short");
        Component arms = Component.translatable(Config.skinSlim
                ? "gui.lanplus.profile.skin.slim" : "gui.lanplus.profile.skin.classic");
        return Component.translatable("gui.lanplus.profile.skin.state", src, arms);
    }

    private void buildBackgroundWidgets() {
        int bx = colLX + colW - 72;
        addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.profile.change"), b -> openBg())
                .bounds(bx, aBgRowY + 12, 66, 20).primary().build());
    }

    private void openBg() {
        this.minecraft.setScreen(new BackgroundPicker(this, bgStyle, bgColor, BG_PALETTE, bgImageId, bgCatalog,
                new BackgroundPicker.Sink() {
                    @Override
                    public void solid(int c) {
                        bgStyle = BG_SOLID;
                        bgColor = c;
                    }

                    @Override
                    public void image(CatalogImage img) {
                        bgStyle = BG_IMAGE;
                        selectBgImage(img);
                    }

                    @Override
                    public void none() {
                        bgStyle = BG_NONE;
                    }
                }));
    }

    private void buildBannerWidgets() {
        int bx = colLX + colW - 72;
        bannerButton = LanplusButton.create(Component.translatable("gui.lanplus.profile.change"), b -> openBannerPicker())
                .bounds(bx, aBannerRowY + 12, 66, 20).primary().build();
        bannerButton.active = !bannerCatalog.isEmpty() || banner != null;
        addRenderableWidget(bannerButton);
    }

    private void selectBgImage(CatalogImage image) {
        bgImage = image;
        bgImageId = image == null ? null : image.id();
    }

    private void openBannerPicker() {
        this.minecraft.setScreen(new ImagePicker(this,
                Component.translatable("gui.lanplus.profile.banner.pick.title"),
                bannerCatalog, banner == null ? null : banner.id(), true, 2, 4f, img -> banner = img));
    }

    private void loadCatalogs() {
        ProfilesService svc = LanPlusClient.profiles();
        if (svc == null) {
            return;
        }
        svc.backgrounds().whenComplete((list, ex) -> this.minecraft.execute(() -> {
            if (ex == null && list != null) {
                bgCatalog = list;
                if (editing && this.minecraft.screen == this) {
                    rebuildWidgets();
                }
            }
        }));
        svc.banners().whenComplete((list, ex) -> this.minecraft.execute(() -> {
            if (ex == null && list != null) {
                bannerCatalog = list;
                if (editing && this.minecraft.screen == this) {
                    rebuildWidgets();
                }
            }
        }));
    }


    private void buildLinkWidgets() {
        linkPickerOpen = -1;
        if (linkRows.isEmpty()) {
            linkRows.add(new LinkRow(firstUnusedPlatform()));
        }
        int pickW = LINK_PICK_W;
        int rmW = 16;
        int boxX = colLX + pickW + 6;
        int boxW = colW - pickW - 6 - rmW - 4;
        for (int i = 0; i < linkRows.size(); i++) {
            LinkRow r = linkRows.get(i);
            int y = aLinksRowsY + i * 24;
            final int idx = i;
            addRenderableWidget(LanplusButton.create(Component.literal(platformLabel(PLATFORMS[r.platform])),
                    b -> openLinkPicker(idx)).bounds(colLX, y, pickW, 20).build());
            EditBox box = new EditBox(this.font, boxX, y + 1, boxW, 18, Component.literal(PLATFORMS[r.platform]));
            box.setMaxLength(40);
            box.setHint(Component.translatable("gui.lanplus.profile.link.hint"));
            box.setValue(r.value == null ? "" : r.value);
            r.box = box;
            addRenderableWidget(box);
            addRenderableWidget(LanplusButton.create(Component.literal("x"), b -> removeLinkRow(idx))
                    .bounds(colLX + colW - rmW, y, rmW, 20).build());
        }
        if (linkRows.size() < PLATFORMS.length) {
            addRenderableWidget(LanplusButton.create(Component.translatable("gui.lanplus.profile.link.add"),
                    b -> addLinkRow()).bounds(colLX, aAddLinkY, colW, 18).build());
        }
    }

    private void captureLinkValues() {
        for (LinkRow r : linkRows) {
            if (r.box != null) {
                r.value = r.box.getValue();
            }
        }
    }

    private void openLinkPicker(int idx) {
        captureLinkValues();
        captureFreeValues();
        linkPickerOpen = idx;
    }

    private void pickLinkPlatform(int idx, int platform) {
        captureLinkValues();
        captureFreeValues();
        linkPickerOpen = -1;
        if (idx >= 0 && idx < linkRows.size()) {
            linkRows.get(idx).platform = platform;
        }
        rebuildWidgets();
    }

    private List<Integer> linkOptions(int idx) {
        List<Integer> out = new ArrayList<>();
        int cur = idx >= 0 && idx < linkRows.size() ? linkRows.get(idx).platform : -1;
        for (int p = 0; p < PLATFORMS.length; p++) {
            if (p == cur || !platformUsed(p, idx)) {
                out.add(p);
            }
        }
        return out;
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
        int answerX = colLX + pickerW + 6;
        int answerW = colW - pickerW - 6;
        for (int i = 0; i < MAX_SLOTS; i++) {
            slotFreeBox[i] = null;
            int y = aQRowsY + i * 24;
            final int slot = i;
            addRenderableWidget(LanplusButton.create(pickerLabel(slot), b -> changeSlotPrompt(slot))
                    .bounds(colLX, y, pickerW, 20).build());

            Prompt p = ProfilePrompt.byId(slotPromptId[i]);
            if (p == null) {
                continue;
            }
            if (p.type() == ProfilePrompt.Type.FREE) {
                EditBox box = new EditBox(this.font, answerX, y + 1, answerW, 18, Component.empty());
                box.setMaxLength(140);
                box.setHint(Component.translatable("gui.lanplus.profile.questions.hint"));
                box.setValue(slotFreeValue[i] == null ? "" : slotFreeValue[i]);
                slotFreeBox[i] = box;
                addRenderableWidget(box);
            } else {
                addRenderableWidget(LanplusButton.create(choiceLabel(slot), b -> cycleChoice(slot))
                        .bounds(answerX, y, answerW, 20).build());
            }
        }
    }

    private void changeSlotPrompt(int slot) {
        captureFreeValues();
        String next = nextPrompt(slot);
        slotPromptId[slot] = next;
        Prompt p = ProfilePrompt.byId(next);
        if (p == null) {
            slotFreeValue[slot] = null;
            slotChoiceToken[slot] = null;
        } else if (p.type() == ProfilePrompt.Type.FREE) {
            slotFreeValue[slot] = "";
        } else {
            slotChoiceToken[slot] = p.choices().get(0);
        }
        rebuildWidgets();
    }

    private void cycleChoice(int slot) {
        Prompt p = ProfilePrompt.byId(slotPromptId[slot]);
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
        for (Prompt p : ProfilePrompt.PROMPTS) {
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
        linkPickerOpen = -1;
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
        for (Prompt p : ProfilePrompt.PROMPTS) {
            if (slot >= MAX_SLOTS) {
                break;
            }
            String answer = profile.prompt(p.id());
            if (answer == null) {
                continue;
            }
            slotPromptId[slot] = p.id();
            if (p.type() == ProfilePrompt.Type.FREE) {
                slotFreeValue[slot] = answer;
            } else {
                slotChoiceToken[slot] = p.choices().contains(answer) ? answer : p.choices().get(0);
            }
            slot++;
        }
        favoriteVisibleToggle = profile.favoriteVisible();
        playingVisibleToggle = profile.currentlyPlayingVisible();
        recentlyPlayedVisibleToggle = profile.recentlyPlayedVisible();
        if (bgStyle == BG_DARK || bgStyle == BG_MINECRAFT) {
            bgStyle = bgImageId != null ? BG_IMAGE : BG_SOLID;
        }
        loadCatalogs();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        refreshTheme();
        renderBackdrop(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 12, TEXT);

        if (!loaded) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.profile.loading"),
                    this.width / 2, this.height / 2, FAINT);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        if (profile == null) {
            g.drawCenteredString(this.font, Component.translatable("gui.lanplus.profile.unavailable"),
                    this.width / 2, this.height / 2, FAINT);
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }

        hits.clear();
        hoverTip = null;
        if (editing) {
            editTop = computeEditTop();
            renderEditDecor(g, mouseX, mouseY);
        } else {
            renderBanner(g);
            renderSidebar(g);
            renderPanel(g, mouseX, mouseY);
            renderUnifiedFrame(g);
            renderBannerIdentity(g);
        }

        if (status != null) {
            if (System.currentTimeMillis() < statusUntil) {
                g.drawString(this.font, status, layoutLeft() + 12, this.height - 52, AMBER);
            } else {
                status = null;
            }
        }
        boolean pickerOpen = editing && editTab == 0 && linkPickerOpen >= 0;
        super.render(g, pickerOpen ? -1 : mouseX, pickerOpen ? -1 : mouseY, partialTick);
        if (pickerOpen) {
            renderLinkPicker(g, mouseX, mouseY);
        } else if (hoverTip != null) {
            g.renderTooltip(this.font, hoverTip, mouseX, mouseY);
        }
    }

    private void renderLinkPicker(GuiGraphics g, int mouseX, int mouseY) {
        linkPickerCells.clear();
        if (linkPickerOpen < 0 || linkPickerOpen >= linkRows.size()) {
            return;
        }
        List<Integer> opts = linkOptions(linkPickerOpen);
        int cur = linkRows.get(linkPickerOpen).platform;
        int itemH = 15;
        int w = LINK_PICK_W;
        for (int p : opts) {
            w = Math.max(w, this.font.width(platformLabel(PLATFORMS[p])) + 12);
        }
        int x = colLX;
        int top = aLinksRowsY + linkPickerOpen * 24 + 21;
        int h = opts.size() * itemH;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        LanPlusUI.panel(g, x, top, x + w, top + h);
        for (int i = 0; i < opts.size(); i++) {
            int p = opts.get(i);
            int iy = top + i * itemH;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= iy && mouseY < iy + itemH;
            boolean sel = p == cur;
            if (hover) {
                g.fill(x + 1, iy, x + w - 1, iy + itemH, ACCENT_TINT);
            }
            if (sel) {
                g.fill(x + 1, iy, x + 2, iy + itemH, ACCENT);
            }
            g.drawString(this.font, platformLabel(PLATFORMS[p]), x + 6, iy + 3,
                    sel ? ACCENT : (hover ? TEXT : MUTED), false);
            linkPickerCells.add(new int[]{x, iy, w, itemH, p});
        }
        g.pose().popPose();
    }

    private int linkPickerButtonAt(double mx, double my) {
        if (editTab != 0) {
            return -1;
        }
        for (int i = 0; i < linkRows.size(); i++) {
            int y = aLinksRowsY + i * 24;
            if (mx >= colLX && mx < colLX + LINK_PICK_W && my >= y && my < y + 20) {
                return i;
            }
        }
        return -1;
    }

    void renderBackdrop(GuiGraphics g) {
        renderTransparentBackground(g);
        if (bgStyle == BG_MINECRAFT || bgStyle == BG_NONE) {
            return;
        }
        if (bgStyle == BG_IMAGE) {
            ProfileImages.Tex tex = ProfileImages.get(bgImage);
            if (tex != null) {
                ProfileImages.blitCover(g, tex, 0, 0, this.width, this.height);
                g.fill(0, 0, this.width, this.height, alpha(bgOpacity));
                return;
            }
        }
        if (bgStyle == BG_SOLID) {
            g.fill(0, 0, this.width, this.height, 0xFF000000 | (bgColor & 0xFFFFFF));
            return;
        }
        g.fill(0, 0, this.width, this.height, alpha(bgOpacity) | 0x0C0D10);
    }

    private static int alpha(int opacity0to100) {
        return (Math.clamp(opacity0to100 * 255L / 100, 0, 255)) << 24;
    }

    private void renderEditDecor(GuiGraphics g, int mouseX, int mouseY) {
        layoutEditAnchors();
        int cl = eL - 10;
        int cr = eR + 10;
        int ctop = EDIT_FRAME_TOP;
        int cbot = editBodyBottom + 6;
        renderEditTabs(g, mouseX, mouseY);
        g.fill(cl, ctop, cr, cbot, PANEL_BG);
        LanPlusUI.outline1(g, cl, ctop, cr, cbot, LanPlusUI.EDGE_DARK);
        LanPlusUI.outline1(g, cl + 1, ctop + 1, cr - 1, cbot - 1, LanPlusUI.shade(LanPlusUI.SURFACE, 1.6f));

        switch (editTab) {
            case 0 -> {
                editHeader(g, Component.translatable("gui.lanplus.profile.about"), colLX, colW, aAboutHdrY);
                renderBio(g, mouseX, mouseY);
                renderBioPreview(g);
                editHeader(g, Component.translatable("gui.lanplus.profile.links"), colLX, colW, aLinksHdrY);
                editHeader(g, Component.translatable("gui.lanplus.profile.questions"), colLX, colW, aQHdrY);
            }
            case 1 -> renderIdentityEdit(g, mouseX, mouseY);
            default -> renderAppearanceEdit(g, mouseX, mouseY);
        }
    }

    private void renderIdentityEdit(GuiGraphics g, int mouseX, int mouseY) {
        editHeader(g, Component.translatable("gui.lanplus.profile.identity"), colLX, colW, aIdentityHdrY);

        identitySubHeader(g, "gui.lanplus.profile.id.profile", "gui.lanplus.profile.id.profile.desc", aIdProfileHdrY);

        g.drawString(this.font, Component.translatable("gui.lanplus.profile.id.pronouns"),
                colLX, aIdPronounY + 6, TEXT, false);

        g.drawString(this.font, Component.translatable("gui.lanplus.profile.id.status"),
                colLX, aIdStatusY + 5, MUTED, false);
        int fieldW = 150;
        int fieldX = colLX + colW - fieldW;
        LanPlusUI.slot(g, fieldX, aIdStatusY, fieldX + fieldW, aIdStatusY + 18);
        g.drawString(this.font, Component.translatable("gui.lanplus.profile.id.status.hint"),
                fieldX + 6, aIdStatusY + 5, FAINT, false);
        Component counter = Component.literal("0/100");
        g.drawString(this.font, counter, colLX + colW - this.font.width(counter), aIdStatusY + 22, FAINT, false);
        if (mouseX >= fieldX && mouseX < fieldX + fieldW && mouseY >= aIdStatusY && mouseY < aIdStatusY + 18) {
            hoverTip = Component.translatable("gui.lanplus.profile.id.soon");
        }

        identitySubHeader(g, "gui.lanplus.profile.id.activity", "gui.lanplus.profile.id.activity.desc", aIdActivityHdrY);

        identityRow(g, aIdInvisibleY, "gui.lanplus.profile.id.invisible", "gui.lanplus.profile.id.invisible.desc",
                invisibleToggle, true, () -> invisibleToggle = !invisibleToggle, mouseX, mouseY);

        boolean fieldsOn = !invisibleToggle;
        identityRow(g, aIdCurrentY, "gui.lanplus.profile.id.current", "gui.lanplus.profile.id.current.desc",
                playingVisibleToggle, fieldsOn, () -> playingVisibleToggle = !playingVisibleToggle, mouseX, mouseY);
        identityRow(g, aIdRecentY, "gui.lanplus.profile.id.recent", "gui.lanplus.profile.id.recent.desc",
                recentlyPlayedVisibleToggle, fieldsOn, () -> recentlyPlayedVisibleToggle = !recentlyPlayedVisibleToggle, mouseX, mouseY);
        identityRow(g, aIdFavoriteY, "gui.lanplus.profile.id.favorite", "gui.lanplus.profile.id.favorite.desc",
                favoriteVisibleToggle, fieldsOn, () -> favoriteVisibleToggle = !favoriteVisibleToggle, mouseX, mouseY);
        identityRow(g, aIdLastSeenY, "gui.lanplus.profile.id.lastseen", "gui.lanplus.profile.id.soon",
                false, false, null, mouseX, mouseY);
    }

    private void identitySubHeader(GuiGraphics g, String titleKey, String descKey, int y) {
        g.drawString(this.font, Component.translatable(titleKey), colLX, y, TEXT, false);
        g.drawString(this.font, Component.translatable(descKey), colLX, y + 11, MUTED, false);
    }

    private void identityRow(GuiGraphics g, int y, String labelKey, String tipKey,
                             boolean on, boolean enabled, Runnable act, int mouseX, int mouseY) {
        int rowH = 22;
        g.drawString(this.font, Component.translatable(labelKey), colLX, y + 7, enabled ? TEXT : FAINT, false);
        int rowRight = colLX + colW;
        Component txt = Component.translatable(on ? "gui.lanplus.toggle.on" : "gui.lanplus.toggle.off");
        int pillY = y + (rowH - 14) / 2;
        int pillX = rowRight - 24 - 28;
        drawPill(g, pillX, pillY, on, enabled);
        g.drawString(this.font, txt, pillX + 28 + 6, pillY + 3, !enabled ? FAINT : (on ? TEXT : MUTED), false);
        if (enabled && act != null) {
            hits.add(new Hit(colLX, y, colW, rowH, act));
        }
        if (tipKey != null && mouseX >= colLX && mouseX < rowRight && mouseY >= y && mouseY < y + rowH) {
            hoverTip = Component.translatable(tipKey);
        }
    }

    private void drawPill(GuiGraphics g, int x, int y, boolean on, boolean enabled) {
        int w = 28;
        int h = 14;
        g.fill(x, y, x + w, y + h, enabled && on ? LanPlusUI.LIME : SLOT);
        LanPlusUI.outline1(g, x, y, x + w, y + h, LanPlusUI.EDGE_DARK);
        int kx = on ? x + w - 12 : x + 2;
        g.fill(kx, y + 2, kx + 10, y + h - 2, !enabled ? FAINT : (on ? 0xFFFFFFFF : MUTED));
    }

    private Component pronounValueLabel() {
        String p = PRONOUN_CYCLE[pronounIndex];
        return p == null ? Component.translatable("gui.lanplus.profile.pronouns.none") : Component.literal(p);
    }

    private void renderAppearanceEdit(GuiGraphics g, int mouseX, int mouseY) {
        appearanceRow(g, aBgRowY, Component.translatable("gui.lanplus.profile.bg.header"), bgRowValue());
        drawBgSwatch(g, colLX + 6, aBgRowY + 8);

        appearanceRow(g, aBannerRowY, Component.translatable("gui.lanplus.profile.banner.header"), bannerRowValue());
        drawBannerThumb(g, colLX + 6, aBannerRowY + 13);

        appearanceRow(g, aSkinRowY, Component.translatable("gui.lanplus.profile.skin.header"), skinStateSubtitle());
        drawFacePreview(g, colLX + 6, aSkinRowY + 8);

        renderEditPreview(g, mouseX, mouseY);
    }

    private void appearanceRow(GuiGraphics g, int rowY, Component title, Component subtitle) {
        LanPlusUI.slot(g, colLX, rowY, colLX + colW, rowY + APP_ROW_H);
        g.drawString(this.font, title, colLX + 42, rowY + 8, TEXT, false);
        if (subtitle != null) {
            g.drawString(this.font, ellipsizeC(subtitle, colW - 42 - 78), colLX + 42, rowY + 22, MUTED, false);
        }
    }

    private Component bgRowValue() {
        return Component.translatable(switch (bgStyle) {
            case BG_IMAGE -> "gui.lanplus.profile.bg.image";
            case BG_NONE -> "gui.lanplus.profile.bg.none";
            default -> "gui.lanplus.profile.bg.solid";
        });
    }

    private Component bannerRowValue() {
        return banner == null ? Component.translatable("gui.lanplus.profile.banner.none")
                : Component.literal(banner.id());
    }

    private void drawBgSwatch(GuiGraphics g, int x, int y) {
        LanPlusUI.outline1(g, x, y, x + 28, y + 28, LanPlusUI.EDGE_DARK);
        if (bgStyle == BG_IMAGE) {
            ProfileImages.Tex tex = ProfileImages.get(bgImage);
            if (tex != null) {
                ProfileImages.blitContain(g, tex, x + 1, y + 1, 26, 26);
                return;
            }
        }
        if (bgStyle == BG_NONE) {
            g.drawString(this.font, "/", x + 11, y + 10, FAINT, false);
            return;
        }
        g.fill(x + 1, y + 1, x + 27, y + 27, 0xFF000000 | (bgColor & 0xFFFFFF));
    }

    private void drawBannerThumb(GuiGraphics g, int x, int y) {
        int w = 32;
        int h = 18;
        g.fill(x, y, x + w, y + h, SLOT);
        LanPlusUI.outline1(g, x, y, x + w, y + h, LanPlusUI.EDGE_DARK);
        if (banner != null) {
            ProfileImages.Tex tex = ProfileImages.get(banner);
            if (tex != null) {
                ProfileImages.blitContain(g, tex, x + 1, y + 1, w - 2, h - 2);
            }
        }
    }

    private void drawFacePreview(GuiGraphics g, int x, int y) {
        LanPlusUI.outline1(g, x, y, x + 28, y + 28, LanPlusUI.EDGE_DARK);
        SkinTextures st = LanPlusClient.skinTextures();
        SkinTextures.Resolved res = st == null ? null : st.get(uuid);
        ResourceLocation skin = res != null ? res.texture() : resolveFallbackSkin();
        PlayerFaceRenderer.draw(g, skin, x + 2, y + 2, 24);
    }

    private void renderEditPreview(GuiGraphics g, int mouseX, int mouseY) {
        LanPlusUI.slot(g, pvX, pvY, pvX + pvW, pvY + pvH);
        g.drawString(this.font, Component.translatable("gui.lanplus.profile.preview.header"), pvX + 8, pvY + 7, TEXT, false);
        g.fill(pvX + 8, pvY + 18, pvX + pvW - 8, pvY + 19, DIVIDER);

        int cx0 = pvX + 12;
        int cy0 = pvY + 28;
        int cw = pvW - 24;
        int ch = pvH - 28 - 14;

        g.enableScissor(cx0, cy0, cx0 + cw, cy0 + ch);
        if (bgStyle == BG_IMAGE) {
            ProfileImages.Tex tex = ProfileImages.get(bgImage);
            if (tex != null) {
                ProfileImages.blitCover(g, tex, cx0, cy0, cw, ch);
                g.fill(cx0, cy0, cx0 + cw, cy0 + ch, alpha(bgOpacity));
            } else {
                g.fill(cx0, cy0, cx0 + cw, cy0 + ch, 0xFF0C0D10);
            }
        } else if (bgStyle == BG_SOLID) {
            g.fill(cx0, cy0, cx0 + cw, cy0 + ch, 0xFF000000 | (bgColor & 0xFFFFFF));
        } else {
            g.fill(cx0, cy0, cx0 + cw, cy0 + ch, 0xFF0C0D10);
        }

        int bh = banner != null ? Math.min(cw / 4, 64) : 44;
        if (banner != null) {
            ProfileImages.Tex tex = ProfileImages.get(banner);
            g.fill(cx0, cy0, cx0 + cw, cy0 + bh, SLOT);
            if (tex != null) {
                ProfileImages.blitCover(g, tex, cx0, cy0, cw, bh);
            }
            g.fillGradient(cx0, cy0 + bh - 22, cx0 + cw, cy0 + bh, 0x00000000, 0xB8000000);
        } else {
            g.fill(cx0, cy0, cx0 + cw, cy0 + bh, 0xCC0C0D10);
        }

        int cardTop = cy0 + bh;
        g.fill(cx0, cardTop, cx0 + cw, cy0 + ch, 0x66000000);

        int av = 36;
        int ax = cx0 + 12;
        int ay = cardTop - av + 10;
        String name = profile.username() == null ? "?" : profile.username();
        g.drawString(this.font, name, ax + av + 10, cardTop - 14, TEXT, false);
        LanPlusUI.slot(g, ax - 2, ay - 2, ax + av + 2, ay + av + 2);
        drawAvatar(g, uuid, ax, ay, av);

        int sy = ay + av + 12;
        g.drawString(this.font, Component.translatable("gui.lanplus.profile.about"), cx0 + 12, sy, LanPlusUI.LIME, false);
        g.fill(cx0 + 12, sy + 11, cx0 + cw - 12, sy + 12, DIVIDER);
        sy += 18;
        String bio = profile.bio();
        if (bio == null || bio.isBlank()) {
            g.drawString(this.font, Component.translatable("gui.lanplus.profile.nobio"), cx0 + 12, sy, FAINT, false);
        } else {
            for (FormattedCharSequence line : this.font.split(Component.literal(bio), cw - 24)) {
                if (sy > cy0 + ch - 12) {
                    break;
                }
                g.drawString(this.font, line, cx0 + 12, sy, 0xFFE6E8EC, false);
                sy += 11;
            }
        }
        g.disableScissor();
        LanPlusUI.outline1(g, cx0, cy0, cx0 + cw, cy0 + ch, LanPlusUI.EDGE_DARK);
    }

    private Component ellipsizeC(Component c, int maxWidth) {
        return Component.literal(ellipsize(c.getString(), maxWidth));
    }

    private void renderEditTabs(GuiGraphics g, int mouseX, int mouseY) {
        int tabW = eW / EDIT_TABS.length;
        for (int i = 0; i < EDIT_TABS.length; i++) {
            int x = eL + i * tabW;
            Component label = Component.translatable("gui.lanplus.profile.tab." + EDIT_TABS[i]);
            boolean active = editTab == i;
            boolean hover = mouseX >= x && mouseX < x + tabW
                    && mouseY >= EDIT_TAB_Y - 3 && mouseY < EDIT_TAB_Y + 12;
            int color = active ? TEXT : (hover ? MUTED : FAINT);
            g.drawString(this.font, label, x + (tabW - this.font.width(label)) / 2, EDIT_TAB_Y, color, false);
            if (active) {
                g.fill(x + 6, EDIT_TAB_Y + 11, x + tabW - 6, EDIT_TAB_Y + 12, ACCENT);
            }
        }
    }

    private int editTabAt(double mouseX, double mouseY) {
        if (mouseY < EDIT_TAB_Y - 3 || mouseY >= EDIT_TAB_Y + 13 || mouseX < eL || mouseX >= eR) {
            return -1;
        }
        int tabW = eW / EDIT_TABS.length;
        int i = (int) ((mouseX - eL) / tabW);
        return i >= 0 && i < EDIT_TABS.length ? i : -1;
    }

    private void editHeader(GuiGraphics g, Component label, int x, int w, int y) {
        g.fill(x, y + 1, x + 2, y + 9, ACCENT);
        g.drawString(this.font, label, x + 6, y, HEADER_COLOR);
        g.fill(x, y + 12, x + w, y + 13, DIVIDER);
    }

    private void renderBio(GuiGraphics g, int mouseX, int mouseY) {
        int x = colLX;
        int y = aBioToolbarY;
        int h = 16;
        int fw = 22;
        for (String[] f : BIO_FMT) {
            boolean hover = mouseX >= x && mouseX < x + fw && mouseY >= y && mouseY < y + h;
            g.fill(x, y, x + fw, y + h, hover ? LanPlusUI.SURFACE_HOVER : SURFACE_RAISED);
            drawBorder(g, x, y, x + fw, y + h, BORDER);
            g.drawString(this.font, f[1], x + (fw - this.font.width(f[1])) / 2, y + 4, TEXT, false);
            String code = f[0];
            hits.add(new Hit(x, y, fw, h, () -> insertBio(code)));
            x += fw + 3;
        }
        x += 6;
        int sw = 14;
        int sgap = 2;
        int sy = y + (h - sw) / 2;
        for (int i = 0; i < MC_COLOR_CODES.length; i++) {
            int cx = x + i * (sw + sgap);
            g.fill(cx, sy, cx + sw, sy + sw, 0xFF000000 | MC_COLOR_RGB[i]);
            drawBorder(g, cx, sy, cx + sw, sy + sw, BORDER);
            char code = MC_COLOR_CODES[i];
            hits.add(new Hit(cx, sy, sw, sw, () -> insertBio("&" + code)));
        }
    }

    private void renderBioPreview(GuiGraphics g) {
        String sec = bioBox == null ? "" : ampToSection(bioBox.getValue());
        if (sec.isBlank()) {
            g.drawString(this.font, Component.translatable("gui.lanplus.profile.bio.preview"),
                    colLX, aBioPreviewY, FAINT, false);
            return;
        }
        int yy = aBioPreviewY;
        int shown = 0;
        for (FormattedCharSequence line : this.font.split(Component.literal(sec), colW)) {
            if (shown >= 2) {
                break;
            }
            g.drawString(this.font, line, colLX, yy, TEXT, false);
            yy += 11;
            shown++;
        }
    }

    private void insertBio(String code) {
        if (bioBox != null) {
            bioBox.insertText(code);
            this.setFocused(bioBox);
        }
    }

    static String ampToSection(String s) {
        return s == null ? "" : AMP_CODE.matcher(s).replaceAll("§$1");
    }

    static String sectionToAmp(String s) {
        return s == null ? "" : s.replace('§', '&');
    }

    private void renderSidebar(GuiGraphics g) {
        int left = layoutLeft();
        int top = contentTop();
        int bottom = this.height - 34;
        sbLeft = left;
        sbTop = top;
        sbBottom = bottom;
        g.fill(left, top, left + SIDEBAR_W, bottom, PANEL_BG);

        g.enableScissor(left + 1, top + 1, left + SIDEBAR_W - 1, bottom - 1);
        int l = left + 8;
        int r = left + SIDEBAR_W - 8;
        int base = top + 6 - sbScrollY;

        int y;
        if (banner != null) {
            y = renderSidebarProgression(g, l, r, base + 6);
        } else {
            drawAvatar(g, uuid, l, base, 36);
            int hx = left + 50;
            g.drawString(this.font, profile.username() == null ? "?" : profile.username(), hx, base + 2, TEXT);
            if (profile.pronouns() != null) {
                int pw = this.font.width(profile.pronouns()) + 6;
                g.fill(hx, base + 14, hx + pw, base + 25, ACCENT_TINT);
                g.drawString(this.font, profile.pronouns(), hx + 3, base + 16, LINK);
            }
            drawTierChip(g, r, base + 2);
            y = renderSidebarProgression(g, l, r, base + 38);
        }

        if (profile.friendCode() != null) {
            g.drawString(this.font, profile.friendCode(), l, y, MUTED);
            hits.add(new Hit(l, y - 1, this.font.width(profile.friendCode()), 10, () -> copyText(profile.friendCode())));
        }
        if (profile.cached()) {
            int chipW = this.font.width(Component.translatable("gui.lanplus.profile.cached").getString()) + 8;
            int rx = r - chipW;
            int ry = y - 2;
            g.fill(rx, ry, rx + chipW, ry + 11, 0x40D8A43C);
            g.drawString(this.font, Component.translatable("gui.lanplus.profile.cached"), rx + 4, ry + 1, AMBER);
        }
        int presX = r - this.font.width(presenceLabel());
        g.fill(presX - 10, y + 1, presX - 4, y + 7, profile.online() ? ONLINE : FAINT);
        g.drawString(this.font, presenceLabel(), presX, y, profile.online() ? ONLINE : MUTED);
        y += 18;

        PlayedTogether pt = profile.playedTogether();
        if (!own && pt != null && pt.hasSessions()) {
            Component label = pt.hasLastAt()
                    ? Component.translatable("gui.lanplus.profile.playedtogether.last",
                    pt.sessions(), LanPlusUI.relativeTime(pt.lastAt()))
                    : Component.translatable("gui.lanplus.profile.playedtogether", pt.sessions());
            g.drawString(this.font, label, l, y, LanPlusUI.LIME);
            y += 14;
        }

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
        boolean favIsHeadline = headline != null && isFav(favId, headline);
        boolean favOwnRow = favorite != null && favorite.name() != null && !favIsHeadline;
        if (favOwnRow) {
            y = renderSidebarModpack(g, left, y, "gui.lanplus.profile.favorite.label", favorite, own, true);
        }
        boolean recentDup = recent != null
                && ((headline != null && recent.modpackId().equals(headline.modpackId()))
                || (favOwnRow && isFav(favId, recent)));
        if (recent != null && recent.name() != null && !recentDup) {
            y = renderSidebarModpack(g, left, y, "gui.lanplus.profile.recentplayed.label", recent, own, isFav(favId, recent));
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
            g.fill(left + SIDEBAR_W - 3, barY, left + SIDEBAR_W, barY + barH, LanPlusUI.EDGE_LIGHT);
        }
    }

    private int bannerHeight() {
        return Math.clamp(layoutWidth() / 8, 44, 64);
    }

    private int contentTop() {
        return CONTENT_TOP + (banner == null ? 0 : bannerHeight() + 6);
    }

    private void renderBanner(GuiGraphics g) {
        if (banner == null) {
            return;
        }
        int left = layoutLeft();
        int right = left + layoutWidth();
        int top = CONTENT_TOP;
        int bottom = top + bannerHeight();
        g.fill(left, top, right, bottom, SLOT);
        ProfileImages.Tex tex = ProfileImages.get(banner);
        if (tex != null) {
            ProfileImages.blitCover(g, tex, left, top, right - left, bottom - top);
        }
        g.fillGradient(left, bottom - 26, right, bottom, 0x00000000, 0xA0000000);
        LanPlusUI.outline1(g, left, top, right, bottom, BORDER);
    }

    private void renderBannerIdentity(GuiGraphics g) {
        if (banner == null) {
            return;
        }
        int left = layoutLeft();
        int right = left + layoutWidth();
        int bottom = CONTENT_TOP + bannerHeight();
        int av = 36;
        int ax = left + 10;
        int ay = bottom - av + 10;
        LanPlusUI.slot(g, ax - 2, ay - 2, ax + av + 2, ay + av + 2);
        drawAvatar(g, uuid, ax, ay, av);
        int hx = ax + av + 8;
        String name = profile.username() == null ? "?" : profile.username();
        g.drawString(this.font, name, hx, bottom - 12, TEXT);
        if (profile.cached()) {
            g.drawString(this.font, Component.translatable("gui.lanplus.profile.cached"),
                    hx + this.font.width(name) + 6, bottom - 12, AMBER);
        }
        if (profile.pronouns() != null) {
            int px = hx + this.font.width(name) + 6;
            int pw = this.font.width(profile.pronouns()) + 6;
            g.fill(px, bottom - 14, px + pw, bottom - 3, ACCENT_TINT);
            g.drawString(this.font, profile.pronouns(), px + 3, bottom - 12, LINK);
        }
        drawTierChip(g, right - 8, bottom - 17);
    }

    private void drawTierChip(GuiGraphics g, int rightX, int y) {
        int tier = profile.tier();
        if (tier <= 0) {
            return;
        }
        Component label = Component.translatable("gui.lanplus.profile.tier", tier);
        int w = this.font.width(label) + 10;
        int x = rightX - w;
        g.fill(x, y, x + w, y + 13, ACCENT);
        g.fill(x, y, x + w, y + 1, ACCENT_HOVER);
        g.drawString(this.font, label, x + 5, y + 3, TEXT);
    }

    private int renderSidebarProgression(GuiGraphics g, int l, int r, int y) {
        int tier = profile.tier();
        if (profile.hasXp()) {
            int xp = profile.xp();
            int barW = r - l;
            int prev = tier <= 0 ? 0 : (int) TIER_THRESHOLDS[Math.min(tier, TIER_THRESHOLDS.length) - 1];
            int next = tier >= TIER_THRESHOLDS.length ? xp : (int) TIER_THRESHOLDS[tier];
            float frac = next <= prev ? 1f : Math.clamp((xp - prev) / (float) (next - prev), 0f, 1f);
            g.fill(l, y, l + barW, y + 4, SLOT);
            g.fill(l, y, l + (int) (barW * frac), y + 4, ACCENT);
            y += 7;
            Component line = tier >= TIER_THRESHOLDS.length
                    ? Component.translatable("gui.lanplus.profile.xp.max", xp)
                    : Component.translatable("gui.lanplus.profile.xp.next", xp,
                    Math.max(0, (int) TIER_THRESHOLDS[tier] - xp), tier + 1);
            g.drawString(this.font, line, l, y, FAINT);
            y += 13;
        } else {
            g.drawString(this.font, Component.translatable("gui.lanplus.profile.advancements", profile.advancements()),
                    l, y, FAINT);
            y += 13;
        }
        return y;
    }

    private int renderSidebarAbout(GuiGraphics g, int l, int r, int y) {
        y = sectionHeader(g, l, y, r, Component.translatable("gui.lanplus.profile.about"));
        String bio = profile.bio();
        if (bio == null || bio.isBlank()) {
            g.drawString(this.font, Component.translatable("gui.lanplus.profile.nobio"), l, y, 0xFF5C616B);
            y += 12;
        } else {
            for (FormattedCharSequence line : this.font.split(Component.literal(bio), r - l)) {
                g.drawString(this.font, line, l, y, 0xFFD3D6DC);
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
            g.drawString(this.font, platformLabel(platform), l, y, MUTED);
            String at = "@" + handle;
            g.drawString(this.font, at, r - this.font.width(at), y, tag ? MUTED : LINK);
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
        g.drawString(this.font, Component.translatable(headerKey), left + 6, y, FAINT);
        y += 12;
        int icon = 32;
        drawModpackIcon(g, left + 6, y, icon, ref);
        int nameX = left + 6 + icon + 6;
        int nameY = y + (icon - 8) / 2;
        int reserve = showStar ? 18 : 0;
        String name = ellipsize(ref.name(), left + SIDEBAR_W - nameX - 6 - reserve);
        boolean link = ref.downloadUrl() != null && !ref.downloadUrl().isBlank();
        g.drawString(this.font, name, nameX, nameY, link ? LINK : 0xFFD3D6DC);
        if (link) {
            hits.add(new Hit(nameX, nameY - 1, this.font.width(name), 10, () -> openLink(ref.downloadUrl())));
        }
        if (showStar) {
            String star = String.valueOf((char) (starred ? 0x2605 : 0x2606));
            int sx = left + SIDEBAR_W - 15;
            g.drawString(this.font, star, sx, nameY, starred ? AMBER : FAINT);
            String id = ref.modpackId();
            hits.add(new Hit(sx - 2, nameY - 3, 16, 14, () -> toggleFavorite(starred ? null : id)));
        }
        return y + icon + 6;
    }

    private void drawModpackIcon(GuiGraphics g, int x, int y, int size, ModpackRef ref) {
        LanPlusUI.slot(g, x, y, x + size, y + size);
        int strip = Math.max(2, size / 9);
        g.fill(x, y, x + size, y + strip, ACCENT);
        String name = ref.name() == null ? "" : ref.name().trim();
        String letter = name.isEmpty() ? "?" : String.valueOf(Character.toUpperCase(name.charAt(0)));
        float scale = size >= 28 ? 2.0f : 1.0f;
        g.pose().pushPose();
        g.pose().translate(x + size / 2f, y + (size + strip) / 2f, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(this.font, letter, -this.font.width(letter) / 2, -this.font.lineHeight / 2, TEXT, false);
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
                loadProfile();
            } else {
                setStatus(Component.translatable(errorKey(error)));
            }
        }));
    }

    private int renderSidebarFriends(GuiGraphics g, int left, int y) {
        List<Friend> all = new ArrayList<>(profileFriends);
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
        g.drawString(this.font, Component.translatable("gui.lanplus.profile.friends"), l, y, FAINT);
        Component count = Component.translatable("gui.lanplus.profile.friends.count", online, all.size());
        g.drawString(this.font, count, r - this.font.width(count), y, FAINT);
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
                g.fill(bx, y + 2, bx + lw, y + 13, ACCENT_TINT);
                g.drawString(this.font, lv, bx + 4, y + 4, LINK);
                nameRight = bx - 2;
            }
            g.drawString(this.font, ellipsize(f.username(), nameRight - tx), tx, y + 2, 0xFFD3D6DC);
            g.drawString(this.font, friendStatus(f), tx, y + 12, FAINT);
            y += 22;
        }
        if (all.size() > max) {
            g.drawString(this.font, Component.translatable("gui.lanplus.profile.friends.more", all.size() - max),
                    l, y + 2, FAINT);
            y += 14;
        }
        return y;
    }

    private static boolean isLive(Friend f) {
        return f.connectivity() == Connectivity.ONLINE || f.connectivity() == Connectivity.STALE;
    }

    private void renderUnifiedFrame(GuiGraphics g) {
        int left = layoutLeft();
        int top = contentTop();
        int right = left + layoutWidth();
        int bottom = this.height - 34;
        int divX = left + SIDEBAR_W;
        g.fill(divX, top + 1, divX + 1, bottom, LanPlusUI.DIVIDER);
        LanPlusUI.outline1(g, left, top, right, bottom, LanPlusUI.EDGE_DARK);
        LanPlusUI.outline1(g, left + 1, top + 1, right - 1, bottom - 1, LanPlusUI.shade(LanPlusUI.SURFACE, 1.6f));
    }

    private void renderPanel(GuiGraphics g, int mouseX, int mouseY) {
        int left = layoutLeft();
        panelX = left + SIDEBAR_W + GAP;
        panelTop = contentTop();
        panelRight = left + layoutWidth();
        panelBottom = this.height - 34;
        int textW = panelRight - panelX - 12;
        g.fill(panelX, panelTop, panelRight, panelBottom, PANEL_BG);

        g.enableScissor(panelX, panelTop, panelRight, panelBottom);
        int x = panelX + 8;
        int y = panelTop + 8 - scrollY;

        y = sectionHeader(g, x, y, x + textW, Component.translatable("gui.lanplus.profile.cosmetics"));
        y = renderCosmeticsShowcase(g, x, y, textW);
        y += SECTION_GAP;

        y = sectionHeader(g, x, y, x + textW, Component.translatable("gui.lanplus.profile.questions"));
        boolean anyPrompt = false;
        for (Prompt p : ProfilePrompt.PROMPTS) {
            String answer = profile.prompt(p.id());
            if (answer == null) {
                continue;
            }
            anyPrompt = true;
            for (FormattedCharSequence line : this.font.split(p.question(), textW)) {
                g.drawString(this.font, line, x, y, MUTED);
                y += 10;
            }
            for (FormattedCharSequence line : this.font.split(ProfilePrompt.answerText(p, answer), textW)) {
                g.drawString(this.font, line, x, y, 0xFFD3D6DC);
                y += 11;
            }
            y += 6;
        }
        if (!anyPrompt) {
            g.drawString(this.font, Component.translatable("gui.lanplus.profile.questions.empty"), x, y, 0xFF5C616B);
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
            g.fill(panelRight - 3, barY, panelRight, barY + barH, LanPlusUI.EDGE_LIGHT);
        }
    }

    private int sectionHeader(GuiGraphics g, int x, int y, int right, Component label) {
        LanPlusUI.sectionHeader(g, this.font, label, x, y, right);
        return y + 17;
    }

    private static final String[] COSMETIC_SLOTS = {"background", "head", "left_hand", "back"};

    private int renderCosmeticsShowcase(GuiGraphics g, int x, int y, int textW) {
        int renderW = 104;
        int renderH = 150;
        cmBoxX = x;
        cmBoxY = y;
        cmBoxW = renderW;
        cmBoxH = renderH;

        g.fill(x, y, x + renderW, y + renderH, SLOT);
        LanPlusUI.outline1(g, x, y, x + renderW, y + renderH, BORDER);
        g.flush();
        g.enableScissor(x + 1, y + 1, x + renderW - 1, y + renderH - 1);
        drawPlayerModel(g, x + renderW / 2, y + renderH - 26);
        g.disableScissor();
        g.drawString(this.font, Component.translatable("gui.lanplus.profile.cosmetics.rotate"),
                x + 8, y + renderH - 12, FAINT);
        int sx = x + renderW + 8;
        int sw = textW - renderW - 8;
        int slotH = 26;
        int slotGap = 4;
        Component soon = Component.translatable("gui.lanplus.profile.cosmetics.soon");
        for (int i = 0; i < COSMETIC_SLOTS.length; i++) {
            int sy = y + i * (slotH + slotGap);
            LanPlusUI.button3d(g, sx, sy, sx + sw, sy + slotH, SURFACE_RAISED);
            g.drawString(this.font, Component.translatable("gui.lanplus.profile.cosmetics." + COSMETIC_SLOTS[i]),
                    sx + 7, sy + 5, 0xFFD3D6DC);
            Component sub = soon;
            if (COSMETIC_SLOTS[i].equals("background")) {
                sub = bgStyle == BG_IMAGE && bgImageId != null
                        ? Component.literal(bgImageId)
                        : Component.translatable(switch (bgStyle) {
                    case BG_SOLID -> "gui.lanplus.profile.bg.solid";
                    case BG_MINECRAFT -> "gui.lanplus.profile.bg.minecraft";
                    case BG_IMAGE -> "gui.lanplus.profile.bg.image";
                    default -> "gui.lanplus.profile.bg.dark";
                });
            }
            g.drawString(this.font, ellipsize(sub.getString(), sw - 14), sx + 7, sy + 15, FAINT);
        }
        int slotsH = COSMETIC_SLOTS.length * (slotH + slotGap) - slotGap;
        return y + Math.max(renderH, slotsH);
    }

    private void drawPlayerModel(GuiGraphics g, int cx, int feetY) {
        SkinTextures st = LanPlusClient.skinTextures();
        SkinTextures.Resolved res = st == null ? null : st.get(uuid);
        ResourceLocation skin = res != null ? res.texture() : resolveFallbackSkin();
        boolean slim = res != null && res.slim();
        PlayerPreview.render(g, cx, feetY, 52f * modelZoom, modelYaw, modelPitch, skin, slim, uuid);
    }

    private void drawBorder(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        LanPlusUI.outline1(g, x1, y1, x2, y2, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (editing && button == 0) {
            int t = editTabAt(mouseX, mouseY);
            if (t >= 0) {
                if (t != editTab) {
                    editTab = t;
                    rebuildWidgets();
                }
                return true;
            }
        }
        if (editing && button == 0 && linkPickerOpen >= 0) {
            for (int[] c : linkPickerCells) {
                if (mouseX >= c[0] && mouseX < c[0] + c[2] && mouseY >= c[1] && mouseY < c[1] + c[3]) {
                    pickLinkPlatform(linkPickerOpen, c[4]);
                    return true;
                }
            }
            int pb = linkPickerButtonAt(mouseX, mouseY);
            linkPickerOpen = (pb >= 0 && pb != linkPickerOpen) ? pb : -1;
            return true;
        }
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
            modelYaw -= (float) dragX;
            modelPitch = Math.clamp(modelPitch - (float) dragY, -35f, 35f);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        if (!editing && inCmBox(mouseX, mouseY)) {
            modelZoom = Math.clamp(modelZoom + (float) delta * 0.15f, 0.6f, 2.5f);
            return true;
        }
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
        return super.mouseScrolled(mouseX, mouseY, scrollX, delta);
    }

    private void doSave() {
        ProfilesService svc = LanPlusClient.profiles();
        if (svc == null || bioBox == null) {
            return;
        }
        captureFreeValues();
        captureLinkValues();
        String bio = ampToSection(bioBox.getValue());
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
            Prompt p = ProfilePrompt.byId(slotPromptId[i]);
            if (p == null) {
                continue;
            }
            if (p.type() == ProfilePrompt.Type.FREE) {
                String value = slotFreeValue[i] == null ? "" : slotFreeValue[i].trim();
                if (!value.isEmpty()) {
                    prompts.put(p.id(), value);
                }
            } else if (slotChoiceToken[i] != null) {
                prompts.put(p.id(), slotChoiceToken[i]);
            }
        }
        setStatus(Component.translatable("gui.lanplus.profile.saving"));
        CompletableFuture<String> bgF = svc.setBackground(bgStyleName(), bgColor, bgOpacity, bgImageId);
        CompletableFuture<String> bannerF = svc.setBanner(banner == null ? null : banner.id());
        CompletableFuture<String> saveF = svc.save(bio, pronouns, links, prompts, invisibleToggle,
                favoriteVisibleToggle, playingVisibleToggle, recentlyPlayedVisibleToggle);
        CompletableFuture.allOf(bgF, bannerF, saveF).whenComplete((v, ex) -> this.minecraft.execute(() -> {
            String error = ex != null ? "offline"
                    : saveF.getNow(null) != null ? saveF.getNow(null)
                    : bgF.getNow(null) != null ? bgF.getNow(null) : bannerF.getNow(null);
            if (error == null) {
                editing = false;
                loaded = false;
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
            if (p != null) {
                applyBackground(p.background());
                this.banner = p.banner();
                if (p.skin() != null && LanPlusClient.skins() != null) {
                    LanPlusClient.skins().resolve(p.uuid(), p.skin());
                }
            }
            this.loaded = true;
            if (this.minecraft.screen == this) {
                rebuildWidgets();
            }
        }));
        loadSidebarFriends();
    }

    private void loadSidebarFriends() {
        LanPlusNetwork network = LanPlusClient.network();
        if (network == null) {
            return;
        }
        network.getFriends(uuid).whenComplete((friends, ex) -> this.minecraft.execute(() -> {
            if (friends != null) {
                this.profileFriends = friends;
                if (LanPlusClient.skins() != null) {
                    LanPlusClient.resolveFriendSkins(friends);
                }
            }
        }));
    }

    private void applyBackground(ProfileBackground bg) {
        if (bg == null) {
            return;
        }
        bgStyle = switch (bg.style()) {
            case "SOLID" -> BG_SOLID;
            case "MINECRAFT" -> BG_MINECRAFT;
            case "IMAGE" -> BG_IMAGE;
            case "NONE" -> BG_NONE;
            default -> BG_DARK;
        };
        bgColor = bg.color() & 0xFFFFFF;
        bgOpacity = Math.clamp(bg.opacity(), 0, 100);
        bgImage = bg.image();
        bgImageId = bg.image() == null ? null : bg.image().id();
    }

    private String bgStyleName() {
        return switch (bgStyle) {
            case BG_SOLID -> "SOLID";
            case BG_MINECRAFT -> "MINECRAFT";
            case BG_IMAGE -> "IMAGE";
            case BG_NONE -> "NONE";
            default -> "DARK";
        };
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

    private ResourceLocation resolveFallbackSkin() {
        Minecraft mc = this.minecraft;
        if (own && mc != null) {
            net.minecraft.client.player.AbstractClientPlayer local = mc.player;
            if (local != null && uuid.equals(local.getUUID())) {
                ResourceLocation loc = local.getSkin().texture();
                return loc;
            }
        }
        return DefaultPlayerSkin.get(uuid).texture();
    }

    private void drawAvatar(GuiGraphics g, UUID id, int x, int y, int size) {
        SkinTextures textures = LanPlusClient.skinTextures();
        SkinTextures.Resolved resolved = textures == null ? null : textures.get(id);
        ResourceLocation tex;
        if (resolved != null) {
            tex = resolved.texture();
        } else if (own && uuid.equals(id) && this.minecraft != null) {
            net.minecraft.client.player.AbstractClientPlayer local = this.minecraft.player;
            tex = local != null && uuid.equals(local.getUUID())
                    ? local.getSkin().texture()
                    : DefaultPlayerSkin.get(id).texture();
        } else {
            tex = DefaultPlayerSkin.get(id).texture();
        }
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

    private int pickerWidth() {
        return colW / 2 - 3;
    }

    private Component pickerLabel(int slot) {
        Prompt p = ProfilePrompt.byId(slotPromptId[slot]);
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
        Prompt p = ProfilePrompt.byId(slotPromptId[slot]);
        if (p == null || slotChoiceToken[slot] == null) {
            return Component.empty();
        }
        return ProfilePrompt.choiceLabel(p, slotChoiceToken[slot]);
    }

    private Component presenceLabel() {
        if (profile.online()) {
            return Component.translatable("gui.lanplus.profile.online");
        }
        if (profile.lastSeen() <= 0) {
            return Component.translatable("gui.lanplus.profile.offline");
        }
        return Component.translatable("gui.lanplus.profile.lastseen", LanPlusUI.relativeTime(profile.lastSeen()));
    }

    private Component friendStatus(Friend f) {
        if (f.blocked()) {
            return Component.translatable("gui.lanplus.rel.blocked");
        }
        if (f.muted()) {
            return Component.translatable("gui.lanplus.rel.muted");
        }
        if (!isLive(f)) {
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
            case ONLINE -> LanPlusUI.ONLINE;
            case STALE -> LanPlusUI.AMBER;
            case OFFLINE -> LanPlusUI.FAINT;
            case UNKNOWN -> 0xFF4A4E57;
        };
    }

    private void setStatus(Component message) {
        this.status = message;
        this.statusUntil = System.currentTimeMillis() + STATUS_DISPLAY_MS;
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
            case "content_blocked" -> "gui.lanplus.profile.err.content_blocked";
            case "bad_pronouns" -> "gui.lanplus.profile.err.bad_pronouns";
            case "bad_link" -> "gui.lanplus.profile.err.bad_link";
            case "bad_prompt" -> "gui.lanplus.profile.err.bad_prompt";
            case "prompt_too_long" -> "gui.lanplus.profile.err.prompt_too_long";
            case "prompt_link" -> "gui.lanplus.profile.err.prompt_link";
            case "too_many_prompts" -> "gui.lanplus.profile.err.too_many_prompts";
            case "bad_modpack" -> "gui.lanplus.profile.err.bad_modpack";
            case "bad_background" -> "gui.lanplus.profile.err.bad_background";
            case "bad_banner" -> "gui.lanplus.profile.err.bad_banner";
            case "bad_png" -> "gui.lanplus.profile.skin.err.bad_png";
            case "too_large" -> "gui.lanplus.profile.skin.err.too_large";
            case "bad_dimensions" -> "gui.lanplus.profile.skin.err.bad_dimensions";
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