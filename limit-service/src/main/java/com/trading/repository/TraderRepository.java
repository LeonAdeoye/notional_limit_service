package com.trading.repository;

import com.trading.model.Trader;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.UUID;

public interface TraderRepository extends MongoRepository<Trader, UUID>
{
}
