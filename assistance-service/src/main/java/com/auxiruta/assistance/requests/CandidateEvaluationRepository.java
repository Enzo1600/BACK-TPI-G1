package com.auxiruta.assistance.requests;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CandidateEvaluationRepository extends JpaRepository<CandidateEvaluation,UUID> {
 List<CandidateEvaluation> findByEvaluationId(UUID id);
}
