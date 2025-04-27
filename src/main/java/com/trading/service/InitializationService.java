package com.trading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.messaging.AmpsMessageOutboundProcessor;
import com.trading.model.Desk;
import com.trading.model.Trader;
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
public class InitializationService {
    private static final Logger log = LoggerFactory.getLogger(InitializationService.class);
    @Autowired
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Autowired
    private final TradingPersistenceService persistenceService;
    @Autowired
    AmpsMessageOutboundProcessor ampsMessageOutboundProcessor;

    @PostConstruct
    public void initialize() {
        persistenceService.getAllDesks().forEach(desk -> ampsMessageOutboundProcessor.publishDeskNotionalUpdate(createDeskInitialMessage(objectMapper, desk)));
        persistenceService.getAllTraders().forEach(trader -> ampsMessageOutboundProcessor.publishTraderNotionalUpdate(createTraderInitialMessage(objectMapper, trader)));
    }

    private String createDeskInitialMessage(ObjectMapper objectMapper, Desk desk) {
        try {
            Map<String, Object> initialDetails = new HashMap<>();
            initialDetails.put("deskId", desk.getId());
            initialDetails.put("deskName", desk.getName());

            initialDetails.put("currentBuyNotional", 0);
            initialDetails.put("currentSellNotional", 0);
            initialDetails.put("currentGrossNotional", 0);

            initialDetails.put("buyUtilizationPercentage", 0);
            initialDetails.put("sellUtilizationPercentage", 0);
            initialDetails.put("grossUtilizationPercentage", 0);

            initialDetails.put("buyNotionalLimit", desk.getBuyNotionalLimit());
            initialDetails.put("sellNotionalLimit", desk.getSellNotionalLimit());
            initialDetails.put("grossNotionalLimit", desk.getGrossNotionalLimit());
            return objectMapper.writeValueAsString(initialDetails);
        } catch (Exception e) {
            log.error("Failed to create breach message desk: {}", desk, e);
            return "";
        }
    }

    private String createTraderInitialMessage(ObjectMapper objectMapper, Trader trader) {
        try {
            Map<String, Object> initialDetails = new HashMap<>();
            initialDetails.put("traderId", trader.getId());
            initialDetails.put("traderName", trader.getName());
            initialDetails.put("deskId", trader.getDeskId());

            initialDetails.put("currentBuyNotional", 0);
            initialDetails.put("currentSellNotional", 0);
            initialDetails.put("currentGrossNotional", 0);

            initialDetails.put("buyUtilizationPercentage", 0);
            initialDetails.put("sellUtilizationPercentage", 0);
            initialDetails.put("grossUtilizationPercentage", 0);

            Desk desk = persistenceService.getDeskById(trader.getDeskId());
            initialDetails.put("deskName", desk.getName());
            initialDetails.put("buyNotionalLimit", desk.getBuyNotionalLimit());
            initialDetails.put("sellNotionalLimit", desk.getSellNotionalLimit());
            initialDetails.put("grossNotionalLimit", desk.getGrossNotionalLimit());

            return objectMapper.writeValueAsString(initialDetails);
        } catch (Exception e) {
            log.error("Failed to create initial message for trader: {}", trader, e);
            return "";
        }
    }
}
