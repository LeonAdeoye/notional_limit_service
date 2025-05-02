package com.trading.controller;

import com.trading.service.CurrencyManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.MDC;
import java.util.Map;
import java.util.UUID;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import com.trading.model.Currency;

/**
 * REST Controller for managing FX rates.
 * Provides endpoints for querying and updating currency exchange rates.
 * Supports both bulk and individual rate updates.
 */
@RestController
@RequestMapping("/api/v1/currency")
@RequiredArgsConstructor
public class CurrencyController {
    private static final Logger log = LoggerFactory.getLogger(CurrencyController.class);
    
    @Autowired
    private final CurrencyManager currencyManager;
    
    /**
     * Retrieves all current FX rates.
     * 
     * @return Map of currency codes to their USD exchange rates
     */
    @GetMapping("/rates")
    public ResponseEntity<Map<Currency, Double>> getCurrentRates() {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            return ResponseEntity.ok(currencyManager.getCurrentRates());
        } catch (Exception e) {
            log.error("ERR-431: Error retrieving current rates", e);
            return ResponseEntity.internalServerError().build();
        } finally {
            MDC.remove("errorId");
        }
    }
    
    /**
     * Updates multiple FX rates in bulk.
     * 
     * @param rates Map of currency codes to their new USD exchange rates
     * @return 200 OK if successful, 500 if update fails
     */
    @PostMapping("/rates")
    public ResponseEntity<Void> updateRates(@Valid @RequestBody Map<Currency, Double> rates) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            rates.forEach(currencyManager::updateRate);
            log.info("Successfully updated {} currency rates", rates.size());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("ERR-432: Error updating currency rates", e);
            return ResponseEntity.internalServerError().build();
        } finally {
            MDC.remove("errorId");
        }
    }
    
    @PutMapping("/rates/{currency}")
    public ResponseEntity<Void> updateRate(
            @PathVariable Currency currency,
            @RequestParam Double rate) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            currencyManager.updateRate(currency, rate);
            log.info("Successfully updated rate for currency: {}", currency);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("ERR-433: Error updating rate for currency: {}", currency, e);
            return ResponseEntity.internalServerError().build();
        } finally {
            MDC.remove("errorId");
        }
    }
    
    @GetMapping("/rates/{currency}")
    public ResponseEntity<Double> getRate(@PathVariable Currency currency) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            if (!currencyManager.hasRate(currency)) {
                log.error("ERR-434: Rate not found for currency: {}", currency);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(currencyManager.getRate(currency));
        } catch (Exception e) {
            log.error("ERR-435: Error retrieving rate for currency: {}", currency, e);
            return ResponseEntity.internalServerError().build();
        } finally {
            MDC.remove("errorId");
        }
    }
} 