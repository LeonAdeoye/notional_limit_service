package com.trading.repository;

import com.trading.model.Trader;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TraderRepository extends MongoRepository<Trader, UUID> {
    List<Trader> findByDeskId(UUID deskId);
    boolean existsByDeskId(UUID deskId);
} 