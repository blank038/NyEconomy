package com.mc9y.nyeconomy.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    public static final int SCALE = 2;
    public static final BigDecimal ZERO = new BigDecimal("0.00");
    public static final BigDecimal MAX_BALANCE = new BigDecimal("2147483647.99");

    private Money() {
    }

    public static BigDecimal of(int amount) {
        return BigDecimal.valueOf(amount).setScale(SCALE);
    }

    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("金额不能为空");
        }

        final BigDecimal normalized;
        try {
            normalized = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("金额最多保留两位小数", e);
        }

        if (normalized.signum() < 0) {
            throw new IllegalArgumentException("金额不能小于 0");
        }
        if (normalized.compareTo(MAX_BALANCE) > 0) {
            throw new IllegalArgumentException("金额超过最大值 " + MAX_BALANCE.toPlainString());
        }
        return normalized;
    }

    public static BigDecimal parseCommand(String input) {
        if (input == null || !input.matches("\\d+(?:\\.\\d{1,2})?")) {
            throw new IllegalArgumentException("金额格式错误");
        }
        try {
            return normalize(new BigDecimal(input));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("金额格式错误", e);
        }
    }

    public static BigDecimal parseConfig(Object input) {
        if (input == null) {
            throw new IllegalArgumentException("金额不能为空");
        }
        try {
            return normalize(new BigDecimal(String.valueOf(input)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("金额格式错误", e);
        }
    }

    public static BigDecimal fromVault(double amount) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            throw new IllegalArgumentException("金额必须是有限数字");
        }
        if (amount < 0.0D) {
            throw new IllegalArgumentException("金额不能小于 0");
        }
        BigDecimal normalized = normalize(BigDecimal.valueOf(amount).setScale(SCALE, RoundingMode.HALF_UP));
        if (amount != 0.0D && normalized.signum() == 0) {
            throw new IllegalArgumentException("金额小于最小单位 0.01");
        }
        return normalized;
    }

    public static int toLegacyInt(BigDecimal amount) {
        return normalize(amount).setScale(0, RoundingMode.DOWN).intValueExact();
    }

    public static int toWholeIntExact(BigDecimal amount) {
        return normalize(amount).intValueExact();
    }

    public static long toCents(BigDecimal amount) {
        return normalize(amount).movePointRight(SCALE).longValueExact();
    }

    public static BigDecimal fromCents(long amount) {
        return normalize(BigDecimal.valueOf(amount, SCALE));
    }

    public static String format(BigDecimal amount) {
        return normalize(amount).toPlainString();
    }
}
