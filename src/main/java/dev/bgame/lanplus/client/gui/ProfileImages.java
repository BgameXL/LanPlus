package dev.bgame.lanplus.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bgame.lanplus.Lanplus;
import dev.bgame.lanplus.api.CatalogImage;
import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.profiles.ProfilesService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ProfileImages {

    record Tex(ResourceLocation location, int width, int height) {}

    private static final ConcurrentHashMap<String, Tex> TEXTURES = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

    private ProfileImages() {}

    static Tex get(CatalogImage image) {
        if (image == null || image.url() == null) {
            return null;
        }
        Tex tex = TEXTURES.get(image.url());
        if (tex != null) {
            return tex;
        }
        ProfilesService profiles = LanPlusClient.profiles();
        if (profiles == null || !PENDING.add(image.url())) {
            return null;
        }
        profiles.imageBytes(image).whenComplete((bytes, ex) -> {
            if (bytes == null || ex != null) {
                PENDING.remove(image.url());
                return;
            }
            Minecraft.getInstance().execute(() -> register(image, bytes));
        });
        return null;
    }

    private static void register(CatalogImage image, byte[] bytes) {
        try {
            NativeImage ni = NativeImage.read(new ByteArrayInputStream(bytes));
            DynamicTexture dyn = new DynamicTexture(ni);
            String name = image.hash() == null || image.hash().isEmpty()
                    ? Integer.toHexString(image.url().hashCode())
                    : image.hash().toLowerCase(Locale.ROOT);
            ResourceLocation loc = new ResourceLocation(Lanplus.MODID, "profile_images/" + name);
            Minecraft.getInstance().getTextureManager().register(loc, dyn);
            TEXTURES.put(image.url(), new Tex(loc, ni.getWidth(), ni.getHeight()));
        } catch (IOException | RuntimeException e) {
        }
    }

    static void blitCover(GuiGraphics g, Tex tex, int x, int y, int w, int h) {
        if (tex == null || w <= 0 || h <= 0) {
            return;
        }
        float scale = Math.max(w / (float) tex.width(), h / (float) tex.height());
        int srcW = Math.max(1, Math.round(w / scale));
        int srcH = Math.max(1, Math.round(h / scale));
        int u = (tex.width() - srcW) / 2;
        int v = (tex.height() - srcH) / 2;
        g.blit(tex.location(), x, y, w, h, u, v, srcW, srcH, tex.width(), tex.height());
    }
}