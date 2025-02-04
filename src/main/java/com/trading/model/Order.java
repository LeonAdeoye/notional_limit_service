package com.trading.model;

import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class Order {
    private UUID id;
    private UUID traderId;
    private String symbol;
    private int quantity;
    private double price;
    private TradeSide side;
    private Currency currency;
    private LocalDate tradeDate;
    
    public double getNotionalValue() {
        return quantity * price;
    }
} 