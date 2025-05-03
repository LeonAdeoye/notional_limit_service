package com.trading.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.UUID;

public record Order(
        UUID id,
        UUID traderId,
        String symbol,
        int quantity,
        double price,
        TradeSide side,
        Currency currency,
//        @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
//        @JsonSerialize(using = LocalDateTimeSerializer.class)
//        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        LocalDateTime tradeTimestamp
) {}