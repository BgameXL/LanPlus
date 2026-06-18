package dev.bgame.lanplus.skins;

import dev.bgame.lanplus.api.SkinRef;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Resolves {@link SkinRef}s into textures client-side and caches them.
 *
 * The backend only ever carries the reference;
 * this service downloads the actual texture from the provider or
 * via P2P fallback on LAN, and caches it locally keyed by hash.
 *
 * This interface stays side-agnostic on purpose (no Minecraft types): binding a resolved
 * texture to the renderer is a client-only concern that lives in {@code client/}.
 */
public interface SkinService {

    /** The local player's current skin reference, or {@code null} if unknown. */
    SkinRef localSkin();

    void setLocalSkin(SkinRef ref);

    /**
     * Ensure a player's skin reference is resolved and cached, downloading as needed.
     * Implementations must not block gameplay.
     */
    CompletableFuture<Void> resolve(UUID player, SkinRef ref);
}
