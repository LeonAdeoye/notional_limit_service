package com.trading.service.disruptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final double ROUNDING_FACTOR = Math.pow(10, 2);
    private static final Function<Double, Double> round2dp = (value) -> Math.round(value * ROUNDING_FACTOR) / ROUNDING_FACTOR;

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
            log.error("ERR-884: Trader not found with ID: {}", order.traderId());
            throw new IllegalArgumentException("Trader not found");
        }

        Desk desk = persistenceService.getDesk(trader.getDeskId());
        if (desk == null) {
            log.error("ERR-885: Desk not found with ID: {}", trader.getDeskId());
            throw new IllegalArgumentException("Desk not found");
        }

        double notionalValueUSD = calculateUSDNotional(order);
        checkSideNotionalLimit(trader, desk, order, notionalValueUSD);
        checkGrossNotionalLimit(trader, desk, order, notionalValueUSD);
        publishTraderNotionalUpdate(trader, order.side(), notionalValueUSD);
        publishDeskNotionalUpdate(desk, order.side(), notionalValueUSD);
    }

    private double calculateUSDNotional(Order order) {
        double localNotional = order.getNotionalValue();
        return currencyManager.convertToUSD(localNotional, order.currency());
    }

    private void checkSideNotionalLimit(Trader trader, Desk desk, Order order, double notionalValueUSD) {
        TradeSide side = order.side();
        String sideStr = side.toString();
        double currentNotional = (side == TradeSide.BUY) ? desk.getCurrentBuyNotional() : desk.getCurrentSellNotional();
        double limit = (side == TradeSide.BUY) ? desk.getBuyNotionalLimit() : desk.getSellNotionalLimit();
        double updatedNotional = currentNotional + notionalValueUSD;

        if (updatedNotional > limit) {
            log.info("REJECTION => Order notional: {} causes a {} {} notional limit breach for desk: {} with a current {} notional: {}",
                    round2dp.apply(notionalValueUSD), limit, sideStr, desk.getName(), sideStr, round2dp.apply(currentNotional));

            String message = createBreachMessage("Full " + sideStr + " limit", desk, order, 100);
            if (!message.isEmpty()) ampsMessageOutboundProcessor.publishLimitBreach(message);
            return;
        }

        log.debug("ACCEPTED => Updated current {} notional for desk: {} from: {} to: {} using new {} order's notional: {}",
                sideStr, desk.getName(), round2dp.apply(currentNotional), round2dp.apply(updatedNotional), sideStr, round2dp.apply(notionalValueUSD));

        if (side == TradeSide.BUY) {
            desk.setCurrentBuyNotional(updatedNotional);
            trader.setCurrentBuyNotional(updatedNotional);
        } else {
            desk.setCurrentSellNotional(updatedNotional);
            trader.setCurrentSellNotional(updatedNotional);
        }

        checkLimitBreaches(desk, order);
    }

    private void checkGrossNotionalLimit(Trader trader, Desk desk, Order order, double notionalValueUSD) {
        double deskGrossTotal = desk.getCurrentGrossNotional() + notionalValueUSD;
        double traderGrossTotal = trader.getCurrentGrossNotional() + notionalValueUSD;
        if(deskGrossTotal > desk.getGrossNotionalLimit()) {
            log.info("REJECTION => Order notional: {} causes a {} gross notional limit 100% breach for desk: {} with a current gross notional: {}", round2dp.apply(notionalValueUSD), desk.getGrossNotionalLimit(), desk.getName(), round2dp.apply(desk.getCurrentGrossNotional()));
            String message = createBreachMessage( "Full Gross limit", desk, order, 100);
            if(!message.isEmpty()) ampsMessageOutboundProcessor.publishLimitBreach(message);
            return;
        }
        desk.setCurrentGrossNotional(deskGrossTotal);
        trader.setCurrentGrossNotional(traderGrossTotal);
    }

    private void publishDeskNotionalUpdate(Desk desk, TradeSide side, double notionalValueUSD) {
        try {
            Map<String, Object> updateDetails = new HashMap<>();
            updateDetails.put("deskId", desk.getId());
            updateDetails.put("deskName", desk.getName());
            updateDetails.put("side", side);
            updateDetails.put("notionalValueUSD", round2dp.apply(notionalValueUSD));

            updateDetails.put("currentBuyNotional", round2dp.apply(desk.getCurrentBuyNotional()));
            updateDetails.put("buyUtilizationPercentage", round2dp.apply(desk.getBuyUtilizationPercentage()));
            updateDetails.put("buyNotionalLimit", desk.getBuyNotionalLimit());

            updateDetails.put("currentSellNotional", round2dp.apply(desk.getCurrentSellNotional()));
            updateDetails.put("sellUtilizationPercentage", round2dp.apply(desk.getSellUtilizationPercentage()));
            updateDetails.put("sellNotionalLimit", desk.getSellNotionalLimit());

            updateDetails.put("grossUtilizationPercentage", round2dp.apply(desk.getGrossUtilizationPercentage()));
            updateDetails.put("currentGrossNotional", round2dp.apply(desk.getCurrentGrossNotional()));
            updateDetails.put("grossNotionalLimit", desk.getGrossNotionalLimit());

            String message = objectMapper.writeValueAsString(updateDetails);
            ampsMessageOutboundProcessor.publishDeskNotionalUpdate(message);
        } catch (Exception e) {
            log.error("ERR-880: Failed to publish notional update message", e);
        }
    }

    private void publishTraderNotionalUpdate(Trader trader, TradeSide side, double notionalValueUSD) {
        try {
            Map<String, Object> updateDetails = new HashMap<>();
            updateDetails.put("traderId", trader.getId());
            updateDetails.put("traderName", trader.getName());
            updateDetails.put("deskId", trader.getDeskId());
            Desk desk = persistenceService.getDesk(trader.getDeskId());
            updateDetails.put("deskName", desk.getName());
            updateDetails.put("side", side);
            updateDetails.put("notionalValueUSD", round2dp.apply(notionalValueUSD));

            updateDetails.put("currentBuyNotional", round2dp.apply(trader.getCurrentBuyNotional()));
            updateDetails.put("buyUtilizationPercentage", round2dp.apply(100 * trader.getCurrentBuyNotional()/desk.getBuyNotionalLimit()));
            updateDetails.put("buyNotionalLimit", desk.getBuyNotionalLimit());

            updateDetails.put("currentSellNotional", round2dp.apply(trader.getCurrentSellNotional()));
            updateDetails.put("sellUtilizationPercentage", round2dp.apply(100 * trader.getCurrentSellNotional()/desk.getSellNotionalLimit()));
            updateDetails.put("sellNotionalLimit", desk.getSellNotionalLimit());

            updateDetails.put("grossUtilizationPercentage", round2dp.apply(100 * trader.getCurrentGrossNotional()/desk.getGrossNotionalLimit()));
            updateDetails.put("currentGrossNotional", round2dp.apply(trader.getCurrentGrossNotional()));
            updateDetails.put("grossNotionalLimit", desk.getGrossNotionalLimit());

            String message = objectMapper.writeValueAsString(updateDetails);
            ampsMessageOutboundProcessor.publishTraderNotionalUpdate(message);
        } catch (Exception e) {
            log.error("ERR-881: Failed to publish notional update message", e);
        }
    }

    private void checkLimitBreaches(Desk desk, Order order) {
        String message = "";
        for(int limitPercentage = 80; limitPercentage >= 20; limitPercentage -= 20) {
            if (desk.getGrossUtilizationPercentage() > limitPercentage) {
                message = createBreachMessage( "Gross limit", desk, order, limitPercentage);
                if(!message.isEmpty()) ampsMessageOutboundProcessor.publishLimitBreach(message);
            }
            if (order.side().equals(TradeSide.BUY) && desk.getBuyUtilizationPercentage() > limitPercentage) {
                message = createBreachMessage( "Buy limit", desk, order, limitPercentage);
                if(!message.isEmpty()) ampsMessageOutboundProcessor.publishLimitBreach(message);
                break;
            }
            if (order.side().equals(TradeSide.SELL) && desk.getSellUtilizationPercentage() > limitPercentage) {
                message = createBreachMessage( "Sell limit", desk, order, limitPercentage);
                if(!message.isEmpty()) ampsMessageOutboundProcessor.publishLimitBreach(message);
                break;
            }
        }
    }

    private String createBreachMessage(String breachType, Desk desk, Order order, int limitPercentage) {
        try {
            Map<String, Object> breachDetails = new HashMap<>();

            breachDetails.put("breachType", breachType);
            breachDetails.put("limitPercentage", limitPercentage);

            breachDetails.put("deskId", desk.getId());
            breachDetails.put("deskName", desk.getName());

            breachDetails.put("orderId", order.id());
            breachDetails.put("traderId", order.traderId());
            breachDetails.put("traderName", persistenceService.getTrader(order.traderId()).getName());
            breachDetails.put("symbol", order.symbol());
            breachDetails.put("side", order.side());
            breachDetails.put("quantity", order.quantity());
            breachDetails.put("price", round2dp.apply(order.price()));
            breachDetails.put("currency", order.currency());
            breachDetails.put("notionalLocal", round2dp.apply(order.getNotionalValue()));
            breachDetails.put("tradeTimestamp", order.tradeTimestamp());

            breachDetails.put("currentBuyNotional", round2dp.apply(desk.getCurrentBuyNotional()));
            breachDetails.put("currentSellNotional", round2dp.apply(desk.getCurrentSellNotional()));
            breachDetails.put("currentGrossNotional", round2dp.apply(desk.getCurrentGrossNotional()));
            breachDetails.put("buyUtilizationPercentage", round2dp.apply(desk.getBuyUtilizationPercentage()));
            breachDetails.put("sellUtilizationPercentage", round2dp.apply(desk.getSellUtilizationPercentage()));
            breachDetails.put("grossUtilizationPercentage", round2dp.apply(desk.getGrossUtilizationPercentage()));
            breachDetails.put("notionalUSD", round2dp.apply(calculateUSDNotional(order)));

            breachDetails.put("buyNotionalLimit", desk.getBuyNotionalLimit());
            breachDetails.put("sellNotionalLimit", desk.getSellNotionalLimit());
            breachDetails.put("grossNotionalLimit", desk.getGrossNotionalLimit());
            return objectMapper.writeValueAsString(breachDetails);
        } catch (Exception e) {
            log.error("ERR-882: Failed to create breach message desk: {}", desk, e);
            return "";
        }
    }
} 