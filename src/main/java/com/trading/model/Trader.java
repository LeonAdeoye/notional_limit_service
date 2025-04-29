package com.trading.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

@Data
@NoArgsConstructor
@Document(collection = "traders")
public class Trader {
    @Id
    private UUID id;
    @NotBlank(message = "Trader name is required")
    private String name;
    @NotNull(message = "Desk ID is required")
    private UUID deskId;

    private double currentBuyNotional;
    private double currentSellNotional;
    private double currentGrossNotional;

    public Trader(UUID traderId, String name, UUID deskId) {
        this.id = traderId;
        this.name = name;
        this.deskId = deskId;
    }

    public Trader(String name, UUID deskId) {
        this.name = name;
        this.deskId = deskId;
        this.id = UUID.randomUUID();
    }
}