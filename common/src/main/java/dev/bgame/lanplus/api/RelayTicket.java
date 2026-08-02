package dev.bgame.lanplus.api;

public record RelayTicket(String ticket, String relayHost, int relayPort, String domain, int expiresInSeconds) {
}
