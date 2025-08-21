package com.trading.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import javax.validation.constraints.Min;
import java.util.UUID;

@Data
@Document(collection = "desks")
public class DeskNotionalLimit {
    @Id
    private UUID deskId;
    @Min(value = 0, message = "Buy notional limit must be non-negative")
    private double buyNotionalLimit;
    @Min(value = 0, message = "Sell notional limit must be non-negative")
    private double sellNotionalLimit;
    @Min(value = 0, message = "Gross notional limit must be non-negative")
    private double grossNotionalLimit;
    private double currentBuyNotional;
    private double currentSellNotional;
    private double currentGrossNotional;

    public DeskNotionalLimit()
    {
        this.deskId = UUID.randomUUID();
        this.buyNotionalLimit = 0.0;
        this.sellNotionalLimit = 0.0;
        this.grossNotionalLimit = 0.0;
        this.currentBuyNotional = 0.0;
        this.currentSellNotional = 0.0;
        this.currentGrossNotional = 0.0;
    }

    public DeskNotionalLimit(UUID deskId)
    {
        this.deskId = deskId;
        this.buyNotionalLimit = 0.0;
        this.sellNotionalLimit = 0.0;
        this.grossNotionalLimit = 0.0;
        this.currentBuyNotional = 0.0;
        this.currentSellNotional = 0.0;
        this.currentGrossNotional = 0.0;
    }

    public DeskNotionalLimit(UUID deskId, double buyNotionalLimit, double sellNotionalLimit, double grossNotionalLimit)
    {
        this.deskId = deskId;
        this.buyNotionalLimit = buyNotionalLimit;
        this.sellNotionalLimit = sellNotionalLimit;
        this.grossNotionalLimit = grossNotionalLimit;
    }
    
    public double getBuyUtilizationPercentage()
    {
        return (currentBuyNotional / buyNotionalLimit) * 100;
    }
    
    public double getSellUtilizationPercentage()
    {
        return (currentSellNotional / sellNotionalLimit) * 100;
    }
    
    public double getGrossUtilizationPercentage()
    {
        return (currentGrossNotional / grossNotionalLimit) * 100;
    }
} 