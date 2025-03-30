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

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

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

        Desk desk = persistenceService.getDesk(trader.getDeskId());
        if (desk == null) {
            log.error("ERR-002: Desk not found with ID: {}", trader.getDeskId());
            throw new IllegalArgumentException("Desk not found");
        }

        double notionalValueUSD = calculateUSDNotional(order);

        if (order.side() == TradeSide.BUY)
            checkBuyNotional(desk, notionalValueUSD);
        else
            checkSellNotional(desk, notionalValueUSD);

        checkGrossNotional(desk, notionalValueUSD, order.side());
    }

    private double calculateUSDNotional(Order order) {
        double localNotional = order.getNotionalValue();
        return currencyManager.convertToUSD(localNotional, order.currency());
    }

    private void checkBuyNotional(Desk desk, double notionalValueUSD) {
        double buyTotal = desk.getCurrentBuyNotional() + notionalValueUSD;
        if(buyTotal > desk.getBuyNotionalLimit()) {
            log.info("Order notional: {} causes a {} buy notional limit breach for desk: {} with a current buy notional: {}", round2dp.apply(notionalValueUSD), desk.getBuyNotionalLimit(), desk.getName(), round2dp.apply(desk.getCurrentBuyNotional()));
            return;
        }
        log.debug("Updated current buy notional for desk: {} from: {} to: {} using the current order's notional", desk.getId(), round2dp.apply(desk.getCurrentBuyNotional()), round2dp.apply(buyTotal), round2dp.apply(notionalValueUSD));
        desk.setCurrentBuyNotional(buyTotal);
        publishNotionalUpdate(desk, TradeSide.BUY, notionalValueUSD);
        checkLimitBreaches(desk, TradeSide.BUY);
    }

    private void checkSellNotional(Desk desk, double notionalValueUSD) {
        double sellTotal = desk.getCurrentSellNotional() + notionalValueUSD;
        if(sellTotal > desk.getSellNotionalLimit()) {
            log.info("Order notional: {} causes a {} sell notional limit breach for desk: {} with a current sell notional: {}", round2dp.apply(notionalValueUSD), desk.getSellNotionalLimit(), desk.getName(), round2dp.apply(desk.getCurrentSellNotional()));
            return;
        }
        log.debug("Updated current sell notional for desk: {} from: {} to: {} using the current order's notional", desk.getId(), round2dp.apply(desk.getCurrentSellNotional()), round2dp.apply(sellTotal), round2dp.apply(notionalValueUSD));
        desk.setCurrentSellNotional(sellTotal);
        publishNotionalUpdate(desk, TradeSide.SELL, notionalValueUSD);
        checkLimitBreaches(desk, TradeSide.SELL);
    }

    private void checkGrossNotional(Desk desk, double notionalValueUSD, TradeSide side) {
        double grossTotal = desk.getCurrentGrossNotional() + notionalValueUSD;
        if(grossTotal > desk.getGrossNotionalLimit()) {
            log.info("Order notional: {} causes a {} gross notional limit breach for desk: {} with a current gross notional: {}", round2dp.apply(notionalValueUSD), desk.getGrossNotionalLimit(), desk.getName(), round2dp.apply(desk.getCurrentGrossNotional()));
            return;
        }
        checkLimitBreaches(desk, side);
    }


    private void updateDeskLimits(Desk desk, TradeSide side, double notionalValueUSD) {
        boolean grossLimitBreached = false;
        double grossTotal = desk.getGrossNotionalLimit() + notionalValueUSD;
        if(grossTotal > desk.getGrossNotionalLimit()) {
            log.info("ERR-003: Order notional: {} causes a {} gross notional limit breach for desk: {} with a current gross notional: {}", round2dp.apply(notionalValueUSD), desk.getGrossNotionalLimit(), desk.getName(), round2dp.apply(desk.getCurrentGrossNotional()));
            grossLimitBreached = true;
        }
        else
        {
            log.debug("Updated current gross notional for desk: {} from: {} to: {} using the current order's notional", desk.getId(), round2dp.apply(desk.getCurrentGrossNotional()), round2dp.apply(grossTotal), round2dp.apply(notionalValueUSD));
            desk.setGrossNotionalLimit(grossTotal);
        }
        if (!grossLimitBreached)
            publishNotionalUpdate(desk, side, notionalValueUSD);
    }

    public Function<Double, Double> round2dp = (value) ->  Math.round(value*Math.pow(10,2))/Math.pow(10,2);

    private void publishNotionalUpdate(Desk desk, TradeSide side, double notionalValueUSD) {
        try {
            Map<String, Object> updateDetails = new HashMap<>();
            updateDetails.put("deskId", desk.getId());
            updateDetails.put("deskName", desk.getName());
            updateDetails.put("side", side);
            updateDetails.put("notionalValueUSD", round2dp.apply(notionalValueUSD));
            if (side == TradeSide.BUY) {
                updateDetails.put("currentBuyNotional", round2dp.apply(desk.getCurrentBuyNotional()));
            } else if (side == TradeSide.SELL) {
                updateDetails.put("currentSellNotional", round2dp.apply(desk.getCurrentSellNotional()));
            }
            updateDetails.put("currentGrossNotional", round2dp.apply(desk.getCurrentGrossNotional()));

            String message = objectMapper.writeValueAsString(updateDetails);
            ampsMessageOutboundProcessor.publishNotionalUpdate(message);
            log.info("Published notional update message: {}", message);
        } catch (Exception e) {
            log.error("Failed to publish notional update message", e);
        }
    }

    private void checkLimitBreaches(Desk desk, TradeSide side) {
        boolean grossUtilizationLimitBreached = false;
        for(int limitPercentage = 80; limitPercentage >= 20; limitPercentage -= 20) {
            if (desk.getGrossUtilizationPercentage() > limitPercentage) {
                String message = createBreachMessage(objectMapper, "Gross limit", desk, side, limitPercentage);
                log.info(message);
                ampsMessageOutboundProcessor.publishLimitBreach(message);
                grossUtilizationLimitBreached = true;
            }
            if (desk.getBuyUtilizationPercentage() > limitPercentage) {
                String message = createBreachMessage(objectMapper, "Buy limit", desk, TradeSide.BUY, limitPercentage);
                log.info(message);
                ampsMessageOutboundProcessor.publishLimitBreach(message);
                break;
            }
            if (desk.getSellUtilizationPercentage() > limitPercentage) {
                String message = createBreachMessage(objectMapper, "Sell limit", desk, TradeSide.SELL, limitPercentage);
                log.info(message);
                break;
            }
            if (grossUtilizationLimitBreached)
                break;
        }
    }

    private String createBreachMessage(ObjectMapper objectMapper, String errorMessage, Desk desk, TradeSide side, int limitPercentage) {
        try {
            Map<String, Object> breachDetails = new HashMap<>();
            breachDetails.put("BreachType", errorMessage);
            breachDetails.put("deskId", desk.getId());
            breachDetails.put("deskName", desk.getName());
            breachDetails.put("limitPercentage", limitPercentage);
            if(side.getCode().equals(TradeSide.BUY)) {
                breachDetails.put("currentBuyNotional", round2dp.apply(desk.getCurrentBuyNotional()));
                breachDetails.put("buyUtilizationPercentage", round2dp.apply(desk.getBuyUtilizationPercentage()));
            } else {
                breachDetails.put("currentSellNotional", round2dp.apply(desk.getCurrentSellNotional()));
                breachDetails.put("sellUtilizationPercentage", round2dp.apply(desk.getSellUtilizationPercentage()));
            }
            breachDetails.put("grossUtilizationPercentage", round2dp.apply(desk.getGrossUtilizationPercentage()));
            return objectMapper.writeValueAsString(breachDetails);
        } catch (Exception e) {
            log.error("Failed to create breach message", e);
            return "{}";
        }
    }
} 