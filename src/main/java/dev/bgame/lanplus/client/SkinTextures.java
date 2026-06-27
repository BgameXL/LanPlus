package dev.bgame.lanplus.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bgame.lanplus.Lanplus;
import dev.bgame.lanplus.skins.SkinTextureSink;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-side binding of resolved skin bytes to Minecraft textures (kept out of the side-agnostic
 * skins package). Textures are registered once per cache key; each player just points at one. Queried
 * by the friends UI avatars and the in-world {@code AbstractClientPlayerMixin}.
 */
public final class SkinTextures implements SkinTextureSink {

    public record Resolved(ResourceLocation texture, boolean slim) {}

    private final ConcurrentHashMap<String, ResourceLocation> byKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Resolved> byPlayer = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger();

    public Resolved get(UUID player) {
        return byPlayer.get(player);
    }

    @Override
    public void accept(UUID player, String key, byte[] png, String model) {
        boolean slim = "slim".equalsIgnoreCase(model);
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            ResourceLocation loc = byKey.computeIfAbsent(key, k -> register(mc, png));
            if (loc != null) {
                byPlayer.put(player, new Resolved(loc, slim));
            }
        });
    }

    private ResourceLocation register(Minecraft mc, byte[] png) {
        try {
            NativeImage image = processSkin(NativeImage.read(png));
            if (image == null) {
                return null;
            }
            DynamicTexture tex = new DynamicTexture(image);
            ResourceLocation loc = new ResourceLocation(Lanplus.MODID, "skins/" + seq.getAndIncrement());
            mc.getTextureManager().register(loc, tex);
            return loc;
        } catch (Exception e) {
            return null;
        }
    }

    private static NativeImage processSkin(NativeImage image) {
        int height = image.getHeight();
        int width = image.getWidth();
        if (width != 64 || (height != 32 && height != 64)) {
            image.close();
            return null;
        }
        boolean legacy = height == 32;
        if (legacy) {
            NativeImage converted = new NativeImage(64, 64, true);
            converted.copyFrom(image);
            image.close();
            image = converted;
            converted.fillRect(0, 32, 64, 32, 0);
            converted.copyRect(4, 16, 16, 32, 4, 4, true, false);
            converted.copyRect(8, 16, 16, 32, 4, 4, true, false);
            converted.copyRect(0, 20, 24, 32, 4, 12, true, false);
            converted.copyRect(4, 20, 16, 32, 4, 12, true, false);
            converted.copyRect(8, 20, 8, 32, 4, 12, true, false);
            converted.copyRect(12, 20, 16, 32, 4, 12, true, false);
            converted.copyRect(44, 16, -8, 32, 4, 4, true, false);
            converted.copyRect(48, 16, -8, 32, 4, 4, true, false);
            converted.copyRect(40, 20, 0, 32, 4, 12, true, false);
            converted.copyRect(44, 20, -8, 32, 4, 12, true, false);
            converted.copyRect(48, 20, -16, 32, 4, 12, true, false);
            converted.copyRect(52, 20, -8, 32, 4, 12, true, false);
        }
        setNoAlpha(image, 0, 0, 32, 16);
        if (legacy) {
            doNotchTransparencyHack(image, 32, 0, 64, 32);
        }
        setNoAlpha(image, 0, 16, 64, 32);
        setNoAlpha(image, 16, 48, 48, 64);
        return image;
    }

    private static void doNotchTransparencyHack(NativeImage image, int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                if ((image.getPixelRGBA(x, y) >> 24 & 255) < 128) {
                    return;
                }
            }
        }
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                image.setPixelRGBA(x, y, image.getPixelRGBA(x, y) & 0xFFFFFF);
            }
        }
    }

    private static void setNoAlpha(NativeImage image, int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                image.setPixelRGBA(x, y, image.getPixelRGBA(x, y) | 0xFF000000);
            }
        }
    }
}