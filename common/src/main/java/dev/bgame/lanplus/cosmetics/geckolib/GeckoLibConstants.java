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

package dev.bgame.lanplus.cosmetics.geckolib;

import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Holder class for several properties and/or handlers inherent to GeckoLib
 */
public final class GeckoLibConstants {
    public static final Logger LOGGER = LogManager.getLogger("GeckoLib");
    public static final String MODID = "geckolib";

    public static void init() {}

    /**
     * Helper method to create a ResourceLocation predefined with GeckoLib's {@link #MODID}
     */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(GeckoLibConstants.MODID, path);
    }

    /**
     * Throw an exception pertaining to a specific resource
     * <p>
     * This mostly serves as a helper for consistent formatting of exceptions
     *
     * @param resource The location or id of the resource the error pertains to
     * @param message The error message to display
     */
    public static RuntimeException exception(ResourceLocation resource, String message) {
        return new RuntimeException(resource + ": " + message);
    }

    /**
     * Throw an exception pertaining to a specific resource
     * <p>
     * This mostly serves as a helper for consistent formatting of exceptions
     *
     * @param resource The location or id of the resource the error pertains to
     * @param message The error message to display
     * @param exception The exception to throw
     */
    public static RuntimeException exception(ResourceLocation resource, String message, Throwable exception) {
        return new RuntimeException(resource + ": " + message, exception);
    }
}
