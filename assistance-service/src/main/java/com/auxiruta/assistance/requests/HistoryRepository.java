package com.auxiruta.assistance.requests;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;

public interface HistoryRepository extends JpaRepository<RequestStatusHistory, UUID> {
  Page<RequestStatusHistory> findByRequestId(UUID id, Pageable page);

  Optional<RequestStatusHistory> findByRequestIdAndOperationKey(UUID id, String key);
}
