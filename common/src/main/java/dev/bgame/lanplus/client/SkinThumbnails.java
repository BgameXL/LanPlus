package dev.bgame.lanplus.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.bgame.lanplus.LanplusCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class SkinThumbnails {

    private static final int MAX_BYTES = 64 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final Executor IO = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "lanplus-skin-thumbs");
        t.setDaemon(true);
        return t;
    });
    private static final Map<String, ResourceLocation> ready = new ConcurrentHashMap<>();
    private static final Set<String> pending = ConcurrentHashMap.newKeySet();

    private SkinThumbnails() {
    }

    public static ResourceLocation get(String id, String url) {
        ResourceLocation loc = ready.get(id);
        if (loc != null) {
            return loc;
        }
        if (url != null && !url.isBlank() && pending.add(id)) {
            CompletableFuture.runAsync(() -> {
                boolean registrationScheduled = false;
                try {
                    HttpResponse<InputStream> resp = HTTP.send(
                            HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                            HttpResponse.BodyHandlers.ofInputStream());
                    try (InputStream body = resp.body()) {
                        if (resp.statusCode() == 200) {
                            byte[] png = body.readNBytes(MAX_BYTES + 1);
                            if (png.length <= MAX_BYTES) {
                                Minecraft.getInstance().execute(() -> {
                                    try {
                                        register(id, png);
                                    } finally {
                                        pending.remove(id);
                                    }
                                });
                                registrationScheduled = true;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException | RuntimeException ignored) {
                } finally {
                    if (!registrationScheduled) {
                        pending.remove(id);
                    }
                }
            }, IO);
        }
        return null;
    }

    public static void forget(String id) {
        ResourceLocation loc = ready.remove(id);
        if (loc != null) {
            Minecraft.getInstance().getTextureManager().release(loc);
        }
        pending.remove(id);
    }

    private static void register(String id, byte[] png) {
        try {
            DynamicTexture tex = new DynamicTexture(NativeImage.read(png));
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(LanplusCommon.MODID, "skinlib/" + id);
            Minecraft.getInstance().getTextureManager().register(rl, tex);
            ready.put(id, rl);
        } catch (Exception ignored) {
        }
    }
}
