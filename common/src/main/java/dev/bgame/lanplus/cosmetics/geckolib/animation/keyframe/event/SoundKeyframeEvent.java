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

package dev.bgame.lanplus.cosmetics.geckolib.animation.keyframe.event;

import dev.bgame.lanplus.cosmetics.geckolib.animatable.GeoAnimatable;
import dev.bgame.lanplus.cosmetics.geckolib.animation.AnimationController;
import dev.bgame.lanplus.cosmetics.geckolib.animation.keyframe.event.data.SoundKeyframeData;

/**
 * The {@link KeyFrameEvent} specific to the {@link AnimationController#soundKeyframeHandler}
 * <p>
 * Called when a sound instruction keyframe is encountered
 */
public class SoundKeyframeEvent<T extends GeoAnimatable> extends KeyFrameEvent<T, SoundKeyframeData> {
	public SoundKeyframeEvent(T entity, double animationTick, AnimationController<T> controller, SoundKeyframeData keyFrameData) {
		super(entity, animationTick, controller, keyFrameData);
	}

	@Override
	public SoundKeyframeData getKeyframeData() {
		return super.getKeyframeData();
	}
}
