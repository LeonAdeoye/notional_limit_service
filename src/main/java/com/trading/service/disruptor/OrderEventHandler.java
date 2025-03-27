package com.trading.service.disruptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lmax.disruptor.EventHandler;
import com.trading.messaging.AmpsMessageOutboundProcessor;
import com.trading.model.Desk;
import com.trading.model.Order;
import com.trading.model.Trader;
import com.trading.model.TradeSide;
import com.trading.service.CurrencyManager;
import com.trading.service.TradingPersistenceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * Event handler for processing order events in the Disruptor.
 * Handles the actual business logic of processing orders and updating limits.
 * Ensures sequential processing of orders for thread safety.
 */
@Component
@RequiredArgsConstructor
public class OrderEventHandler implements EventHandler<OrderEvent> {
    private static final Logger log = LoggerFactory.getLogger(OrderEventHandler.class);
    @Autowired
    private final TradingPersistenceService persistenceService;
    @Autowired
    private final CurrencyManager currencyManager;
    @Autowired
    private final AmpsMessageOutboundProcessor ampsMessageOutboundProcessor;
    ObjectMapper objectMapper = new ObjectMapper();
    /**
     * Main event handling method called by the Disruptor.
     * Processes each order event in sequence.
     */
    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        try {
            // Set error context for logging
            MDC.put("errorId", event.getErrorId());
            processOrder(event.getOrder());
        } finally {
            // Clean up logging context
            MDC.remove("errorId");
        }
    }

    /**
     * Processes a single order by validating trader/desk and updating limits.
     * @throws IllegalArgumentException if trader or desk not found
     */
    private void processOrder(Order order) {
        // Validate and retrieve trader
        Trader trader = persistenceService.getTrader(order.traderId());
        if (trader == null) {
            log.error("ERR-001: Trader not found with ID: {}", order.traderId());
            throw new IllegalArgumentException("Trader not found");
        }

        // Validate and retrieve desk
        Desk desk = persistenceService.getDesk(trader.deskId());
        if (desk == null) {
            log.error("ERR-002: Desk not found with ID: {}", trader.deskId());
            throw new IllegalArgumentException("Desk not found");
        }

        // Calculate notional value and update limits
        double notionalValueUSD = calculateUSDNotional(order);
        updateDeskLimits(desk, order.side(), notionalValueUSD);
        checkLimitBreaches(desk, order.side());
        
        // Persist updated desk state
        persistenceService.saveDesk(desk);
        log.info("Successfully processed order for trader: {}, desk: {}", trader.id(), desk.getId());
    }

    /**
     * Converts order notional value to USD using current FX rates.
     */
    private double calculateUSDNotional(Order order) {
        double localNotional = order.getNotionalValue();
        return currencyManager.convertToUSD(localNotional, order.currency());
    }

    /**
     * Updates desk limits based on order side and notional value.
     */
    private void updateDeskLimits(Desk desk, TradeSide side, double notionalValueUSD) {
        if (side == TradeSide.BUY) {
            desk.setCurrentBuyNotional(desk.getCurrentBuyNotional() + notionalValueUSD);
            log.debug("Updated buy notional for desk: {} to: {} using: {} USD", desk.getId(), desk.getCurrentBuyNotional(), notionalValueUSD);
        } else if (side == TradeSide.SELL) {
            desk.setCurrentSellNotional(desk.getCurrentSellNotional() + notionalValueUSD);
            log.debug("Updated sell notional for desk: {} to: {} using: {} USD", desk.getId(), desk.getCurrentSellNotional(), notionalValueUSD);
        }
        desk.setGrossNotionalLimit(desk.getGrossNotionalLimit() + notionalValueUSD);
        log.debug("Updated gross notional limit for desk: {} to: {} using: {} USD", desk.getId(), desk.getGrossNotionalLimit(), notionalValueUSD);

        publishNotionalUpdate(desk, side, notionalValueUSD);
    }

    private void publishNotionalUpdate(Desk desk, TradeSide side, double notionalValueUSD) {
        try {
            Map<String, Object> updateDetails = new HashMap<>();
            updateDetails.put("deskId", desk.getId());
            updateDetails.put("deskName", desk.getName());
            updateDetails.put("side", side);
            updateDetails.put("notionalValueUSD", notionalValueUSD);
            if (side == TradeSide.BUY) {
                updateDetails.put("currentBuyNotional", desk.getCurrentBuyNotional());
            } else if (side == TradeSide.SELL) {
                updateDetails.put("currentSellNotional", desk.getCurrentSellNotional());
            }
            updateDetails.put("grossNotionalLimit", desk.getGrossNotionalLimit());

            String message = objectMapper.writeValueAsString(updateDetails);
            ampsMessageOutboundProcessor.publishLimitBreach(message);
            log.info("Published notional update message: {}", message);
        } catch (Exception e) {
            log.error("Failed to publish notional update message", e);
        }
    }

    /**
     * Checks for and publishes any limit breaches.
     * Monitors buy, sell, and gross notional limits.
     */

    private void checkLimitBreaches(Desk desk, TradeSide side) {
        // Check buy limit breach
        if (desk.getBuyUtilizationPercentage() > 100) {
            String message = createBreachMessage(objectMapper, "ERR-004", "Buy limit breached", desk, TradeSide.BUY);
            log.error(message);
            ampsMessageOutboundProcessor.publishLimitBreach(message);
        }

        // Check sell limit breach
        if (desk.getSellUtilizationPercentage() > 100) {
            String message = createBreachMessage(objectMapper, "ERR-005", "Sell limit breached", desk, TradeSide.SELL);
            log.error(message);
            ampsMessageOutboundProcessor.publishLimitBreach(message);
        }

        // Check gross limit breach
        if (desk.getGrossUtilizationPercentage() > 100) {
            String message = createBreachMessage(objectMapper, "ERR-006", "Gross limit breached", desk, side);
            log.error(message);
            ampsMessageOutboundProcessor.publishLimitBreach(message);
        }
    }

    private String createBreachMessage(ObjectMapper objectMapper, String errorCode, String errorMessage, Desk desk, TradeSide side) {
        try {
            Map<String, Object> breachDetails = new HashMap<>();
            breachDetails.put("errorCode", errorCode);
            breachDetails.put("errorMessage", errorMessage);
            breachDetails.put("deskId", desk.getId());
            breachDetails.put("deskName", desk.getName());
            if(side.getCode().equals(TradeSide.BUY)) {
                breachDetails.put("currentBuyNotional", desk.getCurrentBuyNotional());
                breachDetails.put("buyUtilizationPercentage", desk.getBuyUtilizationPercentage());
            } else {
                breachDetails.put("currentSellNotional", desk.getCurrentSellNotional());
                breachDetails.put("sellUtilizationPercentage", desk.getSellUtilizationPercentage());
            }
            breachDetails.put("grossUtilizationPercentage", desk.getGrossUtilizationPercentage());
            return objectMapper.writeValueAsString(breachDetails);
        } catch (Exception e) {
            log.error("Failed to create breach message", e);
            return "{}";
        }
    }
} 