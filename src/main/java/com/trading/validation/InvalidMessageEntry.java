package com.trading.validation;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class InvalidMessageEntry {
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private final LocalDateTime timestamp;
    private final String messageData;
    private final String errorMessage;
} 