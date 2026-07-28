package com.mc9y.nyeconomy.util;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class MoneyTest {
    @Test
    public void keepsExactTwoDecimalArithmetic() {
        BigDecimal result = Money.normalize(new BigDecimal("0.10").add(new BigDecimal("0.20")));
        assertEquals(new BigDecimal("0.30"), result);
        assertEquals("12.50", Money.format(new BigDecimal("12.5")));
    }

    @Test
    public void convertsSQLiteCentsWithoutPrecisionLoss() {
        BigDecimal amount = new BigDecimal("2147483647.99");
        assertEquals(214748364799L, Money.toCents(amount));
        assertEquals(amount, Money.fromCents(214748364799L));
    }

    @Test
    public void roundsOnlyAtVaultBoundary() {
        assertEquals(new BigDecimal("0.01"), Money.fromVault(0.005D));
        assertEquals(new BigDecimal("1.24"), Money.fromVault(1.235D));
    }

    @Test
    public void legacyGetterTruncatesFraction() {
        assertEquals(12, Money.toLegacyInt(new BigDecimal("12.99")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void commandRejectsMoreThanTwoDecimals() {
        Money.parseCommand("1.001");
    }

    @Test(expected = IllegalArgumentException.class)
    public void commandRejectsScientificNotation() {
        Money.parseCommand("1E+3");
    }

    @Test
    public void signedCommandAcceptsNegativeAmount() {
        assertEquals(new BigDecimal("-12.34"), Money.parseSignedCommand("-12.34"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void regularCommandStillRejectsNegativeAmount() {
        Money.parseCommand("-12.34");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsBalanceAboveLegacySafeRange() {
        Money.normalize(new BigDecimal("2147483648.00"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void vaultRejectsPositiveSubCentAmount() {
        Money.fromVault(0.004D);
    }

    @Test(expected = IllegalArgumentException.class)
    public void vaultRejectsNegativeAmountBeforeRounding() {
        Money.fromVault(-0.004D);
    }
}
