package dev.bgame.lanplus.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record CosmeticMeta(String id, String name, String artist, CosmeticSlot slot, String rarity,
                           String description, String unlock, int price, String defaultClip,
                           Map<String, String> stateClipMap, CosmeticTransform transform) {

    public static CosmeticMeta def(String id) {
        return new CosmeticMeta(id, id, "", CosmeticSlot.HEAD, "common", "", "", 0, "", Map.of(),
                CosmeticTransform.IDENTITY);
    }

    public static CosmeticMeta parse(String id, String json) {
        if (json == null || json.isBlank()) {
            return def(id);
        }
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            return new CosmeticMeta(id, str(o, "name", id), str(o, "artist", ""), slot(str(o, "slot", "head")),
                    str(o, "rarity", "common"), str(o, "description", ""), unlock(o), intVal(o, "price", 0),
                    str(o, "defaultClip", ""), stateClip(o), transform(o));
        } catch (RuntimeException e) {
            return def(id);
        }
    }

    private static int intVal(JsonObject o, String key, int fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : fallback;
    }

    private static String str(JsonObject o, String key, String fallback) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : fallback;
    }

    private static CosmeticSlot slot(String s) {
        try {
            return CosmeticSlot.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CosmeticSlot.HEAD;
        }
    }

    private static String unlock(JsonObject o) {
        if (!o.has("unlock")) {
            return "";
        }
        JsonElement u = o.get("unlock");
        if (u.isJsonPrimitive()) {
            return u.getAsString();
        }
        if (u.isJsonObject()) {
            JsonObject uo = u.getAsJsonObject();
            String type = str(uo, "type", "");
            if (type.equals("tier")) {
                return "Tier " + str(uo, "tier", "?");
            }
            if (type.equals("modpack")) {
                return "Play " + str(uo, "modpackId", "?");
            }
        }
        return "";
    }

    private static Map<String, String> stateClip(JsonObject o) {
        if (!o.has("stateClipMap") || !o.get("stateClipMap").isJsonObject()) {
            return Map.of();
        }
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("stateClipMap").entrySet()) {
            if (e.getValue().isJsonPrimitive()) {
                m.put(e.getKey(), e.getValue().getAsString());
            }
        }
        return m;
    }

    private static CosmeticTransform transform(JsonObject meta) {
        if (!meta.has("transform") || !meta.get("transform").isJsonObject()) {
            return CosmeticTransform.IDENTITY;
        }
        JsonObject transform = meta.getAsJsonObject("transform");
        JsonArray translation = transform.has("translation") && transform.get("translation").isJsonArray()
                ? transform.getAsJsonArray("translation") : new JsonArray();
        JsonArray rotation = transform.has("rotation") && transform.get("rotation").isJsonArray()
                ? transform.getAsJsonArray("rotation") : new JsonArray();
        float scale = number(transform, "scale", 1);
        if (scale <= 0) {
            scale = 1;
        }
        return new CosmeticTransform(component(translation, 0), component(translation, 1), component(translation, 2),
                component(rotation, 0), component(rotation, 1), component(rotation, 2), scale);
    }

    private static float component(JsonArray values, int index) {
        return index < values.size() ? number(values.get(index), 0) : 0;
    }

    private static float number(JsonObject object, String key, float fallback) {
        return object.has(key) ? number(object.get(key), fallback) : fallback;
    }

    private static float number(JsonElement element, float fallback) {
        try {
            float value = element.getAsFloat();
            return Float.isFinite(value) ? value : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
