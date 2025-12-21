package com.mc9y.nyeconomy.command;

import com.aystudio.core.bukkit.AyCore;
import com.mc9y.nyeconomy.Commodity;
import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.api.event.PlayerBuyCommodityEvent;
import com.mc9y.nyeconomy.data.TopCache;
import com.mc9y.nyeconomy.message.TopMessage;
import com.mc9y.nyeconomy.migration.MigrationHandler;
import com.mc9y.nyeconomy.service.UUIDService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
public class NyeCommand implements CommandExecutor {
    private final Main instance;
    private final List<String> cooldown = new ArrayList<>();

    public NyeCommand(Main main) {
        this.instance = main;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (cooldown.contains(sender.getName())) {
            sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.Delay").replace("&", "§"));
            return true;
        }
        if (!sender.hasPermission("com.mc9y.nyeconomy.bypass")) {
            cooldown.add(sender.getName());
            AyCore.getPlatformApi().runTaskLaterAsynchronously(instance, () -> cooldown.remove(sender.getName()), 20L * instance.getConfig().getInt("CommandDelay"));
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            for (String text : instance.getConfig().getStringList("Message.help." + (sender.hasPermission("nye.admin") ? "admin" : "default"))) {
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
                    this.deleteCurrency(sender, args);
                    break;
                case "reload":
                    if (sender.hasPermission("nye.admin")) {
                        instance.loadConfig();
                        sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.Reload").replace("&", "§"));
                    } else {
                        sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.NoHasPermission").replace("&", "§"));
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
                case "top":
                    this.top(sender, args);
                    break;
                case "migrate":
                    this.migrateData(sender, args);
                    break;
                case "migrate-uuid":
                    this.migrateUUID(sender);
                    break;
                default:
                    sender.sendMessage(instance.prefix + "§c命令参数不存在, 输入 §f/" + label + " help §c查看命令帮助！");
                    break;
            }
        }
        return true;
    }

    /**
     * 查看个人信息
     */
    private void showInfo(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (instance.vaults.isEmpty()) {
                sender.sendMessage(instance.prefix + "§c无可查询数据.");
                return;
            }
            sender.sendMessage("§f" + sender.getName() + " 的个人经济情况;");
            for (String v : instance.vaults) {
                sender.sendMessage(" §a> §f" + v + ": §7" + Main.getNyEconomyAPI().getBalance(v, player.getName()));
            }
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
            UUID playerUUID = UUIDService.getInstance().getPlayerUUID(args[1]);
            
            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (Exception e) {
                sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.FailAmount").replace("&", "§"));
                return;
            }
            switch (type) {
                case 0:
                    Main.getNyEconomyAPI().deposit(args[2], playerUUID, amount);
                    sender.sendMessage(Main.getString("Message.Give", true)
                            .replace("&", "§")
                            .replace("%type%", args[2])
                            .replace("%amount%", String.valueOf(amount))
                            .replace("%player%", args[1]));
                    if (args.length > 4 && "true".equalsIgnoreCase(args[4])) {
                        Player target = Bukkit.getPlayerExact(args[1]);
                        if (target != null && target.isOnline()) {
                            target.sendMessage(Main.getString("Message.receive", true)
                                    .replace("%type%", args[2])
                                    .replace("%amount%", String.valueOf(amount))
                                    .replace("%sender%", sender.getName())
                                    .replace("%player%", args[1]));
                        }
                    }
                    break;
                case 1:
                    Main.getNyEconomyAPI().withdraw(args[2], playerUUID, amount);
                    sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.Take")
                            .replace("&", "§")
                            .replace("%type%", args[2])
                            .replace("%amount%", String.valueOf(amount))
                            .replace("%player%", args[1])
                            .replace("%last%", String.valueOf(Main.getNyEconomyAPI().getBalance(args[2], playerUUID))));
                    break;
                case 2:
                    Main.getNyEconomyAPI().set(args[2], playerUUID, amount);
                    sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.set")
                            .replace("&", "§")
                            .replace("%type%", args[2])
                            .replace("%amount%", String.valueOf(amount))
                            .replace("%player%", args[1]));
                    break;
                default:
                    break;
            }
        } else {
            sender.sendMessage(Main.getString("Message.NoHasPermission", true));
        }
    }

    /**
     * 查看玩家账户余额情况
     */
    private void look(CommandSender sender, String[] args) {
        if (sender.hasPermission("nye.look")) {
            if (args.length == 1) {
                sender.sendMessage(instance.prefix + "§c该玩家不在线!");
                return;
            }
            if (instance.vaults.isEmpty()) {
                sender.sendMessage(instance.prefix + "§c无可查询数据.");
                return;
            }
            sender.sendMessage("§f" + args[1] + " 的个人经济情况;");
            for (String v : instance.vaults) {
                sender.sendMessage(" §a> §f" + v + ": §7" + Main.getNyEconomyAPI().getBalance(v, args[1]));
            }
        } else {
            sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.NoHasPermission").replace("&", "§"));
        }
    }

    /**
     * 玩家购买商品
     */
    private void buy(CommandSender sender, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (args.length == 1) {
                player.sendMessage(instance.prefix + "§c请输入商品名.");
                return;
            }
            if (!Commodity.COMMODITY_MAP.containsKey(args[1])) {
                player.sendMessage(instance.prefix + "§c商品不存在, 请输入正确的商品名.");
                return;
            }
            Commodity commodity = Commodity.COMMODITY_MAP.get(args[1]);
            if (Main.getNyEconomyAPI().getBalance(commodity.getType(), player.getName()) < commodity.getAmount()) {
                player.sendMessage(instance.prefix + instance.getConfig().getString("Message.LackCurrency").replace("&", "§")
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
            player.sendMessage(instance.prefix + instance.getConfig().getString("Message.BuySuccess").replace("&", "§")
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
            if (instance.getConfig().getStringList("PayBlackList").contains(args[2])) {
                sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.PayCancelled").replace("&", "§"));
                return;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (Exception e) {
                sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.FailAmount").replace("&", "§"));
                return;
            }
            if (amount <= 0) {
                sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.FailAmount").replace("&", "§"));
                return;
            }
            if (args[1].equals(sender.getName())) {
                sender.sendMessage(instance.prefix + "§c不能给自己转账！");
                return;
            }
            if (Main.getNyEconomyAPI().getBalance(args[2], sender.getName()) < amount) {
                sender.sendMessage(instance.prefix + "§c你没有足够的货币可以向其他玩家转账！");
                return;
            }
            Main.getNyEconomyAPI().withdraw(args[2], sender.getName(), amount);
            Main.getNyEconomyAPI().deposit(args[2], args[1], amount);
            sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.Pay").replace("&", "§")
                    .replace("%type%", args[2]).replace("%amount%", amount + "")
                    .replace("%player%", args[1]));
        } else {
            sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.NoHasPermission").replace("&", "§"));
        }
    }

    /**
     * 创建货币
     */
    private void createCurrency(CommandSender sender, String[] args) {
        if (sender.hasPermission("nye.admin")) {
            if (args.length <= 1) {
                sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.FailType").replace("&", "§"));
                return;
            }
            if (instance.vaults.contains(args[1])) {
                sender.sendMessage(instance.prefix + "§c该货币已存在！");
                return;
            }
            File f = new File(instance.getDataFolder() + "/Currencies/", args[1] + ".yml");
            try {
                f.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
            instance.vaults.add(args[1]);
            sender.sendMessage(instance.prefix + "§a成功创建货币类型: §f" + args[1] + "§a 请前往 §fconfig.yml §a启用该货币");
        }
    }

    /**
     * 重置玩家货币
     */
    private void resetPlayer(CommandSender sender, String[] args) {
        if (sender.hasPermission("nye.admin")) {
            if (args.length <= 1) {
                sender.sendMessage(instance.prefix + "§c请输入玩家名！");
                return;
            }
            if (args.length <= 2) {
                sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.FailType").replace("&", "§"));
                return;
            }
            if (!instance.vaults.contains(args[2])) {
                sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.FailCurrency").replace("&", "§"));
                return;
            }
            UUID playerUUID = UUIDService.getInstance().getPlayerUUID(args[1]);
            Main.getNyEconomyAPI().reset(args[2], playerUUID);
            sender.sendMessage(instance.prefix + "§e成功重置玩家货币§f(%type%)§e的数据".replace("%type%", args[2]));
        } else {
            sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.NoHasPermission").replace("&", "§"));
        }
    }

    /**
     * 扣除玩家货币
     */
    private void deleteCurrency(CommandSender sender, String[] args) {
        if (sender.hasPermission("nye.admin")) {
            if (checkCurrency(sender, args)) {
                return;
            }
            instance.vaults.remove(args[1]);
            File f1 = new File(instance.getDataFolder() + "/Currencies/", args[1] + ".yml");
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
            sender.sendMessage(instance.prefix + "§e成功移除货币: §f" + args[1] + " §e商品: §f" + removes.size());
        } else {
            sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.NoHasPermission").replace("&", "§"));
        }
    }

    private void top(CommandSender sender, String[] args) {
        if (sender.hasPermission("nye.top")) {
            if (this.checkCurrency(sender, args)) {
                return;
            }
            int page = 1;
            if (args.length > 2) {
                try {
                    page = Integer.parseInt(args[2]);
                } catch (Exception ignored) {
                }
            }
            page = Math.max(1, page);
            new TopMessage(TopCache.getInstance().getTopData(args[1]), page).send(args[1], sender);
        }
    }

    private boolean checkCurrency(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.FailType").replace("&", "§"));
            return true;
        }
        if (!instance.vaults.contains(args[1])) {
            sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.FailCurrency").replace("&", "§"));
            return true;
        }
        return false;
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
            sender.sendMessage(instance.prefix + "§c请输入玩家名！");
            return true;
        }
        if (args.length == 2) {
            sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.FailType").replace("&", "§"));
            return true;
        }
        if (args.length == 3) {
            sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.FailAmount").replace("&", "§"));
            return true;
        }
        if (!instance.vaults.contains(args[2])) {
            sender.sendMessage(instance.prefix + instance.getConfig().getString("Message.FailCurrency").replace("&", "§"));
            return true;
        }
        return false;
    }

    /**
     * 数据迁移命令
     */
    private void migrateData(CommandSender sender, String[] args) {
        if (!sender.hasPermission("nye.admin")) {
            sender.sendMessage(Main.getString("Message.NoHasPermission", true));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("§c用法: /nye migrate <from> <to>");
            sender.sendMessage("§c示例: /nye migrate yaml sqlite");
            sender.sendMessage("§c支持迁移:");
            sender.sendMessage("§c  源: yaml, mysql_old");
            sender.sendMessage("§c  目标: sqlite, mysql");
            return;
        }

        String from = args[1];
        String to = args[2];

        sender.sendMessage("§e======== 开始数据迁移 ========");
        sender.sendMessage("§e源: " + from + " -> 目标: " + to);
        sender.sendMessage("§e请稍等,迁移过程中请勿操作服务器...");

        AyCore.getPlatformApi().runTaskAsynchronously(instance, () -> {
            MigrationHandler handler = new MigrationHandler(Main.getInstance());
            MigrationHandler.MigrationResult result = handler.migrate(from, to);

            AyCore.getPlatformApi().runTask(instance, () -> {
                if (result.success) {
                    sender.sendMessage("§a======== 迁移完成 ========");
                    sender.sendMessage("§a" + result.message);
                    sender.sendMessage("§a总记录: " + result.totalRecords);
                    sender.sendMessage("§a成功: " + result.successCount + ", 失败: " + result.failCount);
                    sender.sendMessage("§e建议重启服务器以应用更改！");
                } else {
                    sender.sendMessage("§c======== 迁移失败 ========");
                    sender.sendMessage("§c失败原因: " + result.message);
                    sender.sendMessage("§c请检查配置文件和控制台日志");
                }
            });
        });
    }

    private void migrateUUID(CommandSender sender) {
        if (!sender.hasPermission("nye.admin")) {
            sender.sendMessage(Main.getString("Message.NoHasPermission", true));
            return;
        }
        
        sender.sendMessage("§e开始 UUID 迁移...");
        
        AyCore.getPlatformApi().runTaskAsynchronously(instance, () -> {
            try {
                String dbType = Main.getInstance().getConfig().getString("data-option.type", "sqlite");
                Connection connection = null;
                
                if ("mysql".equalsIgnoreCase(dbType)) {
                    AyCore.getPlatformApi().runTask(instance, () -> sender.sendMessage("§cMySQL 模式暂不支持自动迁移,请手动操作"));
                    return;
                }
                
                if ("sqlite".equalsIgnoreCase(dbType)) {
                    Class.forName("org.sqlite.JDBC");
                    String dbPath = new java.io.File(Main.getInstance().getDataFolder(), "data/economy.db").getAbsolutePath();
                    connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
                }
                
                if (connection == null) {
                    AyCore.getPlatformApi().runTask(instance, () -> sender.sendMessage("§c无法获取数据库连接"));
                    return;
                }
                
                com.mc9y.nyeconomy.migration.UUIDMigrationHandler handler = new com.mc9y.nyeconomy.migration.UUIDMigrationHandler();
                com.mc9y.nyeconomy.migration.UUIDMigrationHandler.MigrationResult result = handler.migrateToUUID(connection);
                
                connection.close();
                
                AyCore.getPlatformApi().runTask(instance, () -> {
                    if (result.success) {
                        sender.sendMessage("§a迁移成功!");
                        sender.sendMessage("§a总玩家数: " + result.totalPlayers);
                        sender.sendMessage("§a成功: " + result.successCount + ", 失败: " + result.failCount);
                        sender.sendMessage("§e请重启服务器以应用更改!");
                    } else {
                        sender.sendMessage("§c迁移失败: " + result.message);
                    }
                });
            } catch (Exception e) {
                Main.getInstance().getLogger().log(Level.SEVERE, "UUID 迁移失败", e);
                AyCore.getPlatformApi().runTask(instance, () -> sender.sendMessage("§c迁移失败: " + e.getMessage()));
            }
        });
    }
}