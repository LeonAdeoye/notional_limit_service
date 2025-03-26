package com.trading.model;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.UUID;

@Document(collection = "traders")
public record Trader (
    @Id
    UUID id,
    @NotBlank(message = "Trader name is required")
    String name,
    @NotNull(message = "Desk ID is required")
    UUID deskId) {
    public Trader(String name, UUID deskId) {
        this(UUID.randomUUID(), name, deskId);
    }

}