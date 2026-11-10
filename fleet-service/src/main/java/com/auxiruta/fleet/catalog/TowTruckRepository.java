package com.auxiruta.fleet.catalog;

import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;

public interface TowTruckRepository extends JpaRepository<TowTruck, UUID> {
  @EntityGraph(attributePaths = {"operator", "supportedTypes"})
  @Query(
      "select t from TowTruck t where t.operationalStatus=:operational and t.operator.registrationStatus=:registration and t.operator.availabilityStatus=:availability and not exists (select r.id from ResourceReservation r where (r.towTruckId=t.id or r.operatorId=t.operator.id) and r.status in ('HELD','CONFIRMED'))")
  List<TowTruck> findCandidates(
      FleetTypes.Operational operational,
      FleetTypes.Registration registration,
      FleetTypes.Availability availability);

  Optional<TowTruck> findByOperatorId(UUID id);

  @Query("select t.id from TowTruck t where t.operator.keycloakSubject = :subject")
  Optional<UUID> assignedId(String subject);

  Page<TowTruck> findByOperationalStatus(FleetTypes.Operational status, Pageable page);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from TowTruck t where t.id = :id")
  Optional<TowTruck> lockById(UUID id);
}
