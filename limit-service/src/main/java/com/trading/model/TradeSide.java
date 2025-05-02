package com.trading.model;

public enum TradeSide {
    BUY("Buy", "Buying order"),
    SELL("Sell", "Selling order");

    private final String code;
    private final String description;

    TradeSide(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
} 