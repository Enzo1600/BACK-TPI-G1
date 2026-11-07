package com.auxiruta.fleet.catalog;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TruckStatusHistoryRepository extends JpaRepository<TruckStatusHistory,UUID> {
 Optional<TruckStatusHistory> findByOperationKey(String key);
}
