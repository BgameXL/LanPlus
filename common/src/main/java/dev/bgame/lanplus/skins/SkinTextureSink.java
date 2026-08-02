package dev.bgame.lanplus.skins;

import java.util.UUID;

/**
 * Receives resolved skin bytes for binding to the renderer. Implemented client-side (the binding to
 * a texture id is a Minecraft concern that must not live in this side-agnostic package).
 */
@FunctionalInterface
public interface SkinTextureSink {

    void accept(UUID player, String key, byte[] png, String model);
}