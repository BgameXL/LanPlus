/*
 * This file contains code derived from GeckoLib.
 *
 * Original project:
 * https://github.com/bernie-g/geckolib
 *
 * Copyright (c) GeckoLib contributors
 * Licensed under the MIT License.
 *
 * Modifications and additional code are Copyright (c) 2026 Bgame (LAN+)
 * and are licensed under the GNU LGPL v3.0.
 *
 * The original MIT License is preserved in the project's third-party licenses.
 */

package dev.bgame.lanplus.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.bgame.lanplus.cosmetics.CosmeticModel;
import dev.bgame.lanplus.cosmetics.geckolib.cache.object.BakedGeoModel;
import dev.bgame.lanplus.cosmetics.geckolib.cache.object.GeoBone;
import dev.bgame.lanplus.cosmetics.geckolib.cache.object.GeoCube;
import dev.bgame.lanplus.cosmetics.geckolib.cache.object.GeoQuad;
import dev.bgame.lanplus.cosmetics.geckolib.cache.object.GeoVertex;
import dev.bgame.lanplus.cosmetics.geckolib.util.RenderUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class CosmeticGeoRender {

    private CosmeticGeoRender() {
    }

    public static void render(PoseStack poseStack, BakedGeoModel model, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        for (GeoBone bone : model.topLevelBones()) {
            renderBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
        }
    }

    public static void renderThumb(GuiGraphics g, CosmeticModel model, float[] b, int cx, int cy, float box, float spinDeg) {
        float span = Math.max(b[3] - b[0], Math.max(b[4] - b[1], b[5] - b[2]));
        if (span <= 0) {
            span = 16f;
        }
        float s = box * 0.72f / span;
        float mx = (b[0] + b[3]) / 2f;
        float my = (b[1] + b[4]) / 2f;
        float mz = (b[2] + b[5]) / 2f;
        g.flush();
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx, cy, 50.0);
        ps.scale(s, -s, s);
        ps.mulPose(Axis.XP.rotationDegrees(15f));
        ps.mulPose(Axis.YP.rotationDegrees(spinDeg));
        ps.translate(-mx, -my, -mz);
        Lighting.setupForEntityInInventory();
        RenderSystem.enableDepthTest();
        MultiBufferSource.BufferSource buffers = g.bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(model.texture()));
        render(ps, model.geometry(), vc, 0xF000F0, OverlayTexture.NO_OVERLAY, model.colour());
        buffers.endBatch();
        Lighting.setupFor3DItems();
        ps.popPose();
    }

    public static float[] bounds(BakedGeoModel model) {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        PoseStack ps = new PoseStack();
        for (GeoBone bone : model.topLevelBones()) {
            boundsBone(ps, bone, b);
        }
        if (b[0] > b[3]) {
            return new float[]{-1, -1, -1, 1, 1, 1};
        }
        return b;
    }

    private static void boundsBone(PoseStack ps, GeoBone bone, float[] b) {
        ps.pushPose();
        RenderUtil.prepMatrixForBone(ps, bone);
        for (GeoCube cube : bone.getCubes()) {
            boundsCube(ps, cube, b);
        }
        for (GeoBone child : bone.getChildBones()) {
            boundsBone(ps, child, b);
        }
        ps.popPose();
    }

    private static void boundsCube(PoseStack ps, GeoCube cube, float[] b) {
        ps.pushPose();
        RenderUtil.translateToPivotPoint(ps, cube);
        RenderUtil.rotateMatrixAroundCube(ps, cube);
        RenderUtil.translateAwayFromPivotPoint(ps, cube);
        Matrix4f pose = ps.last().pose();
        for (GeoQuad quad : cube.quads()) {
            if (quad == null) {
                continue;
            }
            for (GeoVertex vertex : quad.vertices()) {
                Vector3f p = vertex.position();
                Vector4f w = pose.transform(new Vector4f(p.x(), p.y(), p.z(), 1.0f));
                b[0] = Math.min(b[0], w.x());
                b[1] = Math.min(b[1], w.y());
                b[2] = Math.min(b[2], w.z());
                b[3] = Math.max(b[3], w.x());
                b[4] = Math.max(b[4], w.y());
                b[5] = Math.max(b[5], w.z());
            }
        }
        ps.popPose();
    }

    private static void renderBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        poseStack.pushPose();
        RenderUtil.prepMatrixForBone(poseStack, bone);

        for (GeoCube cube : bone.getCubes()) {
            renderCube(poseStack, cube, buffer, packedLight, packedOverlay, colour);
        }
        for (GeoBone child : bone.getChildBones()) {
            renderBone(poseStack, child, buffer, packedLight, packedOverlay, colour);
        }
        poseStack.popPose();
    }

    private static void renderCube(PoseStack poseStack, GeoCube cube, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        RenderUtil.translateToPivotPoint(poseStack, cube);
        RenderUtil.rotateMatrixAroundCube(poseStack, cube);
        RenderUtil.translateAwayFromPivotPoint(poseStack, cube);

        Matrix3f normalState = poseStack.last().normal();
        Matrix4f poseState = new Matrix4f(poseStack.last().pose());

        for (GeoQuad quad : cube.quads()) {
            if (quad == null) {
                continue;
            }
            Vector3f normal = normalState.transform(new Vector3f(quad.normal()));
            RenderUtil.fixInvertedFlatCube(cube, normal);
            for (GeoVertex vertex : quad.vertices()) {
                Vector3f position = vertex.position();
                Vector4f worldPos = poseState.transform(new Vector4f(position.x(), position.y(), position.z(), 1.0f));

                buffer.addVertex(worldPos.x(), worldPos.y(), worldPos.z(), colour, vertex.texU(), vertex.texV(),
                        packedOverlay, packedLight, normal.x(), normal.y(), normal.z());
            }
        }
    }
}
