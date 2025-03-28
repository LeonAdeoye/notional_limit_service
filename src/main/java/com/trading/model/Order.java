package com.trading.model;

import java.time.LocalDate;
import java.util.UUID;

public record Order (
    UUID id,
    UUID traderId,
    String symbol,
    int quantity,
    double price,
    TradeSide side,
    Currency currency,
    LocalDate tradeDate ) {
    
    public double getNotionalValue() {
        return quantity * price;
    }
} 