package com.foodie.ecommerce.repository;

import com.foodie.ecommerce.model.ClickstreamLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ClickstreamRepository extends MongoRepository<ClickstreamLog, String> {
    List<ClickstreamLog> findTop20ByOrderByTimestampDesc();
    long countByUserId(Long userId);
}