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
            checkBuyNotional(desk, order);
        else
            checkSellNotional(desk, order);

        checkGrossNotional(desk, order);
    }

    private double calculateUSDNotional(Order order) {
        double localNotional = order.getNotionalValue();
        return currencyManager.convertToUSD(localNotional, order.currency());
    }

    private void checkBuyNotional(Desk desk, Order order) {
        double notionalValueUSD = calculateUSDNotional(order);
        double buyTotal = desk.getCurrentBuyNotional() + notionalValueUSD;
        if(buyTotal > desk.getBuyNotionalLimit()) {
            log.info("REJECTION => Order notional: {} causes a {} buy notional limit 100% breach for desk: {} with a current buy notional: {}", round2dp.apply(notionalValueUSD), desk.getBuyNotionalLimit(), desk.getName(), round2dp.apply(desk.getCurrentBuyNotional()));
            return;
        }
        log.debug("ACCEPTED => Updated current BUY notional for desk: {} from: {} to: {} using new BUY order's notional: {}", desk.getName(), round2dp.apply(desk.getCurrentBuyNotional()), round2dp.apply(buyTotal), round2dp.apply(notionalValueUSD));
        desk.setCurrentBuyNotional(buyTotal);
        publishNotionalUpdate(desk, TradeSide.BUY, notionalValueUSD);
        checkLimitBreaches(desk, order);
    }

    private void checkSellNotional(Desk desk, Order order) {
        double notionalValueUSD = calculateUSDNotional(order);
        double sellTotal = desk.getCurrentSellNotional() + notionalValueUSD;
        if(sellTotal > desk.getSellNotionalLimit()) {
            log.info("REJECTION => Order notional: {} causes a {} sell notional limit breach for desk: {} with a current sell notional: {}", round2dp.apply(notionalValueUSD), desk.getSellNotionalLimit(), desk.getName(), round2dp.apply(desk.getCurrentSellNotional()));
            return;
        }
        log.debug("ACCEPTED => Updated current SELL notional for desk: {} from: {} to: {} using the new SELL order's notional: {}", desk.getName(), round2dp.apply(desk.getCurrentSellNotional()), round2dp.apply(sellTotal), round2dp.apply(notionalValueUSD));
        desk.setCurrentSellNotional(sellTotal);
        publishNotionalUpdate(desk, TradeSide.SELL, notionalValueUSD);
        checkLimitBreaches(desk, order);
    }

    private void checkGrossNotional(Desk desk, Order order) {
        double notionalValueUSD = calculateUSDNotional(order);
        double grossTotal = desk.getCurrentGrossNotional() + notionalValueUSD;
        if(grossTotal > desk.getGrossNotionalLimit()) {
            log.info("REJECTION => Order notional: {} causes a {} gross notional limit 100% breach for desk: {} with a current gross notional: {}", round2dp.apply(notionalValueUSD), desk.getGrossNotionalLimit(), desk.getName(), round2dp.apply(desk.getCurrentGrossNotional()));
        }
    }

    private Function<Double, Double> round2dp = (value) ->  Math.round(value*Math.pow(10,2))/Math.pow(10,2);

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

    private void checkLimitBreaches(Desk desk, Order order) {
        String message = "";
        for(int limitPercentage = 80; limitPercentage >= 20; limitPercentage -= 20) {
            if (desk.getGrossUtilizationPercentage() > limitPercentage) {
                message = createBreachMessage(objectMapper, "Gross limit", desk, order, limitPercentage);
                if(!message.isEmpty()) ampsMessageOutboundProcessor.publishLimitBreach(message);
            }
            if (desk.getBuyUtilizationPercentage() > limitPercentage) {
                message = createBreachMessage(objectMapper, "Buy limit", desk, order, limitPercentage);
                if(!message.isEmpty()) ampsMessageOutboundProcessor.publishLimitBreach(message);
                break;
            }
            if (desk.getSellUtilizationPercentage() > limitPercentage) {
                message = createBreachMessage(objectMapper, "Sell limit", desk, order, limitPercentage);
                if(!message.isEmpty()) ampsMessageOutboundProcessor.publishLimitBreach(message);
                break;
            }
        }
    }

    private String createBreachMessage(ObjectMapper objectMapper, String breachType, Desk desk, Order order, int limitPercentage) {
        try {
            Map<String, Object> breachDetails = new HashMap<>();
            breachDetails.put("BreachType", breachType);
            breachDetails.put("limitPercentage", limitPercentage);
            breachDetails.put("deskId", desk.getId());
            breachDetails.put("deskName", desk.getName());
            breachDetails.put("orderId", order.id());
            breachDetails.put("orderTraderId", order.traderId());
            breachDetails.put("orderSymbol", order.symbol());
            breachDetails.put("currentBuyNotional", round2dp.apply(desk.getCurrentBuyNotional()));
            breachDetails.put("currentSellNotional", round2dp.apply(desk.getCurrentSellNotional()));
            breachDetails.put("currentGrossNotional", round2dp.apply(desk.getCurrentGrossNotional()));
            breachDetails.put("buyUtilizationPercentage", round2dp.apply(desk.getBuyUtilizationPercentage()));
            breachDetails.put("sellUtilizationPercentage", round2dp.apply(desk.getSellUtilizationPercentage()));
            breachDetails.put("grossUtilizationPercentage", round2dp.apply(desk.getGrossUtilizationPercentage()));
            return objectMapper.writeValueAsString(breachDetails);
        } catch (Exception e) {
            log.error("Failed to create breach message desk: {}", desk, e);
            return "";
        }
    }
} 