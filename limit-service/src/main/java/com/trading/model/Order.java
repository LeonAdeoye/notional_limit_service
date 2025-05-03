package com.trading.model;

import java.time.LocalDateTime;
import java.util.UUID;

public record Order (
    UUID id,
    UUID traderId,
    String symbol,
    int quantity,
    double price,
    TradeSide side,
    Currency currency,
    LocalDateTime tradeTimestamp ) {
    
    public double getNotionalValue() {
        return quantity * price;
    }
} 