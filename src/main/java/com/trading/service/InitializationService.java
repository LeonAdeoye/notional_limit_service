package com.trading.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.messaging.AmpsMessageOutboundProcessor;
import com.trading.model.Desk;
import com.trading.model.Trader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Service
public class InitializationService {
    private static final Logger log = LoggerFactory.getLogger(InitializationService.class);
    @Autowired
    AmpsMessageOutboundProcessor ampsMessageOutboundProcessor;
    @Autowired
    TradingPersistenceService persistenceService;

    @PostConstruct
    public void initialize() {
        log.info("Publishing initial desk messages to AMPS.");
        for (Desk desk : persistenceService.getAllDesks()) {
            ampsMessageOutboundProcessor.publishInitialMessage(createDeskInitialMessage(new ObjectMapper(), desk));
        }
        log.info("Publishing initial trader messages to AMPS.");
        for (Trader trader : persistenceService.getAllTraders()) {
            ampsMessageOutboundProcessor.publishInitialMessage(createTraderInitialMessage(new ObjectMapper(), trader));
        }
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

            initialDetails.put("deskBuyNotionalLimit", desk.getBuyNotionalLimit());
            initialDetails.put("deskSellNotionalLimit", desk.getSellNotionalLimit());
            initialDetails.put("deskGrossNotionalLimit", desk.getGrossNotionalLimit());
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
            return objectMapper.writeValueAsString(initialDetails);
        } catch (Exception e) {
            log.error("Failed to create initial message for trader: {}", trader, e);
            return "";
        }
    }

}
