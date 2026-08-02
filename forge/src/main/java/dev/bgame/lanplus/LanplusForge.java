package dev.bgame.lanplus;

import com.mojang.logging.LogUtils;
import dev.bgame.lanplus.client.ClientAdvancementDetector;
import dev.bgame.lanplus.client.ClientPresenceDetector;
import dev.bgame.lanplus.client.HostController;
import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.client.LanPlusKeybinds;
import dev.bgame.lanplus.client.TitleScreenButtons;
import dev.bgame.lanplus.client.gui.LanPlusNotifications;
import dev.bgame.lanplus.platform.LanplusPlatform;
import dev.bgame.lanplus.platform.PlatformHolder;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Path;

@Mod(LanplusCommon.MODID)
public class LanplusForge {

    private static final Logger LOGGER = LogUtils.getLogger();

    public LanplusForge() {
        PlatformHolder.set(new ForgePlatform());
        LanplusCommon.init();
        LOGGER.debug("LAN+ Forge mod constructed");
    }

    @Mod.EventBusSubscriber(modid = LanplusCommon.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(LanPlusClient::init);
        }

        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(LanPlusKeybinds.OPEN_FRIENDS);
        }
    }

    @Mod.EventBusSubscriber(modid = LanplusCommon.MODID, value = Dist.CLIENT)
    public static class ClientEvents {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                LanPlusKeybinds.onClientTick();
                ClientPresenceDetector.onClientTick();
                HostController.onClientTick();
            }
        }

        @SubscribeEvent
        public static void onRenderGui(RenderGuiEvent.Post event) {
            LanPlusNotifications.onRenderGui(event.getGuiGraphics());
        }

        @SubscribeEvent
        public static void onScreenRender(ScreenEvent.Render.Post event) {
            LanPlusNotifications.onScreenRender(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }

        @SubscribeEvent
        public static void onScreenClick(ScreenEvent.MouseButtonPressed.Pre event) {
            if (LanPlusNotifications.onMouseClick(event.getMouseX(), event.getMouseY(), event.getButton())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onScreenInit(ScreenEvent.Init.Post event) {
            TitleScreenButtons.tryAddButtons(event.getScreen());
        }

        @SubscribeEvent
        public static void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
            if (event.getAdvancement() == null) {
                return;
            }
            ClientAdvancementDetector.onAdvancementEarn(
                    event.getAdvancement().getId().toString(),
                    event.getAdvancement().getDisplay() != null);
        }

        @SubscribeEvent
        public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            ClientPresenceDetector.onLogout();
        }
    }

    private static final class ForgePlatform implements LanplusPlatform {

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
            return true; // Forge client entry point is only constructed on the client side.
        }
    }
}
