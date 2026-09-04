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

/*
 * Copyright (c) 2020.
 * Author: Bernie G. (Gecko)
 */

package dev.bgame.lanplus.cosmetics.geckolib.animation.keyframe;

import dev.bgame.lanplus.cosmetics.geckolib.animation.AnimationController;

import java.io.Serial;
import java.util.LinkedList;

/**
 * An {@link AnimationPoint} queue holds a queue of {@code AnimationPoints} which are used in
 * the {@link AnimationController} to lerp between values
 */
public final class AnimationPointQueue extends LinkedList<AnimationPoint> {
	@Serial
	private static final long serialVersionUID = 5472797438476621193L;
}
