package dev.bgame.lanplus;

import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.platform.LanplusPlatform;
import dev.bgame.lanplus.platform.PlatformHolder;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

import java.nio.file.Path;

public class LanplusFabric implements ModInitializer {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        PlatformHolder.set(new FabricPlatform());
        LanplusCommon.init();
        LOGGER.info("LAN+ Fabric initialized");
    }

    private static final class FabricPlatform implements LanplusPlatform {

        @Override
        public Path getConfigDir() {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir();
        }

        @Override
        public Path getGameDir() {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
        }

        @Override
        public boolean isClient() {
            return net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType()
                    == net.fabricmc.api.EnvType.CLIENT;
        }
    }
}
