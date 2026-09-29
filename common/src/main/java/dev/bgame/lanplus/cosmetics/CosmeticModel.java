package dev.bgame.lanplus.cosmetics;

import dev.bgame.lanplus.cosmetics.geckolib.cache.object.BakedGeoModel;
import dev.bgame.lanplus.cosmetics.geckolib.loading.object.BakedAnimations;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

public record CosmeticModel(BakedGeoModel geometry, @Nullable BakedAnimations animations, ResourceLocation texture,
                            int colour) {
}
