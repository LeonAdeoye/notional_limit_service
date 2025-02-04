package com.trading.model;

/**
 * Enum representing the side of a trade.
 * Used to indicate whether an order is buying or selling.
 */
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