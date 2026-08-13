package dev.bgame.lanplus.skins;

import java.util.UUID;

/**
 * Receives resolved skin bytes for binding to the renderer. Implemented client-side.
 */
@FunctionalInterface
public interface SkinTextureSink {

    void accept(UUID player, String key, byte[] png, String model);
}