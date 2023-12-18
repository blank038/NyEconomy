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
    public static final Map<String, AccountCache> CACHE_DATA = new HashMap<>();

    private final Map<String, Integer> currencyMap = new HashMap<>();

    public AccountCache(JsonObject jsonObject) {
        this.update(jsonObject);
    }

    public int balance(String type) {
        return this.currencyMap.getOrDefault(Main.getNyEconomyAPI().checkVaultType(type), 0);
    }

    public AccountCache set(String type, int amount) {
        String lastType = Main.getNyEconomyAPI().checkVaultType(type);
        if (this.currencyMap.containsKey(lastType)) {
            this.currencyMap.replace(lastType, amount);
        } else {
            this.currencyMap.put(lastType, amount);
        }
        return this;
    }

    public JsonObject toJsonObject() {
        JsonObject object = new JsonObject();
        JsonArray array = new JsonArray();
        for (Map.Entry<String, Integer> entry : this.currencyMap.entrySet()) {
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
            for (int i = 0; i < array.size(); i++) {
                JsonObject temp = array.get(i).getAsJsonObject();
                if (temp == null || temp.isJsonNull() || temp.get("type").isJsonNull()) {
                    continue;
                }
                String name = temp.get("type").getAsString();
                int count = temp.get("count").getAsInt();
                if (this.currencyMap.containsKey(name)) {
                    this.currencyMap.replace(name, count);
                } else {
                    this.currencyMap.put(name, count);
                }
            }
        }
    }
}
