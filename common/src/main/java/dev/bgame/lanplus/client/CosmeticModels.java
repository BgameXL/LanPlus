package dev.bgame.lanplus.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bgame.lanplus.LanplusCommon;
import dev.bgame.lanplus.cosmetics.CosmeticLoader;
import dev.bgame.lanplus.cosmetics.CosmeticMeta;
import dev.bgame.lanplus.cosmetics.CosmeticModel;
import dev.bgame.lanplus.cosmetics.CosmeticSlot;
import dev.bgame.lanplus.cosmetics.CosmeticTransform;
import dev.bgame.lanplus.cosmetics.blockbench.BlockbenchImporter;
import dev.bgame.lanplus.cosmetics.geckolib.cache.object.BakedGeoModel;
import dev.bgame.lanplus.cosmetics.geckolib.loading.object.BakedAnimations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class CosmeticModels {

    private final Map<String, CosmeticModel> models = new ConcurrentHashMap<>();
    private final Map<String, CosmeticAnimator> animators = new ConcurrentHashMap<>();
    private final Map<UUID, Map<CosmeticSlot, String>> loadout = new ConcurrentHashMap<>();
    private final Map<UUID, Map<CosmeticSlot, String>> localLoadout = new ConcurrentHashMap<>();
    private final Map<String, CosmeticMeta> meta = new ConcurrentHashMap<>();
    private final Map<String, float[]> boundsCache = new ConcurrentHashMap<>();
    private final Set<String> owned = ConcurrentHashMap.newKeySet();
    private final Set<String> local = ConcurrentHashMap.newKeySet();
    private final AtomicInteger seq = new AtomicInteger();
    private volatile int wallet;

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

    public CosmeticTransform transformOf(String id) {
        CosmeticMeta m = meta.get(id);
        return m != null ? m.transform() : CosmeticTransform.IDENTITY;
    }

    public List<String> idsForSlot(CosmeticSlot slot) {
        Set<String> known = new HashSet<>(meta.keySet());
        known.addAll(models.keySet());
        List<String> out = new ArrayList<>();
        for (String id : known) {
            if (slotOf(id) == slot) {
                out.add(id);
            }
        }
        out.sort(null);
        return out;
    }

    public List<String> allIds() {
        Set<String> known = new HashSet<>(meta.keySet());
        known.addAll(models.keySet());
        List<String> out = new ArrayList<>(known);
        out.sort(null);
        return out;
    }

    public int wallet() {
        return wallet;
    }

    public void setWallet(int balance) {
        wallet = Math.max(0, balance);
    }

    public boolean owns(String id) {
        return id != null && (owned.contains(id) || local.contains(id));
    }

    public boolean isLocal(String id) {
        return id != null && local.contains(id);
    }

    public void markLocal(String id) {
        if (id != null) {
            local.add(id);
        }
    }

    public void markOfficial(String id) {
        if (id != null) {
            local.remove(id);
            for (Map<CosmeticSlot, String> equipped : localLoadout.values()) {
                equipped.entrySet().removeIf(entry -> id.equals(entry.getValue()));
            }
        }
    }

    public void setOwned(Set<String> ids) {
        owned.clear();
        if (ids != null) {
            owned.addAll(ids);
        }
    }

    public void markOwned(String id) {
        if (id != null) {
            owned.add(id);
        }
    }

    public boolean spend(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (wallet < amount) {
            return false;
        }
        wallet -= amount;
        return true;
    }

    public float[] bounds(String id) {
        return boundsCache.computeIfAbsent(id, k -> {
            CosmeticModel m = models.get(k);
            return m == null ? new float[]{-1, -1, -1, 1, 1, 1} : CosmeticGeoRender.bounds(m.geometry());
        });
    }

    public String equipped(UUID player, CosmeticSlot slot) {
        Map<CosmeticSlot, String> localEquipped = player == null ? null : localLoadout.get(player);
        if (localEquipped != null && localEquipped.containsKey(slot)) {
            return localEquipped.get(slot);
        }
        Map<CosmeticSlot, String> map = player == null ? null : loadout.get(player);
        return map == null ? null : map.get(slot);
    }

    public Map<CosmeticSlot, String> loadout(UUID player) {
        if (player == null) {
            return null;
        }
        Map<CosmeticSlot, String> official = loadout.get(player);
        Map<CosmeticSlot, String> localEquipped = localLoadout.get(player);
        if (localEquipped == null || localEquipped.isEmpty()) {
            return official == null || official.isEmpty() ? null : official;
        }
        if (official == null || official.isEmpty()) {
            return localEquipped;
        }
        Map<CosmeticSlot, String> merged = new EnumMap<>(CosmeticSlot.class);
        merged.putAll(official);
        merged.putAll(localEquipped);
        return merged;
    }

    public void equip(UUID player, CosmeticSlot slot, String id) {
        if (player == null || slot == null || id == null) {
            return;
        }
        if (local.contains(id)) {
            localLoadout.computeIfAbsent(player, u -> new ConcurrentHashMap<>()).put(slot, id);
            return;
        }
        Map<CosmeticSlot, String> localEquipped = localLoadout.get(player);
        if (localEquipped != null) {
            localEquipped.remove(slot);
        }
        loadout.computeIfAbsent(player, u -> new ConcurrentHashMap<>()).put(slot, id);
    }

    public void unequip(UUID player, CosmeticSlot slot) {
        Map<CosmeticSlot, String> localEquipped = localLoadout.get(player);
        if (localEquipped != null && localEquipped.remove(slot) != null) {
            return;
        }
        Map<CosmeticSlot, String> equipped = loadout.get(player);
        if (equipped != null) {
            equipped.remove(slot);
        }
    }

    public void applyLoadout(UUID player, Map<CosmeticSlot, String> map) {
        if (player == null) {
            return;
        }
        if (map == null || map.isEmpty()) {
            loadout.remove(player);
        } else {
            loadout.put(player, new ConcurrentHashMap<>(map));
        }
    }

    public void animate(String id, UUID player, String state, float limbSwing, float limbSwingAmount, float partialTick) {
        CosmeticAnimator animator = animators.get(id);
        if (animator != null && player != null) {
            CosmeticMeta cosmeticMeta = meta.get(id);
            String requestedClip = cosmeticMeta == null ? null : cosmeticMeta.stateClipMap().get(state);
            if ((requestedClip == null || requestedClip.isBlank()) && state != null && !state.isBlank()) {
                requestedClip = BlockbenchImporter.clipName(id, state);
            }
            String defaultClip = cosmeticMeta == null ? null : cosmeticMeta.defaultClip();
            animator.apply(player.getMostSignificantBits() ^ player.getLeastSignificantBits(), limbSwing,
                    limbSwingAmount, partialTick, requestedClip, defaultClip);
        }
    }

    public boolean register(String id, byte[] geoJson, byte[] animJson, byte[] png) {
        if (id == null || geoJson == null) {
            return false;
        }
        BakedGeoModel geometry;
        BakedAnimations animations = null;
        try {
            geometry = CosmeticLoader.geometry(geoJson);
            if (animJson != null) {
                animations = CosmeticLoader.animations(animJson);
            }
        } catch (RuntimeException e) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        BakedAnimations bakedAnimations = animations;
        mc.execute(() -> {
            CosmeticModel model = new CosmeticModel(geometry, bakedAnimations, registerTexture(mc, png),
                    png == null ? 0xFF101010 : -1);
            models.put(id, model);
            boundsCache.remove(id);

            CosmeticAnimator animator = CosmeticAnimator.create(model);
            if (animator != null) {
                animators.put(id, animator);
            } else {
                animators.remove(id);
            }
        });
        return true;
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
