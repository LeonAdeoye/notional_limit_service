package com.trading.model;

public enum Currency {
    USD("US Dollar"),
    EUR("Euro"),
    GBP("British Pound"),
    JPY("Japanese Yen"),
    CHF("Swiss Franc"),
    AUD("Australian Dollar"),
    CAD("Canadian Dollar"),
    HKD("Hong Kong Dollar"),
    SGD("Singapore Dollar"),
    KRW("South Korean Won");

    private final String description;

    Currency(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
} 