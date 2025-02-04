package com.trading.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.UUID;

@Data
@Document(collection = "desk_limits")
public class DeskLimits {
    @Id
    private UUID id;  // Same as desk ID
    
    @NotNull(message = "Desk ID is required")
    private UUID deskId;
    
    @Min(value = 0, message = "Buy notional limit must be non-negative")
    private double buyNotionalLimit;
    
    @Min(value = 0, message = "Sell notional limit must be non-negative")
    private double sellNotionalLimit;
    
    @Min(value = 0, message = "Gross notional limit must be non-negative")
    private double grossNotionalLimit;
    
    private double currentBuyNotional;
    private double currentSellNotional;
    
    public double getCurrentGrossNotional() {
        return currentBuyNotional + currentSellNotional;
    }
    
    public double getBuyUtilizationPercentage() {
        return (currentBuyNotional / buyNotionalLimit) * 100;
    }
    
    public double getSellUtilizationPercentage() {
        return (currentSellNotional / sellNotionalLimit) * 100;
    }
    
    public double getGrossUtilizationPercentage() {
        return (getCurrentGrossNotional() / grossNotionalLimit) * 100;
    }
} 