package com.mc9y.nyeconomy.helper;

import com.mc9y.nyeconomy.Main;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.Consumer;

/**
 * @author Blank038
 */
public class SchedulerHelper {

    public static void runTaskAsync(Runnable runnable) {
        if (PlatformHelper.isFolia()) {
            Bukkit.getServer().getGlobalRegionScheduler().execute(Main.getInstance(), runnable);
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), runnable);
        }
    }

    public static Object runTaskTimerAsync(Runnable runnable, long delay, long period) {
        if (PlatformHelper.isFolia()) {
            Consumer<ScheduledTask> consumer = (task) -> runnable.run();
            return Bukkit.getServer().getGlobalRegionScheduler().runAtFixedRate(Main.getInstance(), consumer, delay, period);
        } else {
            return Bukkit.getScheduler().runTaskTimerAsynchronously(Main.getInstance(), runnable, delay, period);
        }
    }

    public static void cancelTask(Object o) {
        if (PlatformHelper.isFolia()) {
            ((ScheduledTask) o).cancel();
        } else {
            ((BukkitTask) o).cancel();
        }
    }
}
