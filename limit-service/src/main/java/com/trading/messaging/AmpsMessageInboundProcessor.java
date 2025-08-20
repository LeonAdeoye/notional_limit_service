package com.trading.messaging;

import com.crankuptheamps.client.Client;
import com.crankuptheamps.client.Message;
import com.crankuptheamps.client.MessageHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.trading.validation.OrderMessageValidator;
import com.trading.validation.ValidationResult;
import com.trading.model.Order;
import com.trading.service.NotionalLimitService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;

@Component
@RequiredArgsConstructor
public class AmpsMessageInboundProcessor implements MessageHandler
{
    private static final Logger log = LoggerFactory.getLogger(AmpsMessageInboundProcessor.class);
    
    @Value("${amps.server.url}")
    private String ampsServerUrl;
    @Value("${amps.client.name}")
    private String ampsClientName;
    @Value("${amps.topic.orders}")
    private String ordersTopic;
    @Autowired
    private final NotionalLimitService notionalLimitService;
    private ObjectMapper objectMapper;
    @Autowired
    private final OrderMessageValidator messageValidator;
    private Client ampsClient;
    
    @PostConstruct
    public void initialize() throws Exception
    {
        try
        {
            ampsClient = new Client(ampsClientName);
            ampsClient.connect(ampsServerUrl);
            ampsClient.logon();
            objectMapper = new ObjectMapper();
            DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ENGLISH);
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ENGLISH);
            JavaTimeModule javaTimeModule = new JavaTimeModule();
            javaTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(dateFormatter));
            javaTimeModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(timeFormatter));
            objectMapper.registerModule(javaTimeModule);
            for(Message message : (ampsClient.subscribe(ordersTopic)))
                invoke(message);
        }
        catch (Exception e)
        {
            log.error("ERR-007: Failed to initialize AMPS client", e);
            throw e;
        }
    }
    
    @Override
    public void invoke(Message message)
    {
        String errorId = UUID.randomUUID().toString();
        MDC.put("errorId", errorId);
        
        try
        {
            ValidationResult validationResult = messageValidator.validateMessage(message.getData());
            
            if (!validationResult.isValid())
            {
                log.error("ERR-008: Invalid message received: {}", validationResult.getErrorMessage());
                return;
            }

            Order order = objectMapper.readValue(message.getData(), Order.class);
            log.info("Received valid order message: {}", order);
            notionalLimitService.processOrder(order);
            
        }
        catch (Exception e)
        {
            log.error("ERR-009: Failed to process message", e);
        }
        finally
        {
            MDC.remove("errorId");
        }
    }
} 