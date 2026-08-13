package dev.bgame.lanplus.platform;

import java.nio.file.Path;

/**
 * Minimal loader-agnostic platform interface. Each loader sets the singleton instance
 * during its entry point so the common module can resolve configuration and game paths
 * without depending on FMLPaths/FabricLoader.
 */
public interface LanplusPlatform {

    Path getConfigDir();

    Path getGameDir();
    boolean isClient();
}
