package com.mc9y.nyeconomy.handler;

import com.mc9y.nyeconomy.data.AccountCache;

/**
 * @author Blank038
 * @since 2021-03-07
 */
@SuppressWarnings(value = {"UnusedReturnValue"})
public abstract class AbstractStorgeHandler {
    private static AbstractStorgeHandler INSTANCE;

    /**
     * 查询余额
     *
     * @param name   玩家名
     * @param type   货币类型
     * @param status 数据状态, 0: 无视、1: 不查询、2: 自动检测
     * @return 货币余额
     */
    public abstract int balance(String name, String type, int status);

    /**
     * 给予玩家货币
     *
     * @param name   玩家名
     * @param type   货币类型
     * @param amount 数量
     * @return 执行结果
     */
    public abstract boolean deposit(String name, String type, int amount);

    /**
     * 扣除玩家货币
     *
     * @param name   玩家名
     * @param type   货币类型
     * @param amount 数量
     * @return 执行结果
     */
    public abstract boolean withdraw(String name, String type, int amount);

    /**
     * 设置玩家货币
     *
     * @param name   玩家名
     * @param type   货币类型
     * @param amount 数量
     * @return 执行结果
     */
    public abstract boolean set(String name, String type, int amount);

    /**
     * 主动存储数据
     */
    public abstract void save();

    /**
     * 刷新金币排行
     */
    public abstract void refreshTop();

    public AccountCache getPlayerCache(String name) {
        return null;
    }

    public boolean isExists(String name) {
        return true;
    }

    public static void setHandler(Class<? extends AbstractStorgeHandler> handler) {
        try {
            INSTANCE = handler.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    public static AbstractStorgeHandler getHandler() {
        return INSTANCE;
    }
}