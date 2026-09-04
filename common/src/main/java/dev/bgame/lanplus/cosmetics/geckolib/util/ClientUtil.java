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

package dev.bgame.lanplus.cosmetics.geckolib.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Helper class for segregating client-side code
 */
public final class ClientUtil {
	/**
	 * Get the player on the client
	 */
	public static Player getClientPlayer() {
		return Minecraft.getInstance().player;
	}

	/**
	 * Gets the current level on the client
	 */
	public static Level getLevel() {
		return Minecraft.getInstance().level;
	}
}
