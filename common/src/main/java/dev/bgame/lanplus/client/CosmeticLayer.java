package dev.bgame.lanplus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bgame.lanplus.cosmetics.CosmeticModel;
import dev.bgame.lanplus.cosmetics.CosmeticSlot;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.util.Map;

public final class CosmeticLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public CosmeticLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        CosmeticModels models = LanPlusClient.cosmetics();
        if (models == null) {
            return;
        }
        Map<CosmeticSlot, String> equipped = models.loadout(player.getUUID());
        if (equipped == null || equipped.isEmpty()) {
            return;
        }
        for (Map.Entry<CosmeticSlot, String> entry : equipped.entrySet()) {
            CosmeticModel model = models.model(entry.getValue());
            if (model == null) {
                continue;
            }
            poseStack.pushPose();
            CosmeticGeoRender.anchor(getParentModel(), entry.getKey(), poseStack);
            models.animate(entry.getValue(), player.getUUID(), limbSwing, limbSwingAmount, partialTick);
            VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(model.texture()));
            CosmeticGeoRender.render(poseStack, model.geometry(), buffer, packedLight, OverlayTexture.NO_OVERLAY,
                    model.colour());
            poseStack.popPose();
        }
    }
}
