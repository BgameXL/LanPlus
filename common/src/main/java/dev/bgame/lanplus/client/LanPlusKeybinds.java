package dev.bgame.lanplus.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bgame.lanplus.LanplusCommon;
import dev.bgame.lanplus.client.gui.FriendsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class LanPlusKeybinds {

    public static final String CATEGORY = "key.categories." + LanplusCommon.MODID;

    public static final KeyMapping OPEN_FRIENDS = new KeyMapping(
            "key." + LanplusCommon.MODID + ".friends",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY);

    private LanPlusKeybinds() {}

    public static void onClientTick() {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_FRIENDS.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new FriendsScreen(null));
            }
        }
    }
}
