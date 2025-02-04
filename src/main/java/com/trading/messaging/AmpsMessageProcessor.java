package com.trading.messaging;

import com.crankuptheamps.client.Client;
import com.crankuptheamps.client.Message;
import com.crankuptheamps.client.MessageHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trading.model.Order;
import com.trading.service.NotionalLimitService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.trading.validation.OrderMessageValidator;
import com.trading.validation.ValidationResult;
import org.slf4j.MDC;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;

@Component
@RequiredArgsConstructor
public class AmpsMessageProcessor implements MessageHandler {
    private static final Logger log = LoggerFactory.getLogger(AmpsMessageProcessor.class);
    
    @Value("${amps.server.url}")
    private String ampsServerUrl;
    
    @Value("${amps.client.name}")
    private String ampsClientName;
    
    @Value("${amps.topic.orders}")
    private String ordersTopic;
    
    @Value("${amps.topic.limit.breach}")
    private String limitBreachTopic;
    
    @Autowired
    private final NotionalLimitService notionalLimitService;
    
    @Autowired
    private final ObjectMapper objectMapper;
    
    @Autowired
    private final OrderMessageValidator messageValidator;
    
    private Client ampsClient;
    
    @PostConstruct
    public void initialize() throws Exception {
        try {
            ampsClient = new Client(ampsClientName);
            ampsClient.connect(ampsServerUrl);
            ampsClient.logon();
            ampsClient.subscribe(ordersTopic);
            log.info("Successfully initialized AMPS client and subscribed to topic: {}", ordersTopic);
        } catch (Exception e) {
            log.error("ERR-007: Failed to initialize AMPS client", e);
            throw e;
        }
    }
    
    @Override
    public void invoke(Message message) {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try {
            // First validate the message
            ValidationResult validationResult = messageValidator.validateMessage(message.getData());
            
            if (!validationResult.isValid()) {
                log.error("ERR-008: Invalid message received: {}", validationResult.getErrorMessage());
                return;
            }
            
            // If valid, process the order
            Order order = objectMapper.readValue(message.getData(), Order.class);
            log.info("Received valid order message: {}", order);
            notionalLimitService.processOrder(order);
            
        } catch (Exception e) {
            log.error("ERR-009: Failed to process message", e);
        } finally {
            MDC.remove("errorId");
        }
    }
    
    public void publishLimitBreach(String breachMessage) {
        try {
            ampsClient.publish(limitBreachTopic, breachMessage);
            log.info("Published limit breach message: {}", breachMessage);
        } catch (Exception e) {
            log.error("ERR-009: Failed to publish limit breach message: {}", breachMessage, e);
        }
    }
} 