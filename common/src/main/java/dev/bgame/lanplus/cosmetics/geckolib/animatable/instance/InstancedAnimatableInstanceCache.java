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

package dev.bgame.lanplus.cosmetics.geckolib.animatable.instance;

import dev.bgame.lanplus.cosmetics.geckolib.animatable.GeoAnimatable;
import dev.bgame.lanplus.cosmetics.geckolib.animation.AnimatableManager;
import dev.bgame.lanplus.cosmetics.geckolib.util.GeckoLibUtil;

/**
 * AnimatableInstanceCache implementation for instantiated objects such as Entities or BlockEntities. Returns a single {@link AnimatableManager} instance per cache
 * <p>
 * You should <b><u>NOT</u></b> be instantiating this directly unless you know what you are doing.
 * Use {@link GeckoLibUtil#createInstanceCache GeckoLibUtil.createInstanceCache} instead
 */
public class InstancedAnimatableInstanceCache extends AnimatableInstanceCache {
	protected AnimatableManager<?> manager;

	public InstancedAnimatableInstanceCache(GeoAnimatable animatable) {
		super(animatable);
	}

	/**
	 * Gets the {@link AnimatableManager} instance from this cache
	 * <p>
	 * Because this cache subclass expects a 1:1 relationship of cache to animatable, only one {@code AnimatableManager} instance is used
	 */
	@Override
	public AnimatableManager<?> getManagerForId(long uniqueId) {
		if (this.manager == null)
			this.manager = new AnimatableManager<>(this.animatable);

		return this.manager;
	}
}