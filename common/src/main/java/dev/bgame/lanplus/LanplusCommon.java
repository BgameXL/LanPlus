package dev.bgame.lanplus;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Loader-agnostic mod bootstrap. Each loader entry point calls {@link #init()} once
 * after providing a {@link dev.bgame.lanplus.platform.LanplusPlatform} implementation.
 */
public final class LanplusCommon {

    public static final String MODID = "lanplus";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean initialized;

    private LanplusCommon() {}

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        Config.load();

        LOGGER.info("LAN+ common initialized");
    }
}
