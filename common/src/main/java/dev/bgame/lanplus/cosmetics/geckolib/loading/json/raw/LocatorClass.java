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
import org.jetbrains.annotations.Nullable;
import dev.bgame.lanplus.cosmetics.geckolib.util.JsonUtil;

/**
 * Container class for locator class information, only used in deserialization at startup
 */
public record LocatorClass(@Nullable Boolean ignoreInheritedScale, double[] offset, double[] rotation) {
	public static JsonDeserializer<LocatorClass> deserializer() throws JsonParseException {
		return (json, type, context) -> {
			JsonObject obj = json.getAsJsonObject();
			Boolean ignoreInheritedScale = JsonUtil.getOptionalBoolean(obj, "ignore_inherited_scale");
			double[] offset = JsonUtil.jsonArrayToDoubleArray(GsonHelper.getAsJsonArray(obj, "offset", null));
			double[] rotation = JsonUtil.jsonArrayToDoubleArray(GsonHelper.getAsJsonArray(obj, "rotation", null));

			return new LocatorClass(ignoreInheritedScale, offset, rotation);
		};
	}
}
