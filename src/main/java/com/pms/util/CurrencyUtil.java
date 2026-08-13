package com.pms.util;

public class CurrencyUtil {

    private static final String DEFAULT_CURRENCY = "$";

    public static String getSymbol() {
        return AppPrefs.get("currency_symbol", DEFAULT_CURRENCY);
    }

    public static String format(double amount) {
        return String.format("%s%.2f", getSymbol(), amount);
    }
}
