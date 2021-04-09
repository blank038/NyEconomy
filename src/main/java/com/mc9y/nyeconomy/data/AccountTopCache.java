package com.mc9y.nyeconomy.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mc9y.nyeconomy.Main;

import java.util.HashMap;

/**
 * @author Blank038
 * @since 2021-03-11
 */
public class AccountTopCache {
    private final HashMap<String, Integer> TEMP_MAP = new HashMap<>();

    public AccountTopCache(JsonObject jsonObject) {
        if (jsonObject.has("currencys")) {
            JsonArray array = jsonObject.getAsJsonArray("currencys");
            for (int i = 0; i < array.size(); i++) {
                JsonObject object = array.get(i).getAsJsonObject();
                if (object == null || object.isJsonNull() || object.get("type").isJsonNull()) {
                    continue;
                }
                String key = object.get("type").getAsString().replace("60CFC2D63B8F0E9D",
                        Main.getInstance().getConfig().getString("economy-bridge.currency"));
                TEMP_MAP.put(key, object.get("count").getAsInt());
            }
        }
        if (jsonObject.has("top_data")) {
            JsonArray array = jsonObject.getAsJsonArray("top_data");
            for (int i = 0; i < array.size(); i++) {
                JsonObject object = array.get(i).getAsJsonObject();
                String key = object.get("type").getAsString().replace("60CFC2D63B8F0E9D",
                        Main.getInstance().getConfig().getString("economy-bridge.currency"));
                TEMP_MAP.put(key, object.get("count").getAsInt());
            }
        }
    }

    public int getCurrencyCount(String key) {
        return this.TEMP_MAP.getOrDefault(key, 0);
    }

    public void setRank(String currency, int top) {

    }

    public static class Entry<K, V> {
        private final K NAME;
        private final V COUNT;

        public Entry(K k, V v) {
            this.NAME = k;
            this.COUNT = v;
        }

        public K getName() {
            return this.NAME;
        }

        public V getValue() {
            return COUNT;
        }
    }
}