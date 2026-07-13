package dev.bgame.lanplus.skins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bgame.lanplus.api.SkinRef;
import dev.bgame.lanplus.api.SkinUploadResult;
import dev.bgame.lanplus.core.AssetCache;
import dev.bgame.lanplus.network.LanPlusNetwork;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.net.http.HttpClient;

/**
 * Resolves skin references into PNG bytes off the game thread and hands them to a {@link
 * SkinTextureSink} for client-side binding. MOJANG refs are resolved via the session server (which
 * also carries the slim/classic model); CUSTOM refs are fetched directly behind {@link SkinUrlGuard}.
 * Bytes are cached by key (hash or URL) in the shared {@link AssetCache} (memory + disk), so a
 * shared URL or a reconnecting player is not re-downloaded, across sessions too.
 */
public final class DefaultSkinService implements SkinService {

    private static final String PROFILE_API = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private final SkinTextureSink sink;
    private final LanPlusNetwork network;
    private final AssetCache cache;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Executor executor = Executors.newFixedThreadPool(2, daemon());

    public DefaultSkinService(SkinTextureSink sink, LanPlusNetwork network, AssetCache cache) {
        this.sink = sink;
        this.network = network;
        this.cache = cache;
    }

    @Override
    public CompletableFuture<SkinUploadResult> uploadSkin(byte[] png, boolean slim) {
        if (network == null) {
            return CompletableFuture.completedFuture(new SkinUploadResult(null, null, "offline"));
        }
        return network.uploadSkin(png, slim ? "slim" : null);
    }

    @Override
    public CompletableFuture<Boolean> deleteSkin() {
        return network == null ? CompletableFuture.completedFuture(false) : network.deleteSkin();
    }

    @Override
    public CompletableFuture<Void> resolve(UUID player, SkinRef ref) {
        if (player == null || ref == null) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> {
            Loaded loaded = load(ref);
            if (loaded != null) {
                sink.accept(player, loaded.key, loaded.png, loaded.model);
            }
        }, executor).exceptionally(e -> null);
    }

    private Loaded load(SkinRef ref) {
        return switch (ref.type()) {
            case CUSTOM -> {
                String url = ref.id();
                String key = ref.hash() != null ? ref.hash() : url;
                byte[] png = download(key, url);
                yield png == null ? null : new Loaded(key, png, ref.slim() ? "slim" : null);
            }
            case MOJANG -> loadMojang(ref);
            default -> null;
        };
    }

    private Loaded loadMojang(SkinRef ref) {
        byte[] body = SkinUrlGuard.fetch(http, PROFILE_API + ref.id().replace("-", ""));
        if (body == null) {
            return null;
        }
        try {
            JsonObject profile = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray props = profile.getAsJsonArray("properties");
            if (props == null) {
                return null;
            }
            String texturesB64 = null;
            for (JsonElement el : props) {
                JsonObject p = el.getAsJsonObject();
                if ("textures".equals(p.get("name").getAsString())) {
                    texturesB64 = p.get("value").getAsString();
                    break;
                }
            }
            if (texturesB64 == null) {
                return null;
            }
            JsonObject textures = JsonParser.parseString(
                    new String(Base64.getDecoder().decode(texturesB64), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("textures");
            if (textures == null) {
                return null;
            }
            JsonObject skin = textures.getAsJsonObject("SKIN");
            if (skin == null) {
                return null;
            }
            String url = skin.get("url").getAsString().replaceFirst("^http://", "https://");
            String model = null;
            if (skin.has("metadata")) {
                JsonElement m = skin.getAsJsonObject("metadata").get("model");
                if (m != null) {
                    model = m.getAsString();
                }
            }
            String key = ref.hash() != null ? ref.hash() : url;
            byte[] png = download(key, url);
            return png == null ? null : new Loaded(key, png, model);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private byte[] download(String key, String url) {
        byte[] cached = cache == null ? null : cache.get(key);
        if (cached != null) {
            return cached;
        }
        byte[] png = SkinUrlGuard.fetch(http, url);
        if (png != null && cache != null) {
            cache.put(key, png);
        }
        return png;
    }

    private static ThreadFactory daemon() {
        return r -> {
            Thread t = new Thread(r, "lanplus-skins");
            t.setDaemon(true);
            return t;
        };
    }

    private record Loaded(String key, byte[] png, String model) {}
}