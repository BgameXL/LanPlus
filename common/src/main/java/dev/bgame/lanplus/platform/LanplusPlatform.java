package dev.bgame.lanplus.platform;

import java.nio.file.Path;

/**
 * Minimal loader-agnostic platform interface. Each loader sets the singleton instance
 * during its entry point so the common module can resolve configuration and game paths
 * without depending on FMLPaths/FabricLoader.
 */
public interface LanplusPlatform {

    /** @return directory where mod configuration files should be stored. */
    Path getConfigDir();

    /** @return root game directory (e.g. .minecraft). */
    Path getGameDir();

    /** @return true when running on the physical client. */
    boolean isClient();
}
