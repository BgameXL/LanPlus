package dev.bgame.lanplus.api;

import java.util.Objects;

public record Invite(String code, String address, String worldName, int expiresInSeconds) {
    public Invite {
        Objects.requireNonNull(code, "code");
    }
}
