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

public record GeoFace(GeoVertex[] vertices, Vector3f[] normals) {
    public GeoFace {
        if (vertices.length < 3 || vertices.length > 4) {
            throw new IllegalArgumentException("Mesh faces must contain three or four vertices");
        }
        if (normals.length != vertices.length) {
            throw new IllegalArgumentException("Mesh face normals must match its vertices");
        }
    }
}
