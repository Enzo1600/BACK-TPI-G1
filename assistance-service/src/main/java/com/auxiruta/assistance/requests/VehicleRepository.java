package com.auxiruta.assistance.requests;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {
  Optional<Vehicle> findByIdAndOwnerSubject(UUID id, String subject);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select v from Vehicle v where v.id=:id and v.ownerSubject=:subject")
  Optional<Vehicle> lockOwned(UUID id, String subject);

  Page<Vehicle> findByOwnerSubject(String subject, Pageable page);

  Page<Vehicle> findByOwnerSubjectAndRegistrationStatus(
      String subject, DomainTypes.Registration status, Pageable page);
}
