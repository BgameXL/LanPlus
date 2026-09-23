package dev.bgame.lanplus.client;

import dev.bgame.lanplus.api.CosmeticCatalogEntry;
import dev.bgame.lanplus.core.AssetCache;
import dev.bgame.lanplus.cosmetics.CosmeticMeta;
import dev.bgame.lanplus.network.LanPlusNetwork;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class CosmeticAssetLoader {

    private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(15);

    private final LanPlusNetwork network;
    private final CosmeticModels models;
    private final AssetCache cache;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Executor executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lanplus-cosmetic-assets");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, CosmeticCatalogEntry> catalog = new ConcurrentHashMap<>();
    private final Set<String> requested = ConcurrentHashMap.newKeySet();

    public CosmeticAssetLoader(LanPlusNetwork network, CosmeticModels models, AssetCache cache) {
        this.network = network;
        this.models = models;
        this.cache = cache;
    }

    public void refreshCatalog() {
        if (network == null) {
            return;
        }
        network.getCosmeticCatalog().whenComplete((list, err) -> {
            if (err != null || list == null) {
                return;
            }
            for (CosmeticCatalogEntry e : list) {
                catalog.put(e.id(), e);
                models.putMeta(CosmeticMeta.parse(e.id(), e.meta()));
            }
        });
    }

    public void ensureModel(String id) {
        if (id == null || models.model(id) != null) {
            return;
        }
        CosmeticCatalogEntry entry = catalog.get(id);
        if (entry == null || !requested.add(id)) {
            return;
        }
        executor.execute(() -> {
            byte[] geo = download(entry.geoHash(), entry.geoUrl());
            byte[] png = download(entry.texHash(), entry.texUrl());
            byte[] anim = entry.animUrl() == null ? null : download(entry.animHash(), entry.animUrl());
            if (geo == null || png == null) {
                requested.remove(id);
                return;
            }
            Minecraft.getInstance().execute(() -> models.register(id, geo, anim, png));
        });
    }

    private byte[] download(String key, String url) {
        if (key != null && cache != null) {
            byte[] cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
        }
        byte[] bytes = httpGet(url);
        if (bytes != null && key != null && cache != null) {
            cache.put(key, bytes);
        }
        return bytes;
    }

    private byte[] httpGet(String url) {
        try {
            HttpResponse<byte[]> resp = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(FETCH_TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            return resp.statusCode() == 200 ? resp.body() : null;
        } catch (Exception e) {
            return null;
        }
    }
}