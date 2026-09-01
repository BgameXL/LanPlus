package dev.bgame.lanplus.client;

import dev.bgame.lanplus.LanplusCommon;
import dev.bgame.lanplus.cosmetics.CosmeticModel;
import dev.bgame.lanplus.cosmetics.vendored.geckolib.animatable.GeoAnimatable;
import dev.bgame.lanplus.cosmetics.vendored.geckolib.animatable.instance.AnimatableInstanceCache;
import dev.bgame.lanplus.cosmetics.vendored.geckolib.animation.AnimatableManager;
import dev.bgame.lanplus.cosmetics.vendored.geckolib.animation.Animation;
import dev.bgame.lanplus.cosmetics.vendored.geckolib.animation.AnimationController;
import dev.bgame.lanplus.cosmetics.vendored.geckolib.animation.AnimationState;
import dev.bgame.lanplus.cosmetics.vendored.geckolib.animation.RawAnimation;
import dev.bgame.lanplus.cosmetics.vendored.geckolib.model.GeoModel;
import dev.bgame.lanplus.cosmetics.vendored.geckolib.util.GeckoLibUtil;
import dev.bgame.lanplus.cosmetics.vendored.geckolib.util.RenderUtil;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

final class CosmeticAnimator {
    private static final ResourceLocation DYNAMIC_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(LanplusCommon.MODID, "cosmetics/dynamic");

    private final DynamicAnimatable animatable;
    private final DynamicModel model;

    private CosmeticAnimator(CosmeticModel cosmetic, String animationName) {
        this.animatable = new DynamicAnimatable(animationName);
        this.model = new DynamicModel(cosmetic);
        this.model.getAnimationProcessor().setActiveModel(cosmetic.geometry());
    }

    @Nullable
    static CosmeticAnimator create(CosmeticModel cosmetic) {
        if (cosmetic.animations() == null || cosmetic.animations().animations().isEmpty()) {
            return null;
        }
        String animationName = cosmetic.animations().animations().keySet().iterator().next();
        return new CosmeticAnimator(cosmetic, animationName);
    }

    void apply(long instanceId, float limbSwing, float limbSwingAmount, float partialTick) {
        AnimationState<DynamicAnimatable> state = new AnimationState<>(this.animatable, limbSwing, limbSwingAmount,
                partialTick, limbSwingAmount > 0.01f);
        this.model.handleAnimations(this.animatable, instanceId, state, partialTick);
    }

    private static final class DynamicAnimatable implements GeoAnimatable {
        private final RawAnimation idleAnimation;
        private final AnimatableInstanceCache cache;

        private DynamicAnimatable(String animationName) {
            this.idleAnimation = RawAnimation.begin().thenLoop(animationName);
            this.cache = GeckoLibUtil.createInstanceCache(this);
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "cosmetic_idle", 0,
                    state -> state.setAndContinue(this.idleAnimation)));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return this.cache;
        }

        @Override
        public double getTick(Object object) {
            return RenderUtil.getCurrentTick();
        }
    }

    private static final class DynamicModel extends GeoModel<DynamicAnimatable> {
        private final CosmeticModel cosmetic;

        private DynamicModel(CosmeticModel cosmetic) {
            this.cosmetic = cosmetic;
        }

        @Override
        public ResourceLocation getModelResource(DynamicAnimatable animatable) {
            return DYNAMIC_RESOURCE;
        }

        @Override
        public ResourceLocation getTextureResource(DynamicAnimatable animatable) {
            return this.cosmetic.texture();
        }

        @Override
        public ResourceLocation getAnimationResource(DynamicAnimatable animatable) {
            return DYNAMIC_RESOURCE;
        }

        @Override
        @Nullable
        public Animation getAnimation(DynamicAnimatable animatable, String name) {
            return this.cosmetic.animations() == null ? null : this.cosmetic.animations().getAnimation(name);
        }
    }
}
