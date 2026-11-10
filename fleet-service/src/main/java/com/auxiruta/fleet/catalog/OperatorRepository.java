package com.auxiruta.fleet.catalog;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;

public interface OperatorRepository extends JpaRepository<Operator, UUID> {
  Optional<Operator> findByKeycloakSubject(String subject);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Operator o where o.keycloakSubject = :subject")
  Optional<Operator> lockBySubject(String subject);

  Page<Operator> findByRegistrationStatus(FleetTypes.Registration status, Pageable page);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Operator o where o.id = :id")
  Optional<Operator> lockById(UUID id);
}
