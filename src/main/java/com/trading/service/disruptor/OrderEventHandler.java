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

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        try {
            MDC.put("errorId", event.getErrorId());
            processOrder(event.getOrder());
        } finally {
            MDC.remove("errorId");
        }
    }

    private void processOrder(Order order) {
        Trader trader = persistenceService.getTrader(order.traderId());
        if (trader == null) {
            log.error("ERR-001: Trader not found with ID: {}", order.traderId());
            throw new IllegalArgumentException("Trader not found");
        }

        Desk desk = persistenceService.getDesk(trader.deskId());
        if (desk == null) {
            log.error("ERR-002: Desk not found with ID: {}", trader.deskId());
            throw new IllegalArgumentException("Desk not found");
        }

        double notionalValueUSD = calculateUSDNotional(order);
        updateDeskLimits(desk, order.side(), notionalValueUSD);
        checkLimitBreaches(desk, order.side());

        persistenceService.saveDesk(desk);
        log.info("Successfully processed order for trader: {}, desk: {}", trader.id(), desk.getId());
    }

    private double calculateUSDNotional(Order order) {
        double localNotional = order.getNotionalValue();
        return currencyManager.convertToUSD(localNotional, order.currency());
    }

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

    private void checkLimitBreaches(Desk desk, TradeSide side) {
        boolean grossUtilizationLimitBreached = false;
        for(int limitPercentage = 100; limitPercentage >= 20; limitPercentage -= 20) {
            if (desk.getGrossUtilizationPercentage() > limitPercentage) {
                String message = createBreachMessage(objectMapper, "ERR-006", "Gross limit breached", desk, side, limitPercentage);
                log.error(message);
                ampsMessageOutboundProcessor.publishLimitBreach(message);
                grossUtilizationLimitBreached = true;
            }
            if (desk.getBuyUtilizationPercentage() > limitPercentage) {
                String message = createBreachMessage(objectMapper, "ERR-004", "Buy limit breached", desk, TradeSide.BUY, limitPercentage);
                log.error(message);
                ampsMessageOutboundProcessor.publishLimitBreach(message);
                break;
            }
            if (desk.getSellUtilizationPercentage() > limitPercentage) {
                String message = createBreachMessage(objectMapper, "ERR-005", "Sell limit breached", desk, TradeSide.SELL, limitPercentage);
                log.error(message);
                break;
            }
            if (grossUtilizationLimitBreached)
                break;
        }
    }

    private String createBreachMessage(ObjectMapper objectMapper, String errorCode, String errorMessage, Desk desk, TradeSide side, int limitPercentage) {
        try {
            Map<String, Object> breachDetails = new HashMap<>();
            breachDetails.put("errorCode", errorCode);
            breachDetails.put("errorMessage", errorMessage);
            breachDetails.put("deskId", desk.getId());
            breachDetails.put("deskName", desk.getName());
            breachDetails.put("limitPercentage", limitPercentage);
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