package dev.bgame.lanplus.client.gui;

import dev.bgame.lanplus.api.Profile;
import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.client.SkinTextures;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class ProfileScreen extends Screen {

    private static final int PANEL_BG = 0xC0101018;
    private static final int MARGIN = 20;
    private static final int CONTENT_TOP = 84;
    private static final String[] PLATFORMS =
            {"discord", "instagram", "twitter", "youtube", "twitch", "tiktok", "paypal", "kofi"};
    private static final String[] PRONOUN_CYCLE = {null, "he/him", "she/her", "they/them"};

    private final Screen parent;
    private final UUID uuid;
    private final boolean own;

    private Profile profile;
    private boolean loaded;
    private boolean editing;
    private Component status;

    private EditBox bioBox;
    private Button pronounButton;
    private Button invisibleButton;
    private final EditBox[] linkBoxes = new EditBox[PLATFORMS.length];
    private int pronounIndex;
    private boolean invisibleToggle;

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

    @Override
    protected void init() {
        if (!loaded) {
            loadProfile();
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(this.width - 104, this.height - 28, 84, 20).build());

        if (loaded && profile != null) {
            if (editing) {
                buildEditWidgets();
                addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.profile.save"), b -> doSave())
                        .bounds(MARGIN, this.height - 28, 90, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.profile.cancel"),
                                b -> { editing = false; status = null; rebuildWidgets(); })
                        .bounds(MARGIN + 96, this.height - 28, 90, 20).build());
            } else {
                buildLinkButtons();
                if (own) {
                    addRenderableWidget(Button.builder(Component.translatable("gui.lanplus.profile.edit"),
                                    b -> { editing = true; primeEdit(); rebuildWidgets(); })
                            .bounds(MARGIN, this.height - 28, 90, 20).build());
                }
            }
        }
    }

    private void buildEditWidgets() {
        int w = this.width - 2 * MARGIN;
        bioBox = new EditBox(this.font, MARGIN, CONTENT_TOP + 12, w, 20, Component.translatable("gui.lanplus.profile.about"));
        bioBox.setMaxLength(300);
        bioBox.setHint(Component.translatable("gui.lanplus.profile.bio.hint"));
        bioBox.setValue(profile.bio() == null ? "" : profile.bio());
        addRenderableWidget(bioBox);

        pronounButton = Button.builder(pronounLabel(), b -> {
            pronounIndex = (pronounIndex + 1) % PRONOUN_CYCLE.length;
            pronounButton.setMessage(pronounLabel());
        }).bounds(MARGIN, CONTENT_TOP + 38, 150, 20).build();
        addRenderableWidget(pronounButton);

        invisibleButton = Button.builder(invisibleLabel(), b -> {
            invisibleToggle = !invisibleToggle;
            invisibleButton.setMessage(invisibleLabel());
        }).bounds(MARGIN + 160, CONTENT_TOP + 38, 170, 20).build();
        addRenderableWidget(invisibleButton);

        int cx = this.width / 2;
        int linksTop = CONTENT_TOP + 78;
        int colW = cx - MARGIN - 12;
        int labelW = 58;
        for (int i = 0; i < PLATFORMS.length; i++) {
            int x = (i / 4 == 0 ? MARGIN : cx + 6) + labelW;
            int y = linksTop + (i % 4) * 22;
            EditBox box = new EditBox(this.font, x, y, colW - labelW, 18, Component.literal(PLATFORMS[i]));
            box.setMaxLength(40);
            String cur = profile.link(PLATFORMS[i]);
            box.setValue(cur == null ? "" : cur);
            linkBoxes[i] = box;
            addRenderableWidget(box);
        }
    }

    private void buildLinkButtons() {
        int cx = this.width / 2;
        int top = CONTENT_TOP + 64;
        int idx = 0;
        for (String platform : PLATFORMS) {
            String handle = profile.link(platform);
            if (handle == null || handle.isBlank()) {
                continue;
            }
            int x = (idx % 2 == 0 ? MARGIN : cx + 6);
            int y = top + (idx / 2) * 22;
            Component label = Component.literal(platformLabel(platform) + ": " + handle);
            Button.Builder builder = platform.equals("discord")
                    ? Button.builder(label, b -> copyHandle(handle))
                    : Button.builder(label, b -> openLink(linkUrl(platform, handle)));
            addRenderableWidget(builder.bounds(x, y, cx - MARGIN - 12, 20).build());
            idx++;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.drawString(this.font, this.title, MARGIN, 12, 0xFFFFFFFF);
        int panelTop = 34;
        int panelBottom = this.height - 36;
        g.fill(MARGIN, panelTop, this.width - MARGIN, panelBottom, PANEL_BG);

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

        drawAvatar(g, uuid, MARGIN + 6, panelTop + 6, 40);
        int hx = MARGIN + 54;
        g.drawString(this.font, profile.username() == null ? "?" : profile.username(), hx, panelTop + 8, 0xFFFFFFFF);
        if (profile.friendCode() != null) {
            g.drawString(this.font, profile.friendCode(), hx, panelTop + 20, 0xFF9AA0A6);
        }
        String pronouns = editing ? PRONOUN_CYCLE[pronounIndex] : profile.pronouns();
        if (pronouns != null) {
            g.drawString(this.font, pronouns, hx, panelTop + 32, 0xFF7AA7FF);
        }
        if (!editing) {
            Component presence = presenceLabel();
            int color = profile.online() ? 0xFF55C46A : 0xFF9AA0A6;
            g.drawString(this.font, presence, this.width - MARGIN - 6 - this.font.width(presence), panelTop + 8, color);
        }

        g.drawString(this.font, Component.translatable("gui.lanplus.profile.about"), MARGIN, CONTENT_TOP, 0xFFB9BDC2);
        if (editing) {
            g.drawString(this.font, Component.translatable("gui.lanplus.profile.links"), MARGIN, CONTENT_TOP + 64, 0xFFB9BDC2);
            int cx = this.width / 2;
            int linksTop = CONTENT_TOP + 78;
            for (int i = 0; i < PLATFORMS.length; i++) {
                int x = (i / 4 == 0 ? MARGIN : cx + 6);
                int y = linksTop + (i % 4) * 22;
                g.drawString(this.font, PLATFORMS[i], x, y + 5, 0xFF9AA0A6);
            }
        } else {
            int y = CONTENT_TOP + 12;
            String bio = profile.bio();
            if (bio == null || bio.isBlank()) {
                g.drawString(this.font, Component.translatable("gui.lanplus.profile.nobio"), MARGIN, y, 0xFF6A6E74);
            } else {
                for (FormattedCharSequence line : this.font.split(Component.literal(bio), this.width - 2 * MARGIN - 8)) {
                    g.drawString(this.font, line, MARGIN, y, 0xFFD0D3D8);
                    y += 11;
                }
            }
            if (hasAnyLink()) {
                g.drawString(this.font, Component.translatable("gui.lanplus.profile.links"), MARGIN, CONTENT_TOP + 52, 0xFFB9BDC2);
            }
        }

        if (status != null) {
            g.drawString(this.font, status, MARGIN, this.height - 30, 0xFFE0A030);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void doSave() {
        ProfilesService svc = LanPlusClient.profiles();
        if (svc == null || bioBox == null) {
            return;
        }
        String bio = bioBox.getValue();
        String pronouns = PRONOUN_CYCLE[pronounIndex];
        Map<String, String> links = new LinkedHashMap<>();
        for (int i = 0; i < PLATFORMS.length; i++) {
            String value = linkBoxes[i].getValue().trim();
            links.put(PLATFORMS[i], value.isEmpty() ? null : value);
        }
        setStatus(Component.translatable("gui.lanplus.profile.saving"));
        svc.save(bio, pronouns, links, invisibleToggle).whenComplete((error, ex) -> this.minecraft.execute(() -> {
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

    private void primeEdit() {
        pronounIndex = 0;
        invisibleToggle = profile != null && profile.invisible();
        if (profile != null && profile.pronouns() != null) {
            for (int i = 0; i < PRONOUN_CYCLE.length; i++) {
                if (profile.pronouns().equals(PRONOUN_CYCLE[i])) {
                    pronounIndex = i;
                    break;
                }
            }
        }
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

    private void copyHandle(String handle) {
        this.minecraft.keyboardHandler.setClipboard(handle);
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

    private Component presenceLabel() {
        if (profile.online()) {
            return Component.translatable("gui.lanplus.profile.online");
        }
        if (profile.lastSeen() <= 0) {
            return Component.translatable("gui.lanplus.profile.offline");
        }
        return Component.translatable("gui.lanplus.profile.lastseen", relativeTime(profile.lastSeen()));
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
