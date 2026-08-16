package dev.bgame.lanplus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

public final class JoinHelper {

    private JoinHelper() {
    }

    public static void connect(Minecraft mc, String address) {
        if (address == null || address.isBlank()) {
            return;
        }
        ServerData serverData = new ServerData("LAN+", address, false);
        ServerAddress parsed = ServerAddress.parseString(address);
        if (mc.level != null) {
            boolean local = mc.isLocalServer();
            mc.level.disconnect();
            if (local) {
                mc.clearLevel(new GenericDirtMessageScreen(Component.translatable("menu.savingLevel")));
            } else {
                mc.clearLevel();
            }
        }
        ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, parsed, serverData, false);
    }
}
