package com.trading.service;

import com.trading.model.Desk;
import com.trading.model.Trader;
import com.trading.repository.DeskRepository;
import com.trading.repository.TraderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradingPersistenceServiceTest {

    @Mock
    private DeskRepository deskRepository;
    
    @Mock
    private TraderRepository traderRepository;

    @InjectMocks
    private TradingPersistenceService service;

    private Desk testDesk;
    private Trader testTrader;
    private UUID deskId;
    private UUID traderId;

    @BeforeEach
    void setUp() {
        deskId = UUID.randomUUID();
        traderId = UUID.randomUUID();
        
        testDesk = new Desk(deskId, "Test Desk");
        testTrader = new Trader(traderId, "Test Trader", deskId);
    }

    @Test
    void initializeCaches_LoadsAllData() {
        when(deskRepository.findAll()).thenReturn(Arrays.asList(testDesk));
        when(traderRepository.findAll()).thenReturn(Arrays.asList(testTrader));

        service.initializeCaches();

        assertTrue(service.deskExists(deskId));
        assertTrue(service.traderExists(traderId));
    }

    @Test
    void saveDesk_CreatesNewDeskWithLimits() {
        when(deskRepository.save(any(Desk.class))).thenReturn(testDesk);
        Desk savedDesk = service.saveDesk(testDesk);
        assertNotNull(savedDesk);
        assertEquals(deskId, savedDesk.getId());
    }

    @Test
    void saveTrader_UpdatesDeskTradersCache() {
        when(traderRepository.save(any(Trader.class))).thenReturn(testTrader);

        Trader savedTrader = service.saveTrader(testTrader);

        assertNotNull(savedTrader);
        assertEquals(traderId, savedTrader.getId());
        List<Trader> deskTraders = service.getDeskTraders(deskId);
        assertTrue(deskTraders.contains(savedTrader));
    }

    @Test
    void deleteDesk_RemovesAllRelatedData() {
        // Arrange
        when(traderRepository.save(any(Trader.class))).thenReturn(testTrader);
        when(deskRepository.save(any(Desk.class))).thenReturn(testDesk);

        service.saveDesk(testDesk);
        service.saveTrader(testTrader);

        // Act
        service.deleteDesk(deskId);

        // Assert
        verify(deskRepository).deleteById(deskId);
        assertFalse(service.deskExists(deskId));
        assertTrue(service.getDeskTraders(deskId).isEmpty());
    }

    @Test
    void deleteTrader_UpdatesDeskTradersCache() {
        when(traderRepository.save(any(Trader.class))).thenReturn(testTrader);
        service.saveTrader(testTrader);

        service.deleteTrader(traderId);

        verify(traderRepository).deleteById(traderId);
        assertFalse(service.traderExists(traderId));
        assertTrue(service.getDeskTraders(deskId).isEmpty());
    }

    @Test
    void hasTradersForDesk_ReturnsCorrectStatus() {
        when(traderRepository.save(testTrader)).thenReturn(testTrader);
        assertFalse(service.hasTradersForDesk(deskId));
        
        service.saveTrader(testTrader);
        
        assertTrue(service.hasTradersForDesk(deskId));
    }

    @Test
    void getAllDesks_ReturnsAllDesks() {
        // Arrange
        UUID id = UUID.randomUUID();
        Desk testDesk = new Desk(id, "Test Desk");
        when(deskRepository.save(testDesk)).thenReturn(testDesk);
        // Act
        service.saveDesk(testDesk);
        // Assert
        List<Desk> allDesks = service.getAllDesks();
        assertFalse(allDesks.isEmpty());
        assertEquals(1, allDesks.size());
        assertEquals(testDesk, allDesks.get(0));
    }
} 