package dev.bgame.lanplus.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bgame.lanplus.LanplusCommon;
import dev.bgame.lanplus.cosmetics.CosmeticLoader;
import dev.bgame.lanplus.cosmetics.CosmeticMeta;
import dev.bgame.lanplus.cosmetics.CosmeticModel;
import dev.bgame.lanplus.cosmetics.CosmeticSlot;
import dev.bgame.lanplus.cosmetics.geckolib.cache.object.BakedGeoModel;
import dev.bgame.lanplus.cosmetics.geckolib.loading.object.BakedAnimations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class CosmeticModels {

    private final Map<String, CosmeticModel> models = new ConcurrentHashMap<>();
    private final Map<String, CosmeticAnimator> animators = new ConcurrentHashMap<>();
    private final Map<UUID, Map<CosmeticSlot, String>> loadout = new ConcurrentHashMap<>();
    private final Map<String, CosmeticMeta> meta = new ConcurrentHashMap<>();
    private final Map<String, float[]> boundsCache = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger();

    public CosmeticModel model(String id) {
        return id == null ? null : models.get(id);
    }

    public List<String> ids() {
        List<String> out = new ArrayList<>(models.keySet());
        out.sort(null);
        return out;
    }

    public void putMeta(CosmeticMeta m) {
        if (m != null) {
            meta.put(m.id(), m);
        }
    }

    public CosmeticMeta meta(String id) {
        return id == null ? null : meta.get(id);
    }

    public CosmeticSlot slotOf(String id) {
        CosmeticMeta m = meta.get(id);
        return m != null ? m.slot() : CosmeticSlot.HEAD;
    }

    public List<String> idsForSlot(CosmeticSlot slot) {
        List<String> out = new ArrayList<>();
        for (String id : models.keySet()) {
            if (slotOf(id) == slot) {
                out.add(id);
            }
        }
        out.sort(null);
        return out;
    }

    public float[] bounds(String id) {
        return boundsCache.computeIfAbsent(id, k -> {
            CosmeticModel m = models.get(k);
            return m == null ? new float[]{-1, -1, -1, 1, 1, 1} : CosmeticGeoRender.bounds(m.geometry());
        });
    }

    public String equipped(UUID player, CosmeticSlot slot) {
        Map<CosmeticSlot, String> map = player == null ? null : loadout.get(player);
        return map == null ? null : map.get(slot);
    }

    public Map<CosmeticSlot, String> loadout(UUID player) {
        return player == null ? null : loadout.get(player);
    }

    public void equip(UUID player, CosmeticSlot slot, String id) {
        if (player == null || slot == null || id == null) {
            return;
        }
        loadout.computeIfAbsent(player, u -> new ConcurrentHashMap<>()).put(slot, id);
    }

    public void unequip(UUID player, CosmeticSlot slot) {
        Map<CosmeticSlot, String> equipped = loadout.get(player);
        if (equipped != null) {
            equipped.remove(slot);
        }
    }

    public void animate(String id, UUID player, float limbSwing, float limbSwingAmount, float partialTick) {
        CosmeticAnimator animator = animators.get(id);
        if (animator != null && player != null) {
            animator.apply(player.getMostSignificantBits() ^ player.getLeastSignificantBits(), limbSwing,
                    limbSwingAmount, partialTick);
        }
    }

    public void register(String id, byte[] geoJson, byte[] animJson, byte[] png) {
        if (id == null || geoJson == null) {
            return;
        }
        BakedGeoModel geometry;
        BakedAnimations animations = null;
        try {
            geometry = CosmeticLoader.geometry(geoJson);
            if (animJson != null) {
                animations = CosmeticLoader.animations(animJson);
            }
        } catch (RuntimeException e) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        BakedAnimations bakedAnimations = animations;
        mc.execute(() -> {
            CosmeticModel model = new CosmeticModel(geometry, bakedAnimations, registerTexture(mc, png),
                    png == null ? 0xFF101010 : -1);
            models.put(id, model);

            CosmeticAnimator animator = CosmeticAnimator.create(model);
            if (animator != null) {
                animators.put(id, animator);
            } else {
                animators.remove(id);
            }
        });
    }

    private ResourceLocation registerTexture(Minecraft mc, byte[] png) {
        try {
            NativeImage image = png != null ? NativeImage.read(png) : placeholder();
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(LanplusCommon.MODID, "cosmetics/" + seq.getAndIncrement());
            mc.getTextureManager().register(loc, new DynamicTexture(image));
            return loc;
        } catch (Exception e) {
            return MissingTextureAtlasSprite.getLocation();
        }
    }

    private static NativeImage placeholder() {
        NativeImage image = new NativeImage(16, 16, false);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setPixelRGBA(x, y, 0xFFFFFFFF);
            }
        }
        return image;
    }
}
