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

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;
import dev.bgame.lanplus.cosmetics.geckolib.util.JsonUtil;

/**
 * Container class for generic geometry information, only used in deserialization at startup
 */
public record MinecraftGeometry(Bone[] bones, @Nullable String cape, @Nullable ModelProperties modelProperties) {
	public static JsonDeserializer<MinecraftGeometry> deserializer() throws JsonParseException {
		return (json, type, context) -> {
			JsonObject obj = json.getAsJsonObject();
			Bone[] bones = JsonUtil.jsonArrayToObjectArray(GsonHelper.getAsJsonArray(obj, "bones", new JsonArray(0)), context, Bone.class);
			String cape = GsonHelper.getAsString(obj, "cape", null);
			ModelProperties modelProperties = GsonHelper.getAsObject(obj, "description", null, context, ModelProperties.class);

			return new MinecraftGeometry(bones, cape, modelProperties);
		};
	}
}
