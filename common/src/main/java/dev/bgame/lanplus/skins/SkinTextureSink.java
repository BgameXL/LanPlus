package dev.bgame.lanplus.skins;

import java.util.UUID;

@FunctionalInterface
public interface SkinTextureSink {

    void accept(UUID player, String key, byte[] png, String model);
}
