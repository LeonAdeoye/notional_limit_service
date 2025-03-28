package com.trading.repository;

import com.trading.model.Desk;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface DeskRepository extends MongoRepository<Desk, UUID> {
    Desk findByName(String name);
} 