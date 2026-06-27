package dev.bgame.lanplus.network;

public interface MinecraftAuth {

    String username();

    boolean isPremium();

    void joinServer(String serverId) throws Exception;
}
