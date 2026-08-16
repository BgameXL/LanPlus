package dev.bgame.lanplus.platform;

import java.nio.file.Path;

public interface LanplusPlatform {

    Path getConfigDir();

    Path getGameDir();
    boolean isClient();
}
