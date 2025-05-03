package com.trading.service;

import com.crankuptheamps.client.Client;
import com.crankuptheamps.client.exception.ConnectionException;
import com.trading.model.Order;
import com.trading.model.TradeSide;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import static java.lang.Thread.sleep;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderGeneratorService {

    private Client ampsClient;
    private final ObjectMapper objectMapper;
    private final List<UUID> traderIds = new ArrayList<>();
    private final Random random = new Random();

    @Value("${amps.server.url}")
    private String ampsServerUrl;

    @Value("${amps.topic.orders}")
    private String ordersTopic;

    private static final List<String> HK_SYMBOLS = Arrays.asList(
            "0700.HK", "0001.HK", "0005.HK", "0011.HK", "0016.HK"
    );

    private static final List<String> JP_SYMBOLS = Arrays.asList(
            "7203.T", "9984.T", "6758.T", "6861.T", "9432.T"
    );

    private static final List<String> KR_SYMBOLS = Arrays.asList(
            "005930.KS", "035720.KS", "035420.KS", "068270.KS", "051910.KS"
    );

    @PostConstruct
    public void init() throws ConnectionException {
        // Initialize 5 trader IDs
        traderIds.add(UUID.fromString("aa477902-f601-48f0-b206-48c472f75161"));
        traderIds.add(UUID.fromString("0927b5e0-162d-41c6-98ec-630c1a8d5b22"));
        traderIds.add(UUID.fromString("8f12a606-1e67-43de-bf55-5292fd535224"));
        traderIds.add(UUID.fromString("ddcc0ccd-3f61-43ff-bbb2-49f7a8eac64d"));
        traderIds.add(UUID.fromString("6eef7deb-f873-410f-93ae-09cfa209d84f"));

        log.info("Initialized trader IDs: {}", traderIds);

        ampsClient = new Client("order-generator");
        // Connect to AMPS
        ampsClient.connect(ampsServerUrl);
        ampsClient.logon();

        // Schedule order generation
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        generateOrders(executor);
    }

    private void generateOrders(ScheduledExecutorService executor) {
        int totalOrders = 50;
        int ordersSent = 0;
        long startTime = System.currentTimeMillis();
        long endTime = startTime + (5 * 60 * 10); // 5 minutes

        while (ordersSent < totalOrders && System.currentTimeMillis() < endTime) {
            Order order = createRandomOrder();
            sendOrder(order);
            ordersSent++;

            // Calculate random delay between orders
            long remainingTime = endTime - System.currentTimeMillis();
            long remainingOrders = totalOrders - ordersSent;
            long minDelay = Math.max(1, remainingOrders == 0 ? 1 : remainingTime / remainingOrders);
            long randomDelay = random.nextLong(minDelay);

            try {
                sleep(randomDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        executor.shutdown();
    }

    private Order createRandomOrder() {
        UUID id = UUID.randomUUID();
        UUID traderId = traderIds.get(random.nextInt(traderIds.size()));
        TradeSide side = random.nextBoolean() ? TradeSide.BUY : TradeSide.SELL;
        int quantity = random.nextInt(9901) + 100; // 100 to 10000
        double price = 100 + random.nextDouble() * 900; // 100 to 1000

        // Select random market and symbol
        int marketIndex = random.nextInt(3);
        String symbol;
        Currency currency;

        switch (marketIndex) {
            case 0:
                symbol = HK_SYMBOLS.get(random.nextInt(HK_SYMBOLS.size()));
                currency = Currency.getInstance("HKD");
                break;
            case 1:
                symbol = JP_SYMBOLS.get(random.nextInt(JP_SYMBOLS.size()));
                currency = Currency.getInstance("JPY");
                break;
            default:
                symbol = KR_SYMBOLS.get(random.nextInt(KR_SYMBOLS.size()));
                currency = Currency.getInstance("KRW");
        }

        return new Order(id, traderId, symbol, quantity, price, side, currency, LocalDateTime.now());
    }

    private void sendOrder(Order order) {
        try {
            String orderJson = objectMapper.writeValueAsString(order);
            ampsClient.publish(ordersTopic, orderJson);
            log.info("Sent order: {}", order);
        } catch (Exception e) {
            log.error("Error sending order: {}", order, e);
        }
    }
}