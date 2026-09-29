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

package dev.bgame.lanplus.cosmetics.geckolib.loading.json.raw;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import dev.bgame.lanplus.cosmetics.geckolib.GeckoLibConstants;
import dev.bgame.lanplus.cosmetics.geckolib.util.JsonUtil;

/**
 * Container class for face UV information, only used in deserialization at startup
 */
public record FaceUV(@Nullable String materialInstance, double[] uv, double[] uvSize, Rotation uvRotation) {
	public FaceUV(@Nullable String materialInstance, double[] uv, double[] uvSize) {
		this(materialInstance, uv, uvSize, Rotation.NONE);
	}

	public static JsonDeserializer<FaceUV> deserializer() throws JsonParseException {
		return (json, type, context) -> {
			JsonObject obj = json.getAsJsonObject();
			String materialInstance = GsonHelper.getAsString(obj, "material_instance", null);
			double[] uv = JsonUtil.jsonArrayToDoubleArray(GsonHelper.getAsJsonArray(obj, "uv", null));
			double[] uvSize = JsonUtil.jsonArrayToDoubleArray(GsonHelper.getAsJsonArray(obj, "uv_size", null));
			Rotation uvRotation = Rotation.fromValue(GsonHelper.getAsInt(obj, "uv_rotation", 0));

			return new FaceUV(materialInstance, uv, uvSize, uvRotation);
		};
	}

	public enum Rotation {
		NONE,
		CLOCKWISE_90,
		CLOCKWISE_180,
		CLOCKWISE_270;

		public static Rotation fromValue(int value) throws JsonParseException {
			try {
				return Rotation.values()[(value % 360) / 90];
			}
			catch (Exception e) {
				GeckoLibConstants.LOGGER.error("Invalid Face UV rotation: " + value);

				return fromValue(Mth.floor(Math.abs(value) / 90f) * 90);
			}
		}

		public float[] rotateUvs(float u, float v, float uWidth, float vHeight) {
			return switch (this) {
				case NONE -> new float[] {u, v, uWidth, v, uWidth, vHeight, u, vHeight};
				case CLOCKWISE_90 -> new float[] {uWidth, v, uWidth, vHeight, u, vHeight, u, v};
				case CLOCKWISE_180 -> new float[] {uWidth, vHeight, u, vHeight, u, v, uWidth, v};
				case CLOCKWISE_270 -> new float[] {u, vHeight, u, v, uWidth, v, uWidth, vHeight};
			};
		}
	}
}
