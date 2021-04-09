package com.mc9y.nyeconomy.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mc9y.nyeconomy.Main;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Blank038
 * @since 2021-03-07
 */
public class AccountCache {
    public static final HashMap<String, AccountCache> CACHE_DATA = new HashMap<>();

    private final HashMap<String, Integer> CURRENCY_MAP = new HashMap<>();

    public AccountCache(JsonObject jsonObject) {
        this.update(jsonObject);
    }

    public int balance(String type) {
        return this.CURRENCY_MAP.getOrDefault(Main.getNyEconomyAPI().checkVaultType(type), 0);
    }

    public AccountCache set(String type, int amount) {
        String lastType = Main.getNyEconomyAPI().checkVaultType(type);
        if (this.CURRENCY_MAP.containsKey(lastType)) {
            this.CURRENCY_MAP.replace(lastType, amount);
        } else {
            this.CURRENCY_MAP.put(lastType, amount);
        }
        return this;
    }

    public JsonObject toJsonObject() {
        JsonObject object = new JsonObject();
        JsonArray array = new JsonArray();
        for (Map.Entry<String, Integer> entry : this.CURRENCY_MAP.entrySet()) {
            JsonObject temp = new JsonObject();
            temp.addProperty("type", entry.getKey());
            temp.addProperty("count", entry.getValue());
            array.add(temp);
        }
        object.add("currencys", array);
        return object;
    }

    /**
     * 刷新数据
     *
     * @param object 数据JsonObject对象
     */
    public void update(JsonObject object) {
        if (object != null && object.has("currencys")) {
            JsonArray array = object.getAsJsonArray("currencys");
            // 计算数据
            for (int i = 0; i < array.size(); i++) {
                JsonObject temp = array.get(i).getAsJsonObject();
                if (temp == null || temp.isJsonNull() || temp.get("type").isJsonNull()) {
                    continue;
                }
                String name = temp.get("type").getAsString();
                int count = temp.get("count").getAsInt();
                if (this.CURRENCY_MAP.containsKey(name)) {
                    this.CURRENCY_MAP.replace(name, count);
                } else {
                    this.CURRENCY_MAP.put(name, count);
                }
            }
        }
    }
}
