package com.trading.validation;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ValidationResult {
    private final boolean valid;
    private final String errorMessage;
} 