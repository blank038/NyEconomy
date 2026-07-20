package com.mc9y.nyeconomy.api;

import com.mc9y.nyeconomy.Commodity;
import com.mc9y.nyeconomy.api.event.PlayerBuyCommodityEvent;
import com.mc9y.nyeconomy.api.event.PlayerEconomyChangeEvent;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class ApiCompatibilityTest {
    @Test
    public void preservesLegacyEconomyApiDescriptors() throws Exception {
        assertEquals(int.class, NyEconomyAPI.class
                .getMethod("getBalance", String.class, String.class).getReturnType());
        assertEquals(int.class, NyEconomyAPI.class
                .getMethod("getBalance", String.class, UUID.class).getReturnType());

        assertEquals(void.class, NyEconomyAPI.class
                .getMethod("deposit", String.class, String.class, int.class).getReturnType());
        assertEquals(void.class, NyEconomyAPI.class
                .getMethod("withdraw", String.class, UUID.class, int.class).getReturnType());
        assertEquals(void.class, NyEconomyAPI.class
                .getMethod("set", String.class, String.class, int.class).getReturnType());
        assertEquals(void.class, NyEconomyAPI.class
                .getMethod("reset", String.class, UUID.class).getReturnType());
    }

    @Test
    public void preservesLegacyCommodityAndEventConstructors() throws Exception {
        Commodity.class.getConstructor(String.class, int.class, List.class, String.class);
        PlayerBuyCommodityEvent.class.getConstructor(Player.class, Commodity.class, int.class);
        PlayerEconomyChangeEvent.class.getConstructor(UUID.class, String.class,
                com.mc9y.nyeconomy.api.event.EconomyAction.class,
                int.class, int.class, boolean.class);
    }
}
