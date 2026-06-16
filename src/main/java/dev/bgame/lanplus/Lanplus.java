package dev.bgame.lanplus;

import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.client.LanPlusClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

@Mod(Lanplus.MODID)
public class Lanplus {

    public static final String MODID = "lanplus";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Lanplus() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        LOGGER.debug("LAN+ mod constructed");
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(LanPlusClient::init);
        }
    }
}
