package com.trading.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.UUID;

@Document(collection = "desk_limits")
public record DeskLimits (
    @Id
    UUID id,  // Same as desk ID
    
    @NotNull(message = "Desk ID is required")
    UUID deskId,
    
    @Min(value = 0, message = "Buy notional limit must be non-negative")
    double buyNotionalLimit,
    
    @Min(value = 0, message = "Sell notional limit must be non-negative")
    double sellNotionalLimit,
    
    @Min(value = 0, message = "Gross notional limit must be non-negative")
    double grossNotionalLimit
) {
}