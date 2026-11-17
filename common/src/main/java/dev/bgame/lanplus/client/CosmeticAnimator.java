package dev.bgame.lanplus.client;

import dev.bgame.lanplus.LanplusCommon;
import dev.bgame.lanplus.cosmetics.CosmeticModel;
import dev.bgame.lanplus.cosmetics.geckolib.animatable.GeoAnimatable;
import dev.bgame.lanplus.cosmetics.geckolib.animatable.instance.AnimatableInstanceCache;
import dev.bgame.lanplus.cosmetics.geckolib.animation.AnimatableManager;
import dev.bgame.lanplus.cosmetics.geckolib.animation.Animation;
import dev.bgame.lanplus.cosmetics.geckolib.animation.AnimationController;
import dev.bgame.lanplus.cosmetics.geckolib.animation.AnimationState;
import dev.bgame.lanplus.cosmetics.geckolib.animation.RawAnimation;
import dev.bgame.lanplus.cosmetics.geckolib.model.GeoModel;
import dev.bgame.lanplus.cosmetics.geckolib.util.GeckoLibUtil;
import dev.bgame.lanplus.cosmetics.geckolib.util.RenderUtil;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

final class CosmeticAnimator {
    private static final ResourceLocation DYNAMIC_RESOURCE =
            ResourceLocation.fromNamespaceAndPath(LanplusCommon.MODID, "cosmetics/dynamic");

    private final DynamicAnimatable animatable;
    private final DynamicModel model;

    private CosmeticAnimator(CosmeticModel cosmetic) {
        this.animatable = new DynamicAnimatable(cosmetic.animations().animations().keySet());
        this.model = new DynamicModel(cosmetic);
        this.model.getAnimationProcessor().setActiveModel(cosmetic.geometry());
    }

    @Nullable
    static CosmeticAnimator create(CosmeticModel cosmetic) {
        if (cosmetic.animations() == null || cosmetic.animations().animations().isEmpty()) {
            return null;
        }
        return new CosmeticAnimator(cosmetic);
    }

    void apply(long instanceId, float limbSwing, float limbSwingAmount, float partialTick,
               String requestedClip, String defaultClip) {
        this.animatable.selectAnimation(requestedClip, defaultClip);
        AnimationState<DynamicAnimatable> state = new AnimationState<>(this.animatable, limbSwing, limbSwingAmount,
                partialTick, limbSwingAmount > 0.01f);
        this.model.handleAnimations(this.animatable, instanceId, state, partialTick);
    }

    private static final class DynamicAnimatable implements GeoAnimatable {
        private final Map<String, RawAnimation> animations = new HashMap<>();
        private final RawAnimation fallbackAnimation;
        private final AnimatableInstanceCache cache;
        private RawAnimation currentAnimation;

        private DynamicAnimatable(Iterable<String> animationNames) {
            RawAnimation first = null;
            RawAnimation idle = null;
            for (String name : animationNames) {
                RawAnimation animation = RawAnimation.begin().thenLoop(name);
                this.animations.put(name, animation);
                if (first == null) {
                    first = animation;
                }
                if (name.endsWith(".idle")) {
                    idle = animation;
                }
            }
            this.fallbackAnimation = idle != null ? idle : first;
            this.currentAnimation = this.fallbackAnimation;
            this.cache = GeckoLibUtil.createInstanceCache(this);
        }

        private void selectAnimation(String requestedClip, String defaultClip) {
            RawAnimation animation = this.animations.get(requestedClip);
            if (animation == null) {
                animation = this.animations.get(defaultClip);
            }
            this.currentAnimation = animation != null ? animation : this.fallbackAnimation;
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "cosmetic", 3,
                    state -> state.setAndContinue(this.currentAnimation)));
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
