package com.auxiruta.assistance.requests;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {
  Optional<Evaluation> findFirstByRequestIdOrderByEvaluatedAtDescIdDesc(UUID id);
}
