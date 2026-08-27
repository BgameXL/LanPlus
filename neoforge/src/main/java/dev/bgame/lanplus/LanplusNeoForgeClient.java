package dev.bgame.lanplus;

import dev.bgame.lanplus.client.ClientAdvancementDetector;
import dev.bgame.lanplus.client.ClientPresenceDetector;
import dev.bgame.lanplus.client.HostController;
import dev.bgame.lanplus.client.LanPlusClient;
import dev.bgame.lanplus.client.LanPlusKeybinds;
import dev.bgame.lanplus.client.PauseMenuButtons;
import dev.bgame.lanplus.client.TitleScreenButtons;
import dev.bgame.lanplus.client.gui.LanPlusNotifications;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;

public final class LanplusNeoForgeClient {

    private LanplusNeoForgeClient() {
    }

    static void init(IEventBus modEventBus) {
        modEventBus.addListener(LanplusNeoForgeClient::onClientSetup);
        modEventBus.addListener(LanplusNeoForgeClient::onRegisterKeyMappings);

        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(LanplusNeoForgeClient::onClientTick);
        gameBus.addListener(LanplusNeoForgeClient::onRenderGui);
        gameBus.addListener(LanplusNeoForgeClient::onScreenRender);
        gameBus.addListener(LanplusNeoForgeClient::onScreenClick);
        gameBus.addListener(LanplusNeoForgeClient::onScreenInit);
        gameBus.addListener(LanplusNeoForgeClient::onAdvancementEarn);
        gameBus.addListener(LanplusNeoForgeClient::onLoggingOut);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(LanPlusClient::init);
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(LanPlusKeybinds.OPEN_FRIENDS);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        LanPlusKeybinds.onClientTick();
        ClientPresenceDetector.onClientTick();
        HostController.onClientTick();
    }

    private static void onRenderGui(RenderGuiEvent.Post event) {
        LanPlusNotifications.onRenderGui(event.getGuiGraphics());
    }

    private static void onScreenRender(ScreenEvent.Render.Post event) {
        LanPlusNotifications.onScreenRender(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
    }

    private static void onScreenClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (LanPlusNotifications.onMouseClick(event.getMouseX(), event.getMouseY(), event.getButton())) {
            event.setCanceled(true);
        }
    }

    private static void onScreenInit(ScreenEvent.Init.Post event) {
        TitleScreenButtons.tryAddButtons(event.getScreen());
        PauseMenuButtons.tryAddHostButton(event.getScreen());
    }

    private static void onAdvancementEarn(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getAdvancement() == null) {
            return;
        }
        ClientAdvancementDetector.onAdvancementEarn(
                event.getAdvancement().id().toString(),
                event.getAdvancement().value().display().isPresent());
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPresenceDetector.onLogout();
    }
}
