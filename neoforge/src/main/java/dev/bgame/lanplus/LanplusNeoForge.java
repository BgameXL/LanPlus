package dev.bgame.lanplus;

import dev.bgame.lanplus.platform.LanplusPlatform;
import dev.bgame.lanplus.platform.PlatformHolder;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

@Mod(LanplusCommon.MODID)
public class LanplusNeoForge {

    public LanplusNeoForge(IEventBus modEventBus) {
        PlatformHolder.set(new NeoForgePlatform());
        LanplusCommon.init();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            LanplusNeoForgeClient.init(modEventBus);
        }
    }

    private static final class NeoForgePlatform implements LanplusPlatform {

        @Override
        public Path getConfigDir() {
            return FMLPaths.CONFIGDIR.get();
        }

        @Override
        public Path getGameDir() {
            return FMLPaths.GAMEDIR.get();
        }

        @Override
        public boolean isClient() {
            return FMLEnvironment.dist == Dist.CLIENT;
        }
    }
}
