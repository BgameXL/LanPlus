package dev.bgame.lanplus.api;

public record LibrarySkin(String id, String url, String model, boolean active) {

    public boolean slim() {
        return "slim".equalsIgnoreCase(model);
    }
}
