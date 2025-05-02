package com.trading.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.trading.model.Currency;

@Service
@RequiredArgsConstructor
public class CurrencyManager {
    private static final Logger log = LoggerFactory.getLogger(CurrencyManager.class);
    private final Map<Currency, Double> fxRates = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        log.info("Initializing currency manager and loading FX rates");
        refreshFxRates();
    }
    
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void refreshFxRates() {
        try {
            updateRate(Currency.EUR, 1.18);
            updateRate(Currency.GBP, 1.40);
            updateRate(Currency.JPY, 0.0091);
            updateRate(Currency.HKD, 0.13);
            updateRate(Currency.SGD, 0.74);
            updateRate(Currency.AUD, 0.73);
            updateRate(Currency.USD, 1.0);
            updateRate(Currency.CAD, 0.75);
            updateRate(Currency.KRW, 0.00068);
            
            log.info("Successfully refreshed FX rates for {} currencies", fxRates.size());
        } catch (Exception e) {
            log.error("ERR-301: Failed to refresh FX rates", e);
        }
    }
    
    public double convertToUSD(double amount, Currency fromCurrency) {
        if (Currency.USD.equals(fromCurrency)) {
            return amount;
        }
        
        Double rate = fxRates.get(fromCurrency);
        if (rate == null  || rate == 0.0) {
            log.error("ERR-302: FX rate invalid for currency: {}", fromCurrency);
            throw new IllegalArgumentException("Invalid FX rate available for currency: " + fromCurrency);
        }
        
        return amount * rate;
    }
    
    public void updateRate(Currency currency, double rateToUSD) {
        if(rateToUSD <= 0) {
            log.error("ERR-303: Invalid FX rate: {} -> {}", currency, rateToUSD);
            throw new IllegalArgumentException("Invalid FX rate: " + currency + " -> " + rateToUSD);
        }
        Double oldRate = fxRates.put(currency, rateToUSD);
        if (oldRate == null || !oldRate.equals(rateToUSD)) {
            log.info("Updated FX rate for {}/USD: {} -> {}", 
                    currency, (oldRate == null ? 0 : oldRate), rateToUSD);
        }
    }
    
    public Double getRate(Currency currency) {
        return fxRates.get(currency);
    }
    
    public boolean hasRate(Currency currency) {
        return fxRates.containsKey(currency);
    }
    
    public Map<Currency, Double> getCurrentRates() {
        return new ConcurrentHashMap<>(fxRates);
    }
} 