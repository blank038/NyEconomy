package com.mc9y.nyeconomy.api.event;

import com.mc9y.nyeconomy.Commodity;
import com.mc9y.nyeconomy.util.Money;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.math.BigDecimal;

/**
 * @author Blank038
 */
public class PlayerBuyCommodityEvent extends Event
        implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();
    private final Player player;
    private final Commodity commodity;
    private BigDecimal price;
    private boolean cancelled;

    public PlayerBuyCommodityEvent(Player player, Commodity commodity, int price) {
        this(player, commodity, Money.of(price));
    }

    public PlayerBuyCommodityEvent(Player player, Commodity commodity, BigDecimal price) {
        this.player = player;
        this.commodity = commodity;
        this.price = Money.normalize(price);
    }

    public Player getPlayer() {
        return player;
    }

    public int getPrice() {
        return Money.toLegacyInt(price);
    }

    public void setPrice(int price) {
        this.price = Money.of(price);
    }

    public BigDecimal getPriceExact() {
        return price;
    }

    public void setPriceExact(BigDecimal price) {
        this.price = Money.normalize(price);
    }

    public Commodity getCommodity() {
        return commodity;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
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
