package com.mc9y.nyeconomy.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mc9y.nyeconomy.Main;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Blank038
 * @since 2021-03-11
 */
public class AccountTopCache {
    private final Map<String, Integer> tempMap = new HashMap<>();

    public AccountTopCache(JsonObject jsonObject) {
        if (jsonObject.has("currencys")) {
            JsonArray array = jsonObject.getAsJsonArray("currencys");
            for (int i = 0; i < array.size(); i++) {
                JsonObject object = array.get(i).getAsJsonObject();
                if (object == null || object.isJsonNull() || object.get("type").isJsonNull()) {
                    continue;
                }
                String key = object.get("type").getAsString()
                        .replace("60CFC2D63B8F0E9D", Main.getString("economy-bridge.currency"));
                tempMap.put(key, object.get("count").getAsInt());
            }
        }
        if (jsonObject.has("top_data")) {
            JsonArray array = jsonObject.getAsJsonArray("top_data");
            for (int i = 0; i < array.size(); i++) {
                JsonObject object = array.get(i).getAsJsonObject();
                String key = object.get("type").getAsString()
                        .replace("60CFC2D63B8F0E9D", Main.getString("economy-bridge.currency"));
                tempMap.put(key, object.get("count").getAsInt());
            }
        }
    }

    public AccountTopCache() {
    }

    public int getCurrencyCount(String key) {
        return this.tempMap.getOrDefault(key, 0);
    }

    public Map<String, Integer> getTempMap() {
        return tempMap;
    }

    public static class Entry<K, V> {
        private final K name;
        private final V count;

        public Entry(K k, V v) {
            this.name = k;
            this.count = v;
        }

        public K getName() {
            return this.name;
        }

        public V getValue() {
            return count;
        }
    }
}