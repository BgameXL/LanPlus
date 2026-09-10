package dev.bgame.lanplus.skins;

import dev.bgame.lanplus.api.LibrarySkin;
import dev.bgame.lanplus.api.SkinRef;
import dev.bgame.lanplus.api.SkinUploadResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface SkinService {

    CompletableFuture<Void> resolve(UUID player, SkinRef ref);

    CompletableFuture<List<LibrarySkin>> library();

    CompletableFuture<SkinUploadResult> addSkin(byte[] png, boolean slim);

    CompletableFuture<SkinUploadResult> selectSkin(String skinId);

    CompletableFuture<Boolean> deleteLibrarySkin(String skinId);
}
