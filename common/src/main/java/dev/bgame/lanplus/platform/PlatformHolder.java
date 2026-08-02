package dev.bgame.lanplus.platform;

import java.util.Objects;

public final class PlatformHolder {

    private static LanplusPlatform platform;

    private PlatformHolder() {}

    public static void set(LanplusPlatform instance) {
        platform = Objects.requireNonNull(instance);
    }

    public static LanplusPlatform get() {
        if (platform == null) {
            throw new IllegalStateException("LanplusPlatform has not been initialized by the active loader");
        }
        return platform;
    }
}
