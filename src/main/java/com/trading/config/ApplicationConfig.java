package com.trading.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.crankuptheamps.client.Client;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration class for application-wide beans.
 */
@Configuration
public class ApplicationConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Configure ObjectMapper as needed
        return mapper;
    }

    @Bean
    public Client ampsClient() {
        return new Client("TradingSystem");
    }
} 