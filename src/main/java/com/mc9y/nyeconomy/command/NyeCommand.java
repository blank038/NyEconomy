package com.mc9y.nyeconomy.command;

import com.mc9y.nyeconomy.Commodity;
import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.api.event.PlayerBuyCommodityEvent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Blank038
 */
public class NyeCommand implements CommandExecutor {
    private final Main INSTANCE;
    private final List<String> DELAY_LIST = new ArrayList<>();

    public NyeCommand(Main main) {
        this.INSTANCE = main;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (DELAY_LIST.contains(sender.getName())) {
            sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.Delay").replace("&", "§"));
            return true;
        }
        if (!sender.hasPermission("com.mc9y.nyeconomy.bypass")) {
            DELAY_LIST.add(sender.getName());
            Bukkit.getScheduler().runTaskLaterAsynchronously(INSTANCE, () -> DELAY_LIST.remove(sender.getName()), 20L * INSTANCE.getConfig().getInt("CommandDelay"));
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            for (String text : INSTANCE.getConfig().getStringList("Message.help." + (sender.hasPermission("nye.admin") ? "admin" : "default"))) {
                sender.sendMessage(text.replace("&", "§").replace("%c", label));
            }
        } else {
            switch (args[0]) {
                case "me":
                    this.showInfo(sender);
                    break;
                case "buy":
                    this.buy(sender, args);
                    break;
                case "pay":
                    this.payCommand(sender, args);
                    break;
                case "create":
                    this.createCurrency(sender, args);
                    break;
                case "give":
                    this.change(sender, args, 0);
                    break;
                case "reset":
                    this.resetPlayer(sender, args);
                    break;
                case "delete":
                    this.deletePlayerCurrency(sender, args);
                    break;
                case "reload":
                    if (sender.hasPermission("nye.admin")) {
                        INSTANCE.loadConfig();
                        sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.Reload").replace("&", "§"));
                    } else {
                        sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.NoHasPermission").replace("&", "§"));
                    }
                    break;
                case "set":
                    this.change(sender, args, 2);
                    break;
                case "look":
                    this.look(sender, args);
                    break;
                case "take":
                    this.change(sender, args, 1);
                    break;
                default:
                    sender.sendMessage(INSTANCE.prefix + "§c命令参数不存在, 输入 §f/" + label + " help §c查看命令帮助！");
                    break;
            }
        }
        return true;
    }

    /**
     * 查看个人信息
     */
    private void showInfo(CommandSender sender) {
        if (INSTANCE.vaults.size() == 0) {
            sender.sendMessage(INSTANCE.prefix + "§c无可查询数据.");
            return;
        }
        sender.sendMessage("§f" + sender.getName() + " 的个人经济情况;");
        for (String v : INSTANCE.vaults) {
            sender.sendMessage(" §a> §f" + v + ": §7" + Main.getNyEconomyAPI().getBalance(v, sender.getName()));
        }
    }

    /**
     * 设置玩家账户
     *
     * @param type 0 为增加, 1 为减少, 2 为设置
     */
    private void change(CommandSender sender, String[] args, int type) {
        if (sender.hasPermission("nye.admin")) {
            if (checkArgs(sender, args)) {
                return;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (Exception e) {
                sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.FailAmount").replace("&", "§"));
                return;
            }
            switch (type) {
                case 0:
                    // 给予
                    Main.getNyEconomyAPI().deposit(args[2], args[1], amount);
                    sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.Give").replace("&", "§")
                            .replace("%type%", args[2]).replace("%amount%", amount + "")
                            .replace("%player%", args[1]));
                    break;
                case 1:
                    // 减少
                    Main.getNyEconomyAPI().withdraw(args[2], args[1], amount);
                    sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.Take").replace("&", "§")
                            .replace("%type%", args[2]).replace("%amount%", amount + "")
                            .replace("%player%", args[1]).replace("%last%", Main.getNyEconomyAPI().getBalance(args[2], args[1]) + ""));
                    break;
                case 2:
                    // 设置
                    Main.getNyEconomyAPI().set(args[2], args[1], amount);
                    sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.set").replace("&", "§")
                            .replace("%type%", args[2]).replace("%amount%", amount + "").replace("%player%", args[1]));
                    break;
                default:
                    break;
            }
        } else {
            sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.NoHasPermission").replace("&", "§"));
        }
    }

    /**
     * 查看玩家账户余额情况
     */
    private void look(CommandSender sender, String[] args) {
        if (sender.hasPermission("nye.look")) {
            if (args.length == 1) {
                sender.sendMessage(INSTANCE.prefix + "§c该玩家不在线!");
                return;
            }
            if (INSTANCE.vaults.size() == 0) {
                sender.sendMessage(INSTANCE.prefix + "§c无可查询数据.");
                return;
            }
            sender.sendMessage("§f" + args[1] + " 的个人经济情况;");
            for (String v : INSTANCE.vaults) {
                sender.sendMessage(" §a> §f" + v + ": §7" + Main.getNyEconomyAPI().getBalance(v, args[1]));
            }
        } else {
            sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.NoHasPermission").replace("&", "§"));
        }
    }

    /**
     * 玩家购买商品
     */
    private void buy(CommandSender sender, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (args.length == 1) {
                player.sendMessage(INSTANCE.prefix + "§c请输入商品名.");
                return;
            }
            if (!Commodity.COMMODITY_MAP.containsKey(args[1])) {
                player.sendMessage(INSTANCE.prefix + "§c商品不存在, 请输入正确的商品名.");
                return;
            }
            Commodity commodity = Commodity.COMMODITY_MAP.get(args[1]);
            if (Main.getNyEconomyAPI().getBalance(commodity.getType(), player.getName()) < commodity.getAmount()) {
                player.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.LackCurrency").replace("&", "§")
                        .replace("%type%", commodity.getType()).replace("%amount%", commodity.getAmount() + ""));
                return;
            }
            PlayerBuyCommodityEvent event = new PlayerBuyCommodityEvent(player, commodity, commodity.getAmount());
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return;
            }
            // 开始扣除玩家货币
            Main.getNyEconomyAPI().withdraw(commodity.getType(), player.getName(), event.getPrice());
            commodity.give(player.getName());
            if (Bukkit.getPluginManager().getPlugin("PokeQuest") != null && Bukkit.getPluginManager().getPlugin("PokeQuest").isEnabled()) {
                // 反射提交任务
                try {
                    Class<?> c = Class.forName(" com.blank038.pokequest.Main");
                    Field field = c.getDeclaredField("questManager");
                    field.setAccessible(true);
                    Class<?> questManager = field.getDeclaringClass();
                    Method method = questManager.getMethod("submitQuest", String.class, String.class, String.class);
                    method.invoke(questManager, player.getName(), "BuyCommodity", commodity.getType());
                    field.setAccessible(false);
                } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
            player.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.BuySuccess").replace("&", "§")
                    .replace("%commodity%", commodity.getName()));
        }
    }

    /**
     * 玩家转账
     */
    private void payCommand(CommandSender sender, String[] args) {
        if (sender.hasPermission("nye.pay")) {
            if (checkArgs(sender, args)) {
                return;
            }
            if (INSTANCE.getConfig().getStringList("PayBlackList").contains(args[2])) {
                sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.PayCancelled").replace("&", "§"));
                return;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (Exception e) {
                sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.FailAmount").replace("&", "§"));
                return;
            }
            if (args[1].equals(sender.getName())) {
                sender.sendMessage(INSTANCE.prefix + "§c不能给自己转账！");
                return;
            }
            if (Main.getNyEconomyAPI().getBalance(args[2], sender.getName()) < amount) {
                sender.sendMessage(INSTANCE.prefix + "§c你没有足够的货币可以向其他玩家转账！");
                return;
            }
            Main.getNyEconomyAPI().withdraw(args[2], sender.getName(), amount);
            Main.getNyEconomyAPI().deposit(args[2], args[1], amount);
            sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.Pay").replace("&", "§")
                    .replace("%type%", args[2]).replace("%amount%", amount + "")
                    .replace("%player%", args[1]));
        } else {
            sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.NoHasPermission").replace("&", "§"));
        }
    }

    /**
     * 创建货币
     */
    private void createCurrency(CommandSender sender, String[] args) {
        if (sender.hasPermission("nye.admin")) {
            if (args.length <= 1) {
                sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.FailType").replace("&", "§"));
                return;
            }
            if (INSTANCE.vaults.contains(args[1])) {
                sender.sendMessage(INSTANCE.prefix + "§c该货币已存在！");
                return;
            }
            File f = new File(INSTANCE.getDataFolder() + "/Currencies/", args[1] + ".yml");
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            INSTANCE.vaults.add(args[1]);
            sender.sendMessage(INSTANCE.prefix + "§a成功创建货币类型: §f" + args[1] + "§a 请前往 §fconfig.yml §a启用该货币");
        }
    }

    /**
     * 重置玩家货币
     */
    private void resetPlayer(CommandSender sender, String[] args) {
        if (sender.hasPermission("nye.admin")) {
            if (args.length <= 1) {
                sender.sendMessage(INSTANCE.prefix + "§c请输入玩家名！");
                return;
            }
            if (args.length <= 2) {
                sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.FailType").replace("&", "§"));
                return;
            }
            if (!INSTANCE.vaults.contains(args[2])) {
                sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.FailCurrency").replace("&", "§"));
                return;
            }
            Main.getNyEconomyAPI().reset(args[2], args[1]);
            sender.sendMessage(INSTANCE.prefix + "§e成功重置玩家货币§f(%type%)§e的数据".replace("%type%", args[2]));
        } else {
            sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.NoHasPermission").replace("&", "§"));
        }
    }

    /**
     * 扣除玩家货币
     */
    private void deletePlayerCurrency(CommandSender sender, String[] args) {
        if (sender.hasPermission("nye.admin")) {
            if (args.length <= 1) {
                sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.FailType").replace("&", "§"));
                return;
            }
            if (!INSTANCE.vaults.contains(args[1])) {
                sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.FailCurrency").replace("&", "§"));
                return;
            }
            INSTANCE.vaults.remove(args[1]);
            File f1 = new File(INSTANCE.getDataFolder() + "/Currencies/", args[1] + ".yml");
            f1.delete();
            List<String> removes = new ArrayList<>();
            for (String key : Commodity.COMMODITY_MAP.keySet()) {
                if (Commodity.COMMODITY_MAP.get(key).getType().equals(args[1])) {
                    removes.add(key);
                }
            }
            for (String key : removes) {
                Commodity.COMMODITY_MAP.remove(key);
            }
            sender.sendMessage(INSTANCE.prefix + "§e成功移除货币: §f" + args[1] + " §e商品: §f" + removes.size());
        } else {
            sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.NoHasPermission").replace("&", "§"));
        }
    }

    /**
     * 检测命令参数
     *
     * @param sender 命令执行者
     * @param args   参数列表
     * @return 是否达标
     */
    private boolean checkArgs(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            sender.sendMessage(INSTANCE.prefix + "§c请输入玩家名！");
            return true;
        }
        if (args.length <= 2) {
            sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.FailType").replace("&", "§"));
            return true;
        }
        if (args.length <= 3) {
            sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.FailAmount").replace("&", "§"));
            return true;
        }
        if (!INSTANCE.vaults.contains(args[2])) {
            sender.sendMessage(INSTANCE.prefix + INSTANCE.getConfig().getString("Message.FailCurrency").replace("&", "§"));
            return true;
        }
        return false;
    }
}
