package dev.bgame.lanplus.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.bgame.lanplus.client.CosmeticGeoRender;
import dev.bgame.lanplus.client.CosmeticModels;
import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.cosmetics.CosmeticModel;
import dev.bgame.lanplus.cosmetics.CosmeticSlot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.UUID;

final class PlayerPreview {

    private static PlayerModel<LivingEntity> wideModel;
    private static PlayerModel<LivingEntity> slimModel;

    private PlayerPreview() {
    }

    static PlayerModel<LivingEntity> model(boolean slim) {
        var models = Minecraft.getInstance().getEntityModels();
        if (slim) {
            if (slimModel == null) {
                slimModel = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER_SLIM), true);
            }
            return slimModel;
        }
        if (wideModel == null) {
            wideModel = new PlayerModel<>(models.bakeLayer(ModelLayers.PLAYER), false);
        }
        return wideModel;
    }

    // fucking player model
    static void render(GuiGraphics g, int cx, int feetY, float scale, float yaw, float pitch,
                       ResourceLocation skin, boolean slim, UUID uuid) {
        PlayerModel<LivingEntity> model = model(slim);
        model.setAllVisible(false); // true

        // for test
        model.body.visible = true;
        model.rightArm.visible = true;
        model.leftArm.visible = true;
        //

        model.young = false;
        model.crouching = false;
        model.attackTime = 0f;
        model.riding = false;

        g.flush();

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, feetY, 200.0);
        ps.scale(scale, scale, scale); // matrix4f at negative scale
        ps.mulPose(Axis.ZP.rotationDegrees(180f));
        ps.mulPose(Axis.XP.rotationDegrees(pitch));
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        Lighting.setupForEntityInInventory();
        ps.scale(-1f, -1f, 1f);
        ps.scale(0.9375f, 0.9375f, 0.9375f);
        ps.translate(0f, -1.501f, 0f);
        MultiBufferSource.BufferSource buffers = g.bufferSource();

        RenderSystem.enableDepthTest();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(skin));
        model.renderToBuffer(ps, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

        if (!slim) {
            model.rightArm.x = -6.0F;
            model.leftArm.x = 6.0F;
        }

        renderCosmetics(ps, buffers, model, uuid);
        buffers.endBatch();
        Lighting.setupFor3DItems();
        ps.popPose();
    }

    private static void renderCosmetics(PoseStack ps, MultiBufferSource.BufferSource buffers,
                                        PlayerModel<LivingEntity> model, UUID uuid) {
        CosmeticModels cm = LanPlusClient.cosmetics();
        if (cm == null || uuid == null) {
            return;
        }
        Map<CosmeticSlot, String> loadout = cm.loadout(uuid);
        if (loadout == null || loadout.isEmpty()) {
            return;
        }
        for (Map.Entry<CosmeticSlot, String> e : loadout.entrySet()) {
            CosmeticModel cosmetic = cm.model(e.getValue());
            if (cosmetic == null) {
                continue;
            }
            ps.pushPose();
            anchor(model, e.getKey(), ps);
            cm.animate(e.getValue(), uuid, 0f, 0f, 0f);
            VertexConsumer cvc = buffers.getBuffer(RenderType.entityCutoutNoCull(cosmetic.texture()));
            CosmeticGeoRender.render(ps, cosmetic.geometry(), cvc, 0xF000F0, OverlayTexture.NO_OVERLAY, cosmetic.colour());
            ps.popPose();
        }
    }

    private static void anchor(PlayerModel<LivingEntity> model, CosmeticSlot slot, PoseStack ps) {
        ModelPart part = switch (slot) {
            case HEAD, FACE -> model.head;
            case MAIN_HAND -> model.rightArm;
            case OFF_HAND -> model.leftArm;
            case BODY, BACK, WAIST, LEGS -> model.body;
        };
        part.translateAndRotate(ps);
        ps.scale(-1f, -1f, 1f);
    }
}
