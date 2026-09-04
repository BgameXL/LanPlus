package dev.bgame.lanplus.cosmetics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bgame.lanplus.LanplusCommon;
import dev.bgame.lanplus.cosmetics.geckolib.cache.object.BakedGeoModel;
import dev.bgame.lanplus.cosmetics.geckolib.loading.json.raw.Model;
import dev.bgame.lanplus.cosmetics.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import dev.bgame.lanplus.cosmetics.geckolib.loading.object.BakedAnimations;
import dev.bgame.lanplus.cosmetics.geckolib.loading.object.BakedModelFactory;
import dev.bgame.lanplus.cosmetics.geckolib.loading.object.GeometryTree;

import java.nio.charset.StandardCharsets;

public final class CosmeticLoader {

    private CosmeticLoader() {
    }

    public static BakedGeoModel geometry(byte[] geoJson) {
        JsonObject root = JsonParser.parseString(new String(geoJson, StandardCharsets.UTF_8)).getAsJsonObject();
        Model model = KeyFramesAdapter.GEO_GSON.fromJson(root, Model.class);
        GeometryTree tree = GeometryTree.fromModel(model);
        return BakedModelFactory.getForNamespace(LanplusCommon.MODID).constructGeoModel(tree);
    }

    public static BakedAnimations animations(byte[] animJson) {
        JsonObject root = JsonParser.parseString(new String(animJson, StandardCharsets.UTF_8)).getAsJsonObject();
        return KeyFramesAdapter.GEO_GSON.fromJson(root.getAsJsonObject("animations"), BakedAnimations.class);
    }
}
