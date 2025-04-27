package com.trading.service;

import com.crankuptheamps.client.Client;
import com.crankuptheamps.client.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.messaging.AmpsMessageOutboundProcessor;
import com.trading.model.Desk;
import com.trading.model.Trader;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.trading.validation.OrderMessageValidator;
import org.slf4j.MDC;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;

@Component
@RequiredArgsConstructor
public class InitializationService {
    private static final Logger log = LoggerFactory.getLogger(InitializationService.class);
    @Value("${amps.server.url}")
    private String ampsServerUrl;
    @Value("${amps.client.name}")
    private String ampsClientName;
    @Value("${amps.topic.gui.initialization.request}")
    private String guiInitializationRequestTopic;
    @Autowired
    private final ObjectMapper objectMapper;
    @Autowired
    private final OrderMessageValidator messageValidator;
    private Client ampsClient;
    @Autowired
    private final TradingPersistenceService persistenceService;
    @Autowired
    AmpsMessageOutboundProcessor ampsMessageOutboundProcessor;

    @PostConstruct
    public void initialize() throws Exception {
        try {
            ampsClient = new Client(ampsClientName);
            ampsClient.connect(ampsServerUrl);
            ampsClient.logon();
            for(Message request : (ampsClient.subscribe(guiInitializationRequestTopic))) {
                handle(request);
            }
        } catch (Exception e) {
            log.error("ERR-771: Failed to initialize AMPS client", e);
            throw e;
        }
    }

    private void handle(Message message) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);

        try {
            Map<String, Object> request = objectMapper.readValue(message.getData(), Map.class);
            String requestId = (String) request.get("requestId");
            ObjectMapper objectMapper = new ObjectMapper();
            log.info("Publishing initial desk messages to AMPS.");

            for (Desk desk : persistenceService.getAllDesks()) {
                ampsMessageOutboundProcessor.publishNotionalUpdate(createDeskInitialMessage(objectMapper, desk, requestId));
            }

            log.info("Publishing initial trader messages to AMPS.");
            for (Trader trader : persistenceService.getAllTraders()) {
                ampsMessageOutboundProcessor.publishNotionalUpdate(createTraderInitialMessage(objectMapper, trader, requestId));
            }
        } catch (Exception e) {
            log.error("ERR-772: Failed to process GUI initialization request", e);
        } finally {
            MDC.remove("errorId");
        }
    }

    private String createDeskInitialMessage(ObjectMapper objectMapper, Desk desk, String requestId) {
        try {
            Map<String, Object> initialDetails = new HashMap<>();
            initialDetails.put("requestId", requestId);
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

    private String createTraderInitialMessage(ObjectMapper objectMapper, Trader trader, String requestId) {
        try {
            Map<String, Object> initialDetails = new HashMap<>();
            initialDetails.put("requestId", requestId);
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
