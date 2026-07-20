package com.mc9y.nyeconomy.util;

import java.util.Locale;

public final class MoneyFormatter {
    private MoneyFormatter() {
    }

    public static String format(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    public static String formatFloor(double amount) {
        return String.valueOf((long) Math.floor(amount));
    }
}
