package dev.bgame.lanplus.api;

public record PlayedTogether(int sessions, long lastAt) {

    public boolean hasSessions() {
        return sessions > 0;
    }
    public boolean hasLastAt() {
        return lastAt > 0;
    }
}
