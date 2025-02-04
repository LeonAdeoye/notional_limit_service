package com.trading.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import java.util.UUID;

@Data
@Document(collection = "traders")
public class Trader {
    @Id
    private UUID id;
    
    @NotBlank(message = "Trader name is required")
    private String name;
    
    @NotNull(message = "Desk ID is required")
    private UUID deskId;
} 