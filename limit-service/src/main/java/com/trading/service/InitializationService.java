package com.trading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.messaging.AmpsMessageOutboundProcessor;
import com.trading.model.Desk;
import com.trading.model.DeskNotionalLimit;
import com.trading.model.Trader;
import com.trading.model.TraderNotionalLimit;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
public class InitializationService
{
    private static final Logger log = LoggerFactory.getLogger(InitializationService.class);
    @Autowired
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private final TradingPersistenceService persistenceService;
    @Autowired
    AmpsMessageOutboundProcessor ampsMessageOutboundProcessor;

    @PostConstruct
    public void initialize()
    {
        persistenceService.getAllDeskNotionalLimits().forEach(desk -> ampsMessageOutboundProcessor.publishDeskNotionalUpdate(createDeskInitialMessage(objectMapper, desk)));
        persistenceService.getAllTraderNotionalLimits().forEach(trader -> ampsMessageOutboundProcessor.publishTraderNotionalUpdate(createTraderInitialMessage(objectMapper, trader)));
    }

    private String createDeskInitialMessage(ObjectMapper objectMapper, DeskNotionalLimit deskNotionalLimit)
    {
        try
        {
            Map<String, Object> initialDetails = new HashMap<>();
            initialDetails.put("deskId", deskNotionalLimit.getDeskId());
            String deskName = persistenceService.getDeskById(deskNotionalLimit.getDeskId()).getDeskName();
            initialDetails.put("deskName", deskName);

            initialDetails.put("currentBuyNotional", 0);
            initialDetails.put("currentSellNotional", 0);
            initialDetails.put("currentGrossNotional", 0);

            initialDetails.put("buyUtilizationPercentage", 0);
            initialDetails.put("sellUtilizationPercentage", 0);
            initialDetails.put("grossUtilizationPercentage", 0);

            initialDetails.put("buyNotionalLimit", deskNotionalLimit.getBuyNotionalLimit());
            initialDetails.put("sellNotionalLimit", deskNotionalLimit.getSellNotionalLimit());
            initialDetails.put("grossNotionalLimit", deskNotionalLimit.getGrossNotionalLimit());
            return objectMapper.writeValueAsString(initialDetails);
        }
        catch (Exception e)
        {
            log.error("Failed to create breach message desk: {}", deskNotionalLimit, e);
            return "";
        }
    }

    private String createTraderInitialMessage(ObjectMapper objectMapper, TraderNotionalLimit traderNotionalLimit)
    {
        try
        {
            Map<String, Object> initialDetails = new HashMap<>();
            initialDetails.put("traderId", traderNotionalLimit.getTraderId());
            Trader trader = persistenceService.getTraderById(traderNotionalLimit.getTraderId());
            initialDetails.put("traderName", trader.getFirstName() + " " + trader.getLastName());
            Desk desk = persistenceService.findDeskByTraderId(traderNotionalLimit.getTraderId()).orElse(new Desk());
            initialDetails.put("deskId", desk.getDeskId());

            initialDetails.put("currentBuyNotional", 0);
            initialDetails.put("currentSellNotional", 0);
            initialDetails.put("currentGrossNotional", 0);

            initialDetails.put("buyUtilizationPercentage", 0);
            initialDetails.put("sellUtilizationPercentage", 0);
            initialDetails.put("grossUtilizationPercentage", 0);

            DeskNotionalLimit deskNotionalLimit = persistenceService.getDeskNotionalLimitById(desk.getDeskId());
            initialDetails.put("deskName", desk.getDeskName());
            initialDetails.put("buyNotionalLimit", deskNotionalLimit.getBuyNotionalLimit());
            initialDetails.put("sellNotionalLimit", deskNotionalLimit.getSellNotionalLimit());
            initialDetails.put("grossNotionalLimit", deskNotionalLimit.getGrossNotionalLimit());

            return objectMapper.writeValueAsString(initialDetails);
        }
        catch (Exception e)
        {
            log.error("Failed to create initial message for trader: {}", traderNotionalLimit, e);
            return "";
        }
    }
}
