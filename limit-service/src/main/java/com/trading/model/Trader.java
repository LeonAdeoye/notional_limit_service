package com.trading.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Objects;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@Document("Trader")
public class Trader {
    @Id
    private UUID traderId;
    private String firstName;
    private String lastName;
    private String userId;

    public Trader() {
        this.traderId = UUID.randomUUID();
        this.firstName = "";
        this.lastName = "";
        this.userId = "";
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public UUID getTraderId() {
        return traderId;
    }

    public void setTraderId(UUID traderId) {
        this.traderId = traderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return "Trader{" +
                "traderId=" + traderId +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", userId='" + userId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Trader)) return false;
        Trader trader = (Trader) o;
        return getTraderId().equals(trader.getTraderId()) && getFirstName().equals(trader.getFirstName()) && getLastName().equals(trader.getLastName()) && getUserId().equals(trader.getUserId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTraderId(), getFirstName(), getLastName(), getUserId());
    }
}

