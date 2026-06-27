package dev.bgame.lanplus.client;

import com.mojang.authlib.GameProfile;
import dev.bgame.lanplus.network.MinecraftAuth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

final class ClientMinecraftAuth implements MinecraftAuth {

    @Override
    public String username() {
        User user = Minecraft.getInstance().getUser();
        return user == null ? null : user.getName();
    }

    @Override
    public boolean isPremium() {
        User user = Minecraft.getInstance().getUser();
        if (user == null) {
            return false;
        }

        String token = user.getAccessToken();
        return user.getType() != User.Type.LEGACY
                && token != null && !token.isBlank() && !"0".equals(token);
    }

    @Override
    public void joinServer(String serverId) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        GameProfile profile = new GameProfile(user.getProfileId(), user.getName());
        mc.getMinecraftSessionService().joinServer(profile, user.getAccessToken(), serverId);
    }
}
