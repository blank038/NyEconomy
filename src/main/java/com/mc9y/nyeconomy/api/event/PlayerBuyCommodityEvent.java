package com.mc9y.nyeconomy.api.event;

import com.mc9y.nyeconomy.Commodity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * @author Blank038
 */
public class PlayerBuyCommodityEvent extends Event
        implements Cancellable {
    private static final HandlerList handler = new HandlerList();
    private final Player player;
    private final Commodity commodity;
    private int price;
    private boolean cancelled;

    public PlayerBuyCommodityEvent(Player player, Commodity commodity, int price) {
        this.player = player;
        this.commodity = commodity;
        this.price = price;
    }

    public Player getPlayer() {
        return player;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public Commodity getCommodity() {
        return commodity;
    }

    @Override
    public HandlerList getHandlers() {
        return handler;
    }

    public static HandlerList getHandlerList() {
        return handler;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean b) {
        this.cancelled = b;
    }
}