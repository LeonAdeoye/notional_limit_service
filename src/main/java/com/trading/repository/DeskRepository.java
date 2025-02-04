package com.trading.repository;

import com.trading.model.Desk;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DeskRepository extends MongoRepository<Desk, UUID> {
    // Spring Data MongoDB will implement basic CRUD operations
} 