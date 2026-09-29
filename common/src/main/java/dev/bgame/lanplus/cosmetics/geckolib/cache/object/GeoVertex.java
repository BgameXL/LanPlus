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

package dev.bgame.lanplus.cosmetics.geckolib.cache.object;

import org.joml.Vector3f;

/**
 * Vertex data holder
 *
 * @param position The position of the vertex
 * @param texU The texture U coordinate
 * @param texV The texture V coordinate
 */
public record GeoVertex(Vector3f position, float texU, float texV) {
	public GeoVertex(double x, double y, double z) {
		this(new Vector3f((float)x, (float)y, (float)z), 0, 0);
	}

	public GeoVertex withUVs(float texU, float texV) {
		return new GeoVertex(this.position, texU, texV);
	}
}