package com.trading.messaging;

import com.crankuptheamps.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

@Component
public class AmpsMessageOutboundProcessor {
    private static final Logger log = LoggerFactory.getLogger(AmpsMessageOutboundProcessor.class);
    private Client ampsClient;
    @Value("${amps.server.url}")
    private String ampsServerUrl;
    @Value("${amps.client.name}")
    private String ampsClientName;
    @Value("${amps.topic.limit.breach}")
    private String limitBreachTopic;

    @Value("${amps.topic.notional.update}")
    private String notionalUpdateTopic;

    @PostConstruct
    public void initialize() throws Exception {
        try {
            ampsClient = new Client(ampsClientName);
            ampsClient.connect(ampsServerUrl);
            ampsClient.logon();
        } catch (Exception e) {
            log.error("ERR-901: Failed to initialize AMPS client for AmpsOutboundProcessor", e);
            throw e;
        }
    }
    public void publishLimitBreach(String breachMessage) {
        try {
            ampsClient.publish(limitBreachTopic, breachMessage);
            log.info("Published limit breach message: {}", breachMessage);
        } catch (Exception e) {
            log.error("ERR-902: Failed to publish limit breach message: {}", breachMessage, e);
        }
    }

    public void publishNotionalUpdate(String notionalUpdateMessage) {
        try {
            ampsClient.publish(notionalUpdateTopic, notionalUpdateMessage);
            log.info("Published notional update message: {}", notionalUpdateMessage);
        } catch (Exception e) {
            log.error("ERR-903: Failed to publish notional update message: {}", notionalUpdateMessage, e);
        }
    }
}
