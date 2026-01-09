package com.mc9y.nyeconomy.migration;

import com.mc9y.nyeconomy.Main;
import com.mc9y.nyeconomy.service.UUIDService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * 数据迁移处理器
 * 支持将旧的 YAML 或旧版 MySQL 数据迁移到新的数据库结构
 * 
 * @author Blank038
 */
public class MigrationHandler {
    private final Main plugin;
    private final Map<String, Map<String, Integer>> migrationData = new HashMap<>();

    public MigrationHandler(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * 执行迁移
     * @param from 源类型: yaml, mysql_old
     * @param to 目标类型: sqlite, mysql
     * @return 迁移结果
     */
    public MigrationResult migrate(String from, String to) {
        migrationData.clear();
        MigrationResult result = new MigrationResult();
        
        try {
            // 1. 读取源数据
            plugin.getLogger().info("======== 开始数据迁移 ========");
            plugin.getLogger().info("源: " + from + " -> 目标: " + to);
            
            boolean loadSuccess = false;
            if ("yaml".equalsIgnoreCase(from)) {
                loadSuccess = loadFromYaml(result);
            } else if ("mysql_old".equalsIgnoreCase(from)) {
                loadSuccess = loadFromOldMySQL(result);
            } else {
                result.success = false;
                result.message = "不支持的源类型: " + from;
                return result;
            }
            
            if (!loadSuccess) {
                result.success = false;
                result.message = "读取源数据失败";
                return result;
            }
            
            // 2. 写入目标数据库
            boolean writeSuccess = false;
            if ("sqlite".equalsIgnoreCase(to)) {
                writeSuccess = writeToSqlite(result);
            } else if ("mysql".equalsIgnoreCase(to)) {
                writeSuccess = writeToMySQL(result);
            } else {
                result.success = false;
                result.message = "不支持的目标类型: " + to;
                return result;
            }
            
            if (!writeSuccess) {
                result.success = false;
                result.message = "写入目标数据库失败";
                return result;
            }
            
            result.success = true;
            result.message = String.format("迁移完成! 成功: %d, 失败: %d", 
                result.successCount, result.failCount);
            
            plugin.getLogger().info("======== 迁移完成 ========");
            plugin.getLogger().info(result.message);
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "迁移过程出现异常", e);
            result.success = false;
            result.message = "迁移异常: " + e.getMessage();
        }
        
        return result;
    }

    /**
     * 从 YAML 文件加载数据
     */
    private boolean loadFromYaml(MigrationResult result) {
        File currenciesDir = new File(plugin.getDataFolder(), "Currencies");
        if (!currenciesDir.exists() || !currenciesDir.isDirectory()) {
            plugin.getLogger().warning("Currencies 目录不存在");
            return false;
        }
        
        File[] files = currenciesDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("没有找到 YAML 货币文件");
            return false;
        }
        
        plugin.getLogger().info("找到 " + files.length + " 个货币文件");
        
        for (File file : files) {
            String currencyType = file.getName().replace(".yml", "");
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            
            for (String playerName : config.getKeys(false)) {
                int balance = config.getInt(playerName, 0);
                
                migrationData.computeIfAbsent(playerName, k -> new HashMap<>())
                             .put(currencyType, balance);
                result.totalRecords++;
            }
            
            plugin.getLogger().info("加载货币类型: " + currencyType + 
                " (玩家数: " + config.getKeys(false).size() + ")");
        }
        
        plugin.getLogger().info("总共加载 " + result.totalRecords + " 条记录");
        return true;
    }

    /**
     * 从旧版 MySQL (JSON 格式) 加载数据
     */
    private boolean loadFromOldMySQL(MigrationResult result) {
        try {
            String url = plugin.getConfig().getString("data-option.mysql.url");
            String user = plugin.getConfig().getString("data-option.mysql.user");
            String password = plugin.getConfig().getString("data-option.mysql.password");
            
            try (Connection conn = DriverManager.getConnection(url, user, password);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT user, data FROM ny_economy")) {
                
                while (rs.next()) {
                    String playerName = rs.getString("user");
                    String jsonData = rs.getString("data");
                    
                    // 简单的 JSON 解析 (假设格式: {"currencys":[{"type":"金币","count":100}]})
                    Map<String, Integer> balances = parseOldJsonData(jsonData);
                    if (!balances.isEmpty()) {
                        migrationData.put(playerName, balances);
                        result.totalRecords += balances.size();
                    }
                }
                
                plugin.getLogger().info("从旧版 MySQL 加载 " + result.totalRecords + " 条记录");
                return true;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "从旧版 MySQL 加载数据失败", e);
            return false;
        }
    }

    /**
     * 简单的 JSON 解析 (仅用于迁移)
     */
    private Map<String, Integer> parseOldJsonData(String jsonData) {
        Map<String, Integer> result = new HashMap<>();
        if (jsonData == null || jsonData.isEmpty()) {
            return result;
        }
        
        try {
            // 简单解析: {"currencys":[{"type":"金币","count":100}]}
            int currencysIndex = jsonData.indexOf("\"currencys\"");
            if (currencysIndex == -1) return result;
            
            int arrayStart = jsonData.indexOf("[", currencysIndex);
            int arrayEnd = jsonData.lastIndexOf("]");
            if (arrayStart == -1 || arrayEnd == -1) return result;
            
            String arrayContent = jsonData.substring(arrayStart + 1, arrayEnd);
            String[] items = arrayContent.split("\\},\\{");
            
            for (String item : items) {
                item = item.replace("{", "").replace("}", "");
                String[] pairs = item.split(",");
                
                String type = null;
                Integer count = null;
                
                for (String pair : pairs) {
                    String[] kv = pair.split(":");
                    if (kv.length != 2) continue;
                    
                    String key = kv[0].trim().replace("\"", "");
                    String value = kv[1].trim().replace("\"", "");
                    
                    if ("type".equals(key)) {
                        type = value;
                    } else if ("count".equals(key)) {
                        try {
                            count = Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            // 忽略
                        }
                    }
                }
                
                if (type != null && count != null) {
                    result.put(type, count);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("解析 JSON 数据失败: " + jsonData);
        }
        
        return result;
    }

    /**
     * 写入 SQLite
     */
    private boolean writeToSqlite(MigrationResult result) {
        File dataDir = new File(plugin.getDataFolder(), "data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        
        String dbPath = dataDir.getAbsolutePath() + File.separator + "economy.db";
        
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
            conn.setAutoCommit(false);
            
            String playerSql = "INSERT OR REPLACE INTO nyeconomy_players (player_uuid, player_name, last_seen) VALUES (?, ?, ?)";
            String balanceSql = "INSERT OR REPLACE INTO nyeconomy_balances (player_uuid, currency_type, balance, last_updated) " +
                        "VALUES (?, ?, ?, ?)";
            
            try (PreparedStatement playerPs = conn.prepareStatement(playerSql);
                 PreparedStatement balancePs = conn.prepareStatement(balanceSql)) {
                long now = System.currentTimeMillis();
                int batchCount = 0;
                
                for (Map.Entry<String, Map<String, Integer>> playerEntry : migrationData.entrySet()) {
                    String playerName = playerEntry.getKey();
                    UUID playerUUID = UUIDService.getInstance().getPlayerUUID(playerName);
                    
                    playerPs.setString(1, playerUUID.toString());
                    playerPs.setString(2, playerName);
                    playerPs.setLong(3, now);
                    playerPs.addBatch();
                    
                    for (Map.Entry<String, Integer> balanceEntry : playerEntry.getValue().entrySet()) {
                        String currencyType = balanceEntry.getKey();
                        int balance = balanceEntry.getValue();
                        
                        try {
                            balancePs.setString(1, playerUUID.toString());
                            balancePs.setString(2, currencyType);
                            balancePs.setInt(3, balance);
                            balancePs.setLong(4, now);
                            balancePs.addBatch();
                            
                            batchCount++;
                            
                            if (batchCount >= 1000) {
                                playerPs.executeBatch();
                                balancePs.executeBatch();
                                conn.commit();
                                result.successCount += batchCount;
                                batchCount = 0;
                                
                                plugin.getLogger().info("已迁移 " + result.successCount + " / " + result.totalRecords + " 条记录");
                            }
                        } catch (SQLException e) {
                            plugin.getLogger().warning("写入失败: " + playerName + " - " + currencyType + ": " + e.getMessage());
                            result.failCount++;
                        }
                    }
                }
                
                if (batchCount > 0) {
                    playerPs.executeBatch();
                    balancePs.executeBatch();
                    conn.commit();
                    result.successCount += batchCount;
                }
                
                plugin.getLogger().info("成功写入 SQLite: " + result.successCount + " 条记录");
                return true;
            } catch (SQLException e) {
                conn.rollback();
                plugin.getLogger().log(Level.SEVERE, "SQLite 事务回滚", e);
                return false;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "连接 SQLite 失败", e);
            return false;
        }
    }

    /**
     * 写入 MySQL
     */
    private boolean writeToMySQL(MigrationResult result) {
        try {
            String url = plugin.getConfig().getString("data-option.mysql.url");
            String user = plugin.getConfig().getString("data-option.mysql.user");
            String password = plugin.getConfig().getString("data-option.mysql.password");
            
            try (Connection conn = DriverManager.getConnection(url, user, password)) {
                conn.setAutoCommit(false);
                
                String playerSql = "INSERT INTO nyeconomy_players (player_uuid, player_name, last_seen) " +
                            "VALUES (?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), last_seen = VALUES(last_seen)";
                String balanceSql = "INSERT INTO nyeconomy_balances (player_uuid, currency_type, balance, last_updated) " +
                            "VALUES (?, ?, ?, ?) " +
                            "ON DUPLICATE KEY UPDATE balance = VALUES(balance), last_updated = VALUES(last_updated)";
                
                try (PreparedStatement playerPs = conn.prepareStatement(playerSql);
                     PreparedStatement balancePs = conn.prepareStatement(balanceSql)) {
                    long now = System.currentTimeMillis();
                    int batchCount = 0;
                    
                    for (Map.Entry<String, Map<String, Integer>> playerEntry : migrationData.entrySet()) {
                        String playerName = playerEntry.getKey();
                        UUID playerUUID = UUIDService.getInstance().getPlayerUUID(playerName);
                        
                        playerPs.setString(1, playerUUID.toString());
                        playerPs.setString(2, playerName);
                        playerPs.setLong(3, now);
                        playerPs.addBatch();
                        
                        for (Map.Entry<String, Integer> balanceEntry : playerEntry.getValue().entrySet()) {
                            String currencyType = balanceEntry.getKey();
                            int balance = balanceEntry.getValue();
                            
                            try {
                                balancePs.setString(1, playerUUID.toString());
                                balancePs.setString(2, currencyType);
                                balancePs.setInt(3, balance);
                                balancePs.setLong(4, now);
                                balancePs.addBatch();
                                
                                batchCount++;
                                
                                if (batchCount >= 1000) {
                                    playerPs.executeBatch();
                                    balancePs.executeBatch();
                                    conn.commit();
                                    result.successCount += batchCount;
                                    batchCount = 0;
                                    
                                    plugin.getLogger().info("已迁移 " + result.successCount + " / " + result.totalRecords + " 条记录");
                                }
                            } catch (SQLException e) {
                                plugin.getLogger().warning("写入失败: " + playerName + " - " + currencyType + ": " + e.getMessage());
                                result.failCount++;
                            }
                        }
                    }
                    
                    if (batchCount > 0) {
                        playerPs.executeBatch();
                        balancePs.executeBatch();
                        conn.commit();
                        result.successCount += batchCount;
                    }
                    
                    plugin.getLogger().info("成功写入 MySQL: " + result.successCount + " 条记录");
                    return true;
                } catch (SQLException e) {
                    conn.rollback();
                    plugin.getLogger().log(Level.SEVERE, "MySQL 事务回滚", e);
                    return false;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "连接 MySQL 失败", e);
            return false;
        }
    }

    /**
     * 迁移结果
     */
    public static class MigrationResult {
        public boolean success = false;
        public String message = "";
        public int totalRecords = 0;
        public int successCount = 0;
        public int failCount = 0;
    }
}