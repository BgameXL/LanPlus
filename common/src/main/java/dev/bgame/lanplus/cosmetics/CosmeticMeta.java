package dev.bgame.lanplus.cosmetics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record CosmeticMeta(String id, String name, String artist, CosmeticSlot slot, String rarity,
                           String description, String unlock, String defaultClip, Map<String, String> stateClipMap) {

    public static CosmeticMeta def(String id) {
        return new CosmeticMeta(id, id, "", CosmeticSlot.HEAD, "common", "", "", "", Map.of());
    }

    public static CosmeticMeta parse(String id, String json) {
        if (json == null || json.isBlank()) {
            return def(id);
        }
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            return new CosmeticMeta(id, str(o, "name", id), str(o, "artist", ""), slot(str(o, "slot", "head")),
                    str(o, "rarity", "common"), str(o, "description", ""), unlock(o), str(o, "defaultClip", ""),
                    stateClip(o));
        } catch (RuntimeException e) {
            return def(id);
        }
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
}
