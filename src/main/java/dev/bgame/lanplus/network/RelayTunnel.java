package dev.bgame.lanplus.network;

import dev.bgame.lanplus.api.RelayTicket;

import java.util.concurrent.CompletableFuture;

public interface RelayTunnel {

    CompletableFuture<String> open(int localPort, RelayTicket ticket);

    void close();

    boolean isOpen();
}
