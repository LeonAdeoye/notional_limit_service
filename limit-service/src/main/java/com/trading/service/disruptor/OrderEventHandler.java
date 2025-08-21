package com.trading.service.disruptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lmax.disruptor.EventHandler;
import com.trading.messaging.AmpsMessageOutboundProcessor;
import com.trading.model.*;
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
import java.util.UUID;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class OrderEventHandler implements EventHandler<OrderEvent>
{
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
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch)
    {
        try
        {
            MDC.put("errorId", event.getErrorId());
            processOrder(event.getOrder());
        }
        finally
        {
            MDC.remove("errorId");
        }
    }

    private void processOrder(Order order)
    {
        TraderNotionalLimit trader = persistenceService.getTraderNotionalLimit(UUID.fromString(order.getOwnerId()));
        if (trader == null)
        {
            log.error("ERR-884: Trader not found with ID: {}", order.getOwnerId());
            throw new IllegalArgumentException("Trader not found");
        }

        DeskNotionalLimit deskNotionalLimit = persistenceService.getDeskNotionalLimit(trader.getTraderId());
        if (deskNotionalLimit == null)
        {
            log.error("ERR-885: Desk not found with ID: {}", trader.getTraderId());
            throw new IllegalArgumentException("Desk not found");
        }

        double notionalValueUSD = calculateUSDNotional(order);
        checkSideNotionalLimit(trader, deskNotionalLimit, order, notionalValueUSD);
        checkGrossNotionalLimit(trader, deskNotionalLimit, order, notionalValueUSD);
        publishTraderNotionalUpdate(trader, order.getSide(), notionalValueUSD);
        publishDeskNotionalUpdate(deskNotionalLimit, order.getSide(), notionalValueUSD);
    }

    private double calculateUSDNotional(Order order)
    {
        double localNotional = order.getOrderNotionalValueInLocal();
        return currencyManager.convertToUSD(localNotional, Enum.valueOf(Currency.class, order.getSettlementCurrency()));
    }

    private void checkSideNotionalLimit(TraderNotionalLimit trader, DeskNotionalLimit deskNotionalLimit, Order order, double notionalValueUSD)
    {
        Side side = order.getSide();
        String sideStr = side.toString();
        double currentNotional = (side == Side.BUY) ? deskNotionalLimit.getCurrentBuyNotional() : deskNotionalLimit.getCurrentSellNotional();
        double limit = (side == Side.BUY) ? deskNotionalLimit.getBuyNotionalLimit() : deskNotionalLimit.getSellNotionalLimit();
        double updatedNotional = currentNotional + notionalValueUSD;

        String deskName = persistenceService.getDeskById(deskNotionalLimit.getDeskId()).getDeskName();
        if (updatedNotional > limit)
        {
            log.info("REJECTION => Order notional: {} causes a {} {} notional limit breach for desk: {} with a current {} notional: {}",
                    round2dp.apply(notionalValueUSD), limit, sideStr, deskName, sideStr, round2dp.apply(currentNotional));

            String message = createBreachMessage("Full " + sideStr + " limit", deskNotionalLimit, order, 100);
            if (!message.isEmpty()) ampsMessageOutboundProcessor.publishLimitBreach(message);
            return;
        }

        log.debug("ACCEPTED => Updated current {} notional for desk: {} from: {} to: {} using new {} order's notional: {}",
                sideStr, deskName, round2dp.apply(currentNotional), round2dp.apply(updatedNotional), sideStr, round2dp.apply(notionalValueUSD));

        if (side == Side.BUY)
        {
            deskNotionalLimit.setCurrentBuyNotional(updatedNotional);
            trader.setCurrentBuyNotional(updatedNotional);
        }
        else
        {
            deskNotionalLimit.setCurrentSellNotional(updatedNotional);
            trader.setCurrentSellNotional(updatedNotional);
        }

        checkLimitBreaches(deskNotionalLimit, order);
    }

    private void checkGrossNotionalLimit(TraderNotionalLimit trader, DeskNotionalLimit deskNotionalLimit, Order order, double notionalValueUSD)
    {
        double deskGrossTotal = deskNotionalLimit.getCurrentGrossNotional() + notionalValueUSD;
        double traderGrossTotal = trader.getCurrentGrossNotional() + notionalValueUSD;
        if(deskGrossTotal > deskNotionalLimit.getGrossNotionalLimit())
        {
            String deskName = persistenceService.getDeskById(deskNotionalLimit.getDeskId()).getDeskName();
            log.info("REJECTION => Order notional: {} causes a {} gross notional limit 100% breach for desk: {} with a current gross notional: {}",
                round2dp.apply(notionalValueUSD), deskNotionalLimit.getGrossNotionalLimit(), deskName, round2dp.apply(deskNotionalLimit.getCurrentGrossNotional()));
            String message = createBreachMessage( "Full Gross limit", deskNotionalLimit, order, 100);
            if(!message.isEmpty()) ampsMessageOutboundProcessor.publishLimitBreach(message);
            return;
        }
        deskNotionalLimit.setCurrentGrossNotional(deskGrossTotal);
        trader.setCurrentGrossNotional(traderGrossTotal);
    }

    private void publishDeskNotionalUpdate(DeskNotionalLimit deskNotionalLimit, Side side, double notionalValueUSD)
    {
        try
        {
            Map<String, Object> updateDetails = new HashMap<>();
            updateDetails.put("deskId", deskNotionalLimit.getDeskId());
            String deskName = persistenceService.getDeskById(deskNotionalLimit.getDeskId()).getDeskName();
            updateDetails.put("deskName", deskName);
            updateDetails.put("side", side);
            updateDetails.put("notionalValueUSD", round2dp.apply(notionalValueUSD));

            updateDetails.put("currentBuyNotional", round2dp.apply(deskNotionalLimit.getCurrentBuyNotional()));
            updateDetails.put("buyUtilizationPercentage", round2dp.apply(deskNotionalLimit.getBuyUtilizationPercentage()));
            updateDetails.put("buyNotionalLimit", deskNotionalLimit.getBuyNotionalLimit());

            updateDetails.put("currentSellNotional", round2dp.apply(deskNotionalLimit.getCurrentSellNotional()));
            updateDetails.put("sellUtilizationPercentage", round2dp.apply(deskNotionalLimit.getSellUtilizationPercentage()));
            updateDetails.put("sellNotionalLimit", deskNotionalLimit.getSellNotionalLimit());

            updateDetails.put("grossUtilizationPercentage", round2dp.apply(deskNotionalLimit.getGrossUtilizationPercentage()));
            updateDetails.put("currentGrossNotional", round2dp.apply(deskNotionalLimit.getCurrentGrossNotional()));
            updateDetails.put("grossNotionalLimit", deskNotionalLimit.getGrossNotionalLimit());

            String message = objectMapper.writeValueAsString(updateDetails);
            ampsMessageOutboundProcessor.publishDeskNotionalUpdate(message);
        }
        catch (Exception e)
        {
            log.error("ERR-880: Failed to publish notional update message", e);
        }
    }

    private void publishTraderNotionalUpdate(TraderNotionalLimit traderNotionalLimit, Side side, double notionalValueUSD)
    {
        try
        {
            Map<String, Object> updateDetails = new HashMap<>();
            Trader trader = persistenceService.getTraderById(traderNotionalLimit.getTraderId());
            updateDetails.put("traderId", trader.getTraderId());
            updateDetails.put("traderName", trader.getFirstName() + " " + trader.getLastName());
            Desk desk = persistenceService.findDeskByTraderId(traderNotionalLimit.getTraderId()).orElse(new Desk());

            updateDetails.put("deskId", desk.getDeskId());
            updateDetails.put("deskName", desk.getDeskName());
            updateDetails.put("side", side);
            updateDetails.put("notionalValueUSD", round2dp.apply(notionalValueUSD));

            DeskNotionalLimit deskNotionalLimit = persistenceService.getDeskNotionalLimit(desk.getDeskId());
            updateDetails.put("currentBuyNotional", round2dp.apply(traderNotionalLimit.getCurrentBuyNotional()));
            updateDetails.put("buyUtilizationPercentage", round2dp.apply(100 * traderNotionalLimit.getCurrentBuyNotional()/ deskNotionalLimit.getBuyNotionalLimit()));
            updateDetails.put("buyNotionalLimit", deskNotionalLimit.getBuyNotionalLimit());

            updateDetails.put("currentSellNotional", round2dp.apply(traderNotionalLimit.getCurrentSellNotional()));
            updateDetails.put("sellUtilizationPercentage", round2dp.apply(100 * traderNotionalLimit.getCurrentSellNotional()/ deskNotionalLimit.getSellNotionalLimit()));
            updateDetails.put("sellNotionalLimit", deskNotionalLimit.getSellNotionalLimit());

            updateDetails.put("grossUtilizationPercentage", round2dp.apply(100 * traderNotionalLimit.getCurrentGrossNotional()/ deskNotionalLimit.getGrossNotionalLimit()));
            updateDetails.put("currentGrossNotional", round2dp.apply(traderNotionalLimit.getCurrentGrossNotional()));
            updateDetails.put("grossNotionalLimit", deskNotionalLimit.getGrossNotionalLimit());

            String message = objectMapper.writeValueAsString(updateDetails);
            ampsMessageOutboundProcessor.publishTraderNotionalUpdate(message);
        }
        catch (Exception e)
        {
            log.error("ERR-881: Failed to publish notional update message", e);
        }
    }

    private void checkLimitBreaches(DeskNotionalLimit deskNotionalLimit, Order order)
    {
        String message = "";
        for(int limitPercentage = 80; limitPercentage >= 20; limitPercentage -= 20)
        {
            if (deskNotionalLimit.getGrossUtilizationPercentage() > limitPercentage)
            {
                message = createBreachMessage( "Gross limit", deskNotionalLimit, order, limitPercentage);
                if(!message.isEmpty()) ampsMessageOutboundProcessor.publishLimitBreach(message);
            }
            if (order.getSide().equals(Side.BUY) && deskNotionalLimit.getBuyUtilizationPercentage() > limitPercentage)
            {
                message = createBreachMessage( "Buy limit", deskNotionalLimit, order, limitPercentage);
                if(!message.isEmpty()) ampsMessageOutboundProcessor.publishLimitBreach(message);
                break;
            }
            if (!order.getSide().equals(Side.BUY) && deskNotionalLimit.getSellUtilizationPercentage() > limitPercentage)
            {
                message = createBreachMessage( "Sell limit", deskNotionalLimit, order, limitPercentage);
                if(!message.isEmpty()) ampsMessageOutboundProcessor.publishLimitBreach(message);
                break;
            }
        }
    }

    private String createBreachMessage(String breachType, DeskNotionalLimit deskNotionalLimit, Order order, int limitPercentage)
    {
        try
        {
            Map<String, Object> breachDetails = new HashMap<>();

            breachDetails.put("breachType", breachType);
            breachDetails.put("limitPercentage", limitPercentage);
            breachDetails.put("deskId", deskNotionalLimit.getDeskId());
            String deskName = persistenceService.getDeskById(deskNotionalLimit.getDeskId()).getDeskName();
            breachDetails.put("deskName", deskName);
            breachDetails.put("orderId", order.getOrderId());
            breachDetails.put("traderId", order.getOwnerId());
            breachDetails.put("traderName", persistenceService.findTraderFullNameByUserId(order.getOwnerId()));
            breachDetails.put("symbol", order.getInstrumentCode());
            breachDetails.put("side", order.getSide());
            breachDetails.put("quantity", order.getQuantity());
            breachDetails.put("price", round2dp.apply(order.getPrice()));
            breachDetails.put("currency", order.getSettlementCurrency());
            breachDetails.put("notionalLocal", round2dp.apply(order.getOrderNotionalValueInLocal()));
            breachDetails.put("tradeTimestamp", order.getArrivalTime());

            breachDetails.put("currentBuyNotional", round2dp.apply(deskNotionalLimit.getCurrentBuyNotional()));
            breachDetails.put("currentSellNotional", round2dp.apply(deskNotionalLimit.getCurrentSellNotional()));
            breachDetails.put("currentGrossNotional", round2dp.apply(deskNotionalLimit.getCurrentGrossNotional()));
            breachDetails.put("buyUtilizationPercentage", round2dp.apply(deskNotionalLimit.getBuyUtilizationPercentage()));
            breachDetails.put("sellUtilizationPercentage", round2dp.apply(deskNotionalLimit.getSellUtilizationPercentage()));
            breachDetails.put("grossUtilizationPercentage", round2dp.apply(deskNotionalLimit.getGrossUtilizationPercentage()));
            breachDetails.put("notionalUSD", round2dp.apply(calculateUSDNotional(order)));

            breachDetails.put("buyNotionalLimit", deskNotionalLimit.getBuyNotionalLimit());
            breachDetails.put("sellNotionalLimit", deskNotionalLimit.getSellNotionalLimit());
            breachDetails.put("grossNotionalLimit", deskNotionalLimit.getGrossNotionalLimit());
            return objectMapper.writeValueAsString(breachDetails);
        }
        catch (Exception e)
        {
            log.error("ERR-882: Failed to create breach message desk: {}", deskNotionalLimit, e);
            return "";
        }
    }
} 