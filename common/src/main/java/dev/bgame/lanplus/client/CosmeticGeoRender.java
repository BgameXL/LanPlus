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

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bgame.lanplus.cosmetics.vendored.geckolib.cache.object.BakedGeoModel;
import dev.bgame.lanplus.cosmetics.vendored.geckolib.cache.object.GeoBone;
import dev.bgame.lanplus.cosmetics.vendored.geckolib.cache.object.GeoCube;
import dev.bgame.lanplus.cosmetics.vendored.geckolib.cache.object.GeoQuad;
import dev.bgame.lanplus.cosmetics.vendored.geckolib.cache.object.GeoVertex;
import dev.bgame.lanplus.cosmetics.vendored.geckolib.util.RenderUtil;
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
