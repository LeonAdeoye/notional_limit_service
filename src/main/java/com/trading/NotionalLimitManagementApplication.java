package com.trading;

import com.trading.service.CurrencyManager;
import com.trading.service.NotionalLimitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application class for the Trading System.
 * Enables component scanning, auto-configuration, and scheduling.
 * Implements CommandLineRunner to perform initialization on startup.
 */
@SpringBootApplication
@EnableScheduling  // For scheduled FX rate updates
public class NotionalLimitManagementApplication implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(NotionalLimitManagementApplication.class);
    
    @Autowired
    private CurrencyManager currencyManager;
    
    @Autowired
    private NotionalLimitService notionalLimitService;
    
    public static void main(String[] args) {
        SpringApplication.run(NotionalLimitManagementApplication.class, args);
    }

    /**
     * Runs on application startup.
     * Initializes the application by:
     * 1. Loading initial FX rates
     * 2. Starting the Disruptor
     * 3. Verifying system connectivity
     */
    @Override
    public void run(String... args) throws Exception {
        log.info("Initializing Trading Application...");
        
        try {
            // Initialize currency rates
            log.info("Loading initial FX rates...");
            currencyManager.initialize();
            
            // Verify Disruptor is running
            log.info("Verifying Disruptor status...");
            notionalLimitService.initialize();
            
            log.info("Trading Application initialized successfully");
            
        } catch (Exception e) {
            log.error("Failed to initialize Trading Application", e);
            // Throw the exception to prevent the application from starting
            throw new RuntimeException("Application initialization failed", e);
        }
    }
} 