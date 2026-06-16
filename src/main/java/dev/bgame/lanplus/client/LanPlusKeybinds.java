package dev.bgame.lanplus.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bgame.lanplus.Lanplus;
import dev.bgame.lanplus.client.gui.FriendsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Lanplus.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LanPlusKeybinds {

    public static final String CATEGORY = "key.categories." + Lanplus.MODID;

    public static final KeyMapping OPEN_FRIENDS = new KeyMapping(
            "key." + Lanplus.MODID + ".friends",
            KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY);

    private LanPlusKeybinds() {}

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_FRIENDS);
    }
}

@Mod.EventBusSubscriber(modid = Lanplus.MODID, value = Dist.CLIENT)
final class LanPlusKeyHandler {

    private LanPlusKeyHandler() {}

    @SubscribeEvent
    static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (LanPlusKeybinds.OPEN_FRIENDS.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new FriendsScreen(null));
            }
        }
    }
}
