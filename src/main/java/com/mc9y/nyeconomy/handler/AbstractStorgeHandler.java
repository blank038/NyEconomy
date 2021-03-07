package com.mc9y.nyeconomy.handler;

/**
 * @author Blank038
 * @since 2021-03-07
 */
public abstract class AbstractStorgeHandler {
    private static AbstractStorgeHandler INSTANCE;

    public abstract void balance(String name, String type);

    public abstract void deposit(String name, String type, int amount);

    public abstract void withdraw(String name, String type, int amount);

    public abstract void set(String name, String type, int amount);

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