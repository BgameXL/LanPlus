package dev.bgame.lanplus.api;

public record SkinUploadResult(String url, String hash, String error) {

    public boolean success() {
        return error == null;
    }
}
