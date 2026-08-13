package dev.bgame.lanplus.skins;

import dev.bgame.lanplus.api.SkinRef;
import dev.bgame.lanplus.api.SkinUploadResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SkinService {

    CompletableFuture<Void> resolve(UUID player, SkinRef ref);

    CompletableFuture<SkinUploadResult> uploadSkin(byte[] png, boolean slim);

    CompletableFuture<Boolean> deleteSkin();
}
