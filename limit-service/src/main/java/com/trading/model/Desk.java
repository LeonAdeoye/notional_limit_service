package com.trading.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@Document("Desk")
public class Desk {
    @Id
    private UUID deskId;
    private String deskName;
    private List<UUID> traders;

    public Desk() {
        this.deskId = UUID.randomUUID();
        this.deskName = "";
        this.traders = new ArrayList<>();
    }

    public UUID getDeskId() {
        return deskId;
    }

    public void setDeskId(UUID deskId) {
        this.deskId = deskId;
    }

    public String getDeskName() {
        return deskName;
    }

    public void setDeskName(String deskName) {
        this.deskName = deskName;
    }

    public List<UUID> getTraders() {
        return traders;
    }

    public void setTraders(List<UUID> traders) {
        this.traders = traders;
    }

    @Override
    public String toString() {
        return "Desk{" +
                "deskId=" + deskId +
                ", deskName='" + deskName + '\'' +
                ", traders=" + traders +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Desk)) return false;
        Desk desk = (Desk) o;
        return deskId.equals(desk.deskId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getDeskId(), getDeskName(), getTraders());
    }
}
