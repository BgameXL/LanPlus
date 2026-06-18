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
            DynamicTexture tex = new DynamicTexture(NativeImage.read(png));
            ResourceLocation loc = new ResourceLocation(Lanplus.MODID, "skins/" + seq.getAndIncrement());
            mc.getTextureManager().register(loc, tex);
            return loc;
        } catch (Exception e) {
            return null;
        }
    }
}