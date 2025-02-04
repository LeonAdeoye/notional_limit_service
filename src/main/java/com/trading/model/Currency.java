package com.trading.model;

/**
 * Enum representing supported currencies in the trading system.
 * Each currency includes its standard ISO code and description.
 */
public enum Currency {
    USD("US Dollar"),
    EUR("Euro"),
    GBP("British Pound"),
    JPY("Japanese Yen"),
    CHF("Swiss Franc"),
    AUD("Australian Dollar"),
    CAD("Canadian Dollar"),
    HKD("Hong Kong Dollar"),
    SGD("Singapore Dollar");

    private final String description;

    Currency(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
} 