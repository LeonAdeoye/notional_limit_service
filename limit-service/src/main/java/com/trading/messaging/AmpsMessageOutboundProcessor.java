package com.trading.messaging;

import com.crankuptheamps.client.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;

@Component
public class AmpsMessageOutboundProcessor
{
    private static final Logger log = LoggerFactory.getLogger(AmpsMessageOutboundProcessor.class);
    private Client ampsClient;
    @Value("${amps.server.url}")
    private String ampsServerUrl;
    @Value("${amps.client.name}")
    private String ampsClientName;
    @Value("${amps.topic.limit.breach}")
    private String limitBreachTopic;
    @Value("${amps.topic.desk.notional.update}")
    private String deskNotionalUpdateTopic;
    @Value("${amps.topic.trader.notional.update}")
    private String traderNotionalUpdateTopic;

    @PostConstruct
    public void initialize() throws Exception
    {
        try
        {
            ampsClient = new Client(ampsClientName);
            ampsClient.connect(ampsServerUrl);
            ampsClient.logon();
        }
        catch (Exception e)
        {
            log.error("ERR-901: Failed to initialize AMPS client for AmpsOutboundProcessor", e);
            throw e;
        }
    }
    public void publishLimitBreach(String breachMessage)
    {
        try
        {
            ampsClient.publish(limitBreachTopic, breachMessage);
            log.info("Published limit breach message: {}", breachMessage);
        }
        catch (Exception e)
        {
            log.error("ERR-902: Failed to publish limit breach message: {}", breachMessage, e);
        }
    }

    public void publishDeskNotionalUpdate(String notionalUpdateMessage)
    {
        try {
            ampsClient.publish(deskNotionalUpdateTopic, notionalUpdateMessage);
            log.info("Published desk notional update message: {}", notionalUpdateMessage);
        } catch (Exception e) {
            log.error("ERR-903: Failed to publish desk notional update message: {}", notionalUpdateMessage, e);
        }
    }

    public void publishTraderNotionalUpdate(String notionalUpdateMessage) {
        try {
            ampsClient.publish(traderNotionalUpdateTopic, notionalUpdateMessage);
            log.info("Published trader notional update message: {}", notionalUpdateMessage);
        } catch (Exception e) {
            log.error("ERR-904: Failed to publish trader notional update message: {}", notionalUpdateMessage, e);
        }
    }
}
