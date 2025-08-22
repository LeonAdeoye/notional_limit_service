package com.trading.service;

import com.trading.model.Desk;
import com.trading.model.DeskNotionalLimit;
import com.trading.model.Trader;
import com.trading.model.TraderNotionalLimit;
import com.trading.repository.DeskNotionalLimitRepository;
import com.trading.repository.DeskRepository;
import com.trading.repository.TraderNotionalLimitRepository;
import com.trading.repository.TraderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class TradingPersistenceService
{
    private static final Logger log = LoggerFactory.getLogger(TradingPersistenceService.class);
    @Autowired
    private final DeskNotionalLimitRepository deskNotionalLimitRepository;
    @Autowired
    private final DeskRepository deskRepository;
    @Autowired
    private final TraderRepository traderRepository;
    @Autowired
    private final TraderNotionalLimitRepository traderNotionalLimitRepository;
    private final Map<UUID, DeskNotionalLimit> deskNotionalLimitCache = new ConcurrentHashMap<>();
    private final Map<UUID, TraderNotionalLimit> traderNotionalLimitCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<TraderNotionalLimit>> deskTradersCache = new ConcurrentHashMap<>();
    private Map<UUID, Desk> desksCache = new ConcurrentHashMap<>();
    private Map<UUID, Trader> tradersCache = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initializeCaches()
    {
        log.info("Initializing trading data caches from MongoDB");
        try
        {
            List<Desk> desks = deskRepository.findAll();
            desks.forEach(desk -> desksCache.put(desk.getDeskId(), desk));
            log.info("Loaded {} desks from MongoDB", desks.size());

            List<Trader> traders = traderRepository.findAll();
            traders.forEach(trader -> tradersCache.put(trader.getTraderId(), trader));
            log.info("Loaded {} traders from MongoDB", traders.size());

            List<DeskNotionalLimit> deskNotionalLimits = deskNotionalLimitRepository.findAll();
            deskNotionalLimits.forEach(deskNotionalLimit -> deskNotionalLimitCache.put(deskNotionalLimit.getDeskId(), deskNotionalLimit));
            log.info("Loaded {} desk notional limits from MongoDB", deskNotionalLimits.size());

            List<TraderNotionalLimit> tradersNotionalLimits = traderNotionalLimitRepository.findAll();
            tradersNotionalLimits.forEach(traderNotionalLimit -> traderNotionalLimitCache.put(traderNotionalLimit.getTraderId(), traderNotionalLimit));
            log.info("Loaded {} trader notional limits from MongoDB", tradersNotionalLimits.size());

            deskTradersCache.putAll(tradersNotionalLimits.stream().collect(Collectors.groupingBy(TraderNotionalLimit::getTraderId)));
            log.info("Initialized desk traders cache with {} entries", deskTradersCache.size());
        }
        catch (Exception e)
        {
            log.error("ERR-201: Failed to initialize caches from MongoDB", e);
            throw new RuntimeException("Failed to initialize trading data", e);
        }
    }
    
    @Transactional
    public DeskNotionalLimit saveDeskNotionalLimit(DeskNotionalLimit deskNotionalLimit)
    {
        try
        {
            DeskNotionalLimit savedDeskNotionalLimit = deskNotionalLimitRepository.save(deskNotionalLimit);
            deskNotionalLimitCache.put(savedDeskNotionalLimit.getDeskId(), savedDeskNotionalLimit);
            log.info("Saved desk notional with ID: {} to MongoDB and cache", savedDeskNotionalLimit.getDeskId());
            return savedDeskNotionalLimit;
        }
        catch (Exception e)
        {
            log.error("ERR-202: Failed to save desk: {}", deskNotionalLimit.getDeskId(), e);
            throw e;
        }
    }
    
    @Transactional
    public TraderNotionalLimit saveTraderNotionalLimit(TraderNotionalLimit traderNotionalLimit)
    {
        try
        {
            TraderNotionalLimit savedTraderNotionalLimit = traderNotionalLimitRepository.save(traderNotionalLimit);
            traderNotionalLimitCache.put(savedTraderNotionalLimit.getTraderId(), savedTraderNotionalLimit);
            deskTradersCache.computeIfAbsent(savedTraderNotionalLimit.getTraderId(), k -> new java.util.ArrayList<>()).add(savedTraderNotionalLimit);
            log.info("Saved trader notional with ID: {} to MongoDB and cache", savedTraderNotionalLimit.getTraderId());
            return savedTraderNotionalLimit;
        }
        catch (Exception e)
        {
            log.error("ERR-203: Failed to save trader: {}", traderNotionalLimit.getTraderId(), e);
            throw e;
        }
    }
    
    @Transactional
    public void deleteDeskNotionalLimit(UUID deskId)
    {
        try
        {
            deskNotionalLimitRepository.deleteById(deskId);
            deskNotionalLimitCache.remove(deskId);
            deskTradersCache.remove(deskId);
            log.info("Deleted desk and its limits with ID: {} from MongoDB and cache", deskId);
        }
        catch (Exception e)
        {
            log.error("ERR-204: Failed to delete desk: {}", deskId, e);
            throw e;
        }
    }
    
    @Transactional
    public void deleteTraderNotionalLimit(UUID traderId)
    {
        try
        {
            TraderNotionalLimit trader = traderNotionalLimitCache.get(traderId);
            if (trader != null)
            {
                deskTradersCache.getOrDefault(trader.getTraderId(), new java.util.ArrayList<>())
                    .removeIf(t -> t.getTraderId().equals(traderId));
            }
            
            traderNotionalLimitRepository.deleteById(traderId);
            traderNotionalLimitCache.remove(traderId);
            log.info("Deleted trader with ID: {} from MongoDB and cache", traderId);
        }
        catch (Exception e)
        {
            log.error("ERR-205: Failed to delete trader: {}", traderId, e);
            throw e;
        }
    }
    
    public DeskNotionalLimit getDeskNotionalLimit(UUID deskId)
    {
        return deskNotionalLimitCache.get(deskId);
    }
    
    public TraderNotionalLimit getTraderNotionalLimit(UUID traderId)
    {
        return traderNotionalLimitCache.get(traderId);
    }
    
    public List<TraderNotionalLimit> getDeskTraderNotionalLimits(UUID deskId)
    {
        return deskTradersCache.getOrDefault(deskId, new java.util.ArrayList<>());
    }
    
    public boolean deskNotionalLimitExists(UUID deskId)
    {
        return deskNotionalLimitCache.containsKey(deskId);
    }
    
    public boolean traderNotionalLimitExists(UUID traderId)
    {
        return traderNotionalLimitCache.containsKey(traderId);
    }
    
    public List<DeskNotionalLimit> getAllDeskNotionalLimits()
    {
        return new ArrayList<>(deskNotionalLimitCache.values());
    }
    
    public boolean hasTradersForDesk(UUID deskId)
    {
        return !getDeskTraderNotionalLimits(deskId).isEmpty();
    }

    public List<TraderNotionalLimit> getAllTraderNotionalLimits()
    {
        return new ArrayList<>(traderNotionalLimitCache.values());
    }

    public DeskNotionalLimit getDeskNotionalLimitById(UUID deskId)
    {
        return deskNotionalLimitCache.get(deskId);
    }

    public Trader getTraderById(UUID traderId)
    {
        return tradersCache.get(traderId);
    }

    public Desk getDeskById(UUID deskId)
    {
        return desksCache.get(deskId);
    }

    public Optional<Desk> findDeskByTraderId(UUID traderId)
    {
        return desksCache.values().stream().filter(desk -> desk.getTraders().contains(traderId)).findFirst();
    }

    public String findTraderFullNameByUserId(String userId)
    {
        for (Trader trader : tradersCache.values())
        {
            if (trader.getUserId().equals(userId))
                return trader.getFirstName() + " " + trader.getLastName();
        }

        return userId;
    }

    public Optional<Trader> findTraderByUserId(String userId)
    {
        return tradersCache.values().stream().filter(trader -> trader.getUserId().equals(userId)).findFirst();
    }

}