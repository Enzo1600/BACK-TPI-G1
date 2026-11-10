package com.auxiruta.fleet.catalog;

import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.auxiruta.commons.GeoMath;
import static com.auxiruta.fleet.catalog.FleetTypes.*;

@Service
public class CandidateService {
  private final TowTruckRepository trucks;
  private final Clock clock;

  public CandidateService(TowTruckRepository trucks, Clock clock) {
    this.trucks = trucks;
    this.clock = clock;
  }

  public record Candidate(
      UUID towTruckId,
      UUID operatorId,
      String operatorSubject,
      BigDecimal capacityKg,
      Set<VehicleType> supportedTypes,
      boolean supportsNonRollable,
      String qualificationCategory,
      Instant qualificationValidFrom,
      Instant qualificationValidUntil,
      Instant availableSince,
      BigDecimal latitude,
      BigDecimal longitude,
      Instant locationUpdatedAt,
      double straightDistanceM) {}

  @Transactional(readOnly = true)
  public List<Candidate> find(
      BigDecimal latitude,
      BigDecimal longitude,
      VehicleType type,
      BigDecimal weight,
      boolean nonRollable) {
    Instant now = clock.instant();
    return trucks
        .findCandidates(Operational.ACTIVE, Registration.ACTIVE, Availability.AVAILABLE)
        .stream()
        .filter(
            t ->
                t.maximumLoadKg.compareTo(weight) >= 0
                    && t.supportedTypes.contains(type)
                    && (!nonRollable || t.supportsNonRollable))
        .filter(
            t ->
                t.latitude != null
                    && t.longitude != null
                    && t.locationUpdatedAt != null
                    && !t.locationUpdatedAt.isAfter(now)
                    && !t.locationUpdatedAt.isBefore(now.minusSeconds(300)))
        .filter(
            t ->
                GeoMath.distance(
                        -31.4167,
                        -64.1833,
                        t.latitude.doubleValue(),
                        t.longitude.doubleValue(),
                        6371000)
                    <= 15000)
        .map(
            t -> {
              Operator o = org.hibernate.Hibernate.unproxy(t.operator, Operator.class);
              return new Candidate(
                  t.id,
                  o.id,
                  o.keycloakSubject,
                  t.maximumLoadKg,
                  Set.copyOf(t.supportedTypes),
                  t.supportsNonRollable,
                  o.qualificationCategory == null ? null : o.qualificationCategory.name(),
                  o.qualificationValidFrom,
                  o.qualificationValidUntil,
                  o.availableSince,
                  t.latitude,
                  t.longitude,
                  t.locationUpdatedAt,
                  GeoMath.distance(
                      latitude.doubleValue(),
                      longitude.doubleValue(),
                      t.latitude.doubleValue(),
                      t.longitude.doubleValue(),
                      6371000));
            })
        .filter(
            c ->
                "LIGHT_TOWING".equals(c.qualificationCategory())
                    && c.qualificationValidFrom() != null
                    && c.qualificationValidUntil() != null
                    && !c.qualificationValidFrom().isAfter(now)
                    && c.qualificationValidUntil().isAfter(now)
                    && c.availableSince() != null)
        .sorted(
            Comparator.comparingDouble(Candidate::straightDistanceM)
                .thenComparing(c -> c.towTruckId().toString()))
        .limit(5)
        .toList();
  }
}
