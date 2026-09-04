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

package dev.bgame.lanplus.cosmetics.geckolib.loading.json;

import com.google.gson.annotations.SerializedName;

/**
 * Geo format version enum, mostly just used in deserialization at startup
 */
public enum FormatVersion {
	@SerializedName("1.12.0") V_1_12_0,
	@SerializedName("1.14.0") V_1_14_0,
	@SerializedName("1.21.0") V_1_21_0
}
