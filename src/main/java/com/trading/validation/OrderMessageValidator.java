package com.trading.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.model.Order;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderMessageValidator {
    private static final Logger log = LoggerFactory.getLogger(OrderMessageValidator.class);
    private static final String INVALID_MESSAGES_DIR = "invalid_messages";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    
    private final ObjectMapper objectMapper;
    
    public ValidationResult validateMessage(String messageData) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            // Try to parse the message into an Order object
            Order order = objectMapper.readValue(messageData, Order.class);
            
            // Validate required fields
            StringBuilder errors = new StringBuilder();
            
            if (order.price() <= 0) {
                errors.append("Price must be positive. ");
            }
            
            if (order.quantity() <= 0) {
                errors.append("Quantity must be positive. ");
            }
            
            if (order.symbol() == null || order.symbol().trim().isEmpty()) {
                errors.append("Symbol is required. ");
            }
            
            if (order.traderId() == null) {
                errors.append("Trader ID is required. ");
            }
            
            if (order.side() == null) {
                errors.append("Trade side is required. ");
            }
            
            if (order.tradeDate() == null) {
                errors.append("Trade date is required. ");
            }
            
            if (order.currency() == null) {
                errors.append("Currency is required. ");
            }
            
            if (errors.length() > 0) {
                String errorMessage = errors.toString().trim();
                log.error("ERR-501: Invalid message format: {}", errorMessage);
                journalInvalidMessage(messageData, errorMessage);
                return new ValidationResult(false, errorMessage);
            }
            
            return new ValidationResult(true, null);
            
        } catch (Exception e) {
            String errorMessage = "Failed to parse message: " + e.getMessage();
            log.error("ERR-502: {}", errorMessage);
            journalInvalidMessage(messageData, errorMessage);
            return new ValidationResult(false, errorMessage);
        } finally {
            MDC.remove("errorId");
        }
    }
    
    private void journalInvalidMessage(String messageData, String errorMessage) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            // Create directory if it doesn't exist
            Files.createDirectories(Paths.get(INVALID_MESSAGES_DIR));
            
            // Create journal entry
            InvalidMessageEntry entry = new InvalidMessageEntry(
                LocalDateTime.now(),
                messageData,
                errorMessage
            );
            
            // Write to date-specific file
            String filename = String.format("%s/invalid_messages_%s.json",
                INVALID_MESSAGES_DIR,
                LocalDateTime.now().format(DATE_FORMAT));
                
            File file = new File(filename);
            boolean isNewFile = !file.exists();
            
            try (FileWriter writer = new FileWriter(file, true)) {
                if (!isNewFile) {
                    writer.write("\n");
                }
                writer.write(objectMapper.writeValueAsString(entry));
            }
            
            log.info("Journaled invalid message to file: {}", filename);
            
        } catch (Exception e) {
            log.error("ERR-503: Failed to journal invalid message", e);
        } finally {
            MDC.remove("errorId");
        }
    }
} 