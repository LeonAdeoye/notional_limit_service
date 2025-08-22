package com.trading.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.UUID;

@Data
@NoArgsConstructor
@Document(collection = "traderNotionalLimit")
public class TraderNotionalLimit {
    @Id
    private UUID traderId;
    private double currentBuyNotional;
    private double currentSellNotional;
    private double currentGrossNotional;

    public TraderNotionalLimit(UUID traderId)
    {
        this.traderId = traderId;
        this.currentBuyNotional = 0.0;
        this.currentSellNotional = 0.0;
        this.currentGrossNotional = 0.0;
    }

    public TraderNotionalLimit(String name, UUID deskId)
    {
        this.traderId = UUID.randomUUID();
        this.currentBuyNotional = 0.0;
        this.currentSellNotional = 0.0;
        this.currentGrossNotional = 0.0;
    }
}