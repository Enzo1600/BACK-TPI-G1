package com.auxiruta.assistance.requests;

import java.util.*;
import java.time.*;
import java.math.*;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.*;
import com.auxiruta.commons.GeoMath;
import com.auxiruta.assistance.routing.*;
import static com.auxiruta.assistance.routing.RouteProvider.*;

@Service
public class EvaluationService {
  private final EvaluationStore store;
  private final RouteProvider routes;
  private final FleetClient fleet;
  private final Clock clock;
  private final ObjectMapper mapper;

  public EvaluationService(
      EvaluationStore store,
      RouteProvider routes,
      FleetClient fleet,
      Clock clock,
      ObjectMapper mapper) {
    this.store = store;
    this.routes = routes;
    this.fleet = fleet;
    this.clock = clock;
    this.mapper = mapper;
  }

  static int adjusted(BigDecimal seconds, BigDecimal factor) {
    return seconds.multiply(factor).setScale(0, RoundingMode.CEILING).intValueExact();
  }

  static boolean inCoverage(BigDecimal lat, BigDecimal lon, JsonNode rules) {
    var c = rules.path("coverage");
    return GeoMath.distance(
            c.path("center-latitude").asDouble(),
            c.path("center-longitude").asDouble(),
            lat.doubleValue(),
            lon.doubleValue(),
            c.path("haversine-earth-radius-meters").asDouble())
        <= c.path("radius-meters").asDouble();
  }

  static int value(JsonNode rules, String group, String name) {
    return rules.path(group).path(name).asInt();
  }

  private static boolean same(Point a, Point b) {
    return a.latitude().compareTo(b.latitude()) == 0 && a.longitude().compareTo(b.longitude()) == 0;
  }

  public void evaluate(UUID id) {
    EvaluationStore.Input input = store.load(id);
    if (input == null) return;
    JsonNode rules;
    try {
      rules = mapper.readTree(input.rules());
    } catch (Exception e) {
      throw new IllegalStateException("INVALID_RULE_SNAPSHOT");
    }
    Evaluation e = new Evaluation();
    e.searchAttemptNumber = input.attemptNumber();
    e.originLatitude = input.originLat();
    e.originLongitude = input.originLon();
    e.destinationLatitude = input.destinationLat();
    e.destinationLongitude = input.destinationLon();
    e.vehicleType = input.vehicleType();
    e.declaredWeightKg = input.weight();
    e.mobilityCondition = input.mobility();
    e.configurationRevision = rules.path("rules-version").asText();
    e.maximumArrivalSeconds = value(rules, "routing", "max-arrival-budget-seconds");
    e.loadingSeconds =
        value(
            rules,
            "execution",
            input.mobility().equals("ROLLABLE")
                ? "rollable-loading-seconds"
                : "non-rollable-loading-seconds");
    e.unloadingSeconds = value(rules, "execution", "unloading-seconds");
    e.operationalMarginSeconds = value(rules, "routing", "planning-contingency-seconds");
    e.coverageAndAdmissionSummary = "AR-MVP-5 admission";
    List<CandidateEvaluation> details = new ArrayList<>();
    if (!inCoverage(input.originLat(), input.originLon(), rules)
        || !inCoverage(input.destinationLat(), input.destinationLon(), rules)) {
      e.result = "REJECTED";
      e.reasonCode = "OUTSIDE_COVERAGE";
      store.save(input, e, details);
      return;
    }
    e.coverageAndAdmissionSummary = "Origin and destination inside coverage";
    int age = value(rules, "routing", "max-route-age-seconds"),
        snap = value(rules, "routing", "max-snap-distance-meters");
    BigDecimal factor = rules.path("routing").path("duration-factor").decimalValue();
    Point origin = new Point(input.originLat(), input.originLon()),
        destination = new Point(input.destinationLat(), input.destinationLon());
    try {
      Route transport = routes.route(origin, destination, e.configurationRevision, age, snap);
      e.transportDistanceM = transport.distanceM();
      e.transportDurationSeconds = transport.durationSeconds();
      e.adjustedTransportSeconds = adjusted(transport.durationSeconds(), factor);
      e.routeObtainedAt = transport.obtainedAt();
      if (transport
                  .distanceM()
                  .compareTo(BigDecimal.valueOf(value(rules, "routing", "min-transport-meters")))
              < 0
          || transport
                  .distanceM()
                  .compareTo(BigDecimal.valueOf(value(rules, "routing", "max-transport-meters")))
              > 0) {
        e.result = "REJECTED";
        e.reasonCode = "TRANSPORT_DISTANCE";
        store.save(input, e, details);
        return;
      }
      if (e.adjustedTransportSeconds > value(rules, "routing", "max-adjusted-transport-seconds")) {
        e.result = "REJECTED";
        e.reasonCode = "TRANSPORT_DURATION";
        store.save(input, e, details);
        return;
      }
      List<FleetClient.Candidate> resources =
          fleet.candidates(
              input.originLat(),
              input.originLon(),
              input.vehicleType(),
              input.weight(),
              input.mobility().equals("NON_ROLLABLE"));
      Map<UUID, Instant> freeSince = new HashMap<>();
      boolean pending = false;
      for (var resource : resources) {
        if (resource.towTruckId() == null
            || resource.operatorId() == null
            || resource.operatorSubject() == null
            || resource.capacityKg() == null
            || resource.supportedTypes() == null
            || resource.qualificationCategory() == null
            || resource.qualificationValidFrom() == null
            || resource.qualificationValidUntil() == null
            || resource.availableSince() == null
            || resource.latitude() == null
            || resource.longitude() == null
            || resource.locationUpdatedAt() == null)
          throw new IllegalStateException("FLEET_INVALID_RESPONSE");
        CandidateEvaluation c = new CandidateEvaluation();
        c.towTruckId = resource.towTruckId();
        c.operatorId = resource.operatorId();
        c.operatorSubject = resource.operatorSubject();
        c.truckCapacityKg = resource.capacityKg();
        c.qualificationCategory = resource.qualificationCategory();
        c.qualificationValidUntil = resource.qualificationValidUntil();
        c.truckLatitude = resource.latitude();
        c.truckLongitude = resource.longitude();
        c.truckLocationUpdatedAt = resource.locationUpdatedAt();
        c.compatible =
            resource.capacityKg().compareTo(input.weight()) >= 0
                && resource.supportedTypes().contains(input.vehicleType())
                && (!input.mobility().equals("NON_ROLLABLE") || resource.supportsNonRollable());
        c.transportDistanceM = e.transportDistanceM;
        c.transportDurationSeconds = e.transportDurationSeconds;
        c.adjustedTransportSeconds = e.adjustedTransportSeconds;
        c.transportRouteObtainedAt = e.routeObtainedAt;
        details.add(c);
        freeSince.put(c.towTruckId, resource.availableSince());
        Instant validation = clock.instant();
        if (!c.compatible) {
          c.reasonCode = "INCOMPATIBLE";
          continue;
        }
        if (!inCoverage(c.truckLatitude, c.truckLongitude, rules)
            || c.truckLocationUpdatedAt.isAfter(validation)
            || c.truckLocationUpdatedAt.isBefore(
                validation.minusSeconds(value(rules, "fleet", "max-location-age-seconds")))) {
          c.reasonCode = "LOCATION_INVALID";
          continue;
        }
        try {
          Point truck = new Point(c.truckLatitude, c.truckLongitude);
          Route approach =
              same(truck, origin)
                  ? new Route(BigDecimal.ZERO, BigDecimal.ZERO, transport.obtainedAt())
                  : routes.route(truck, origin, e.configurationRevision, age, snap);
          c.approachDistanceM = approach.distanceM();
          c.approachDurationSeconds = approach.durationSeconds();
          c.adjustedApproachSeconds = adjusted(approach.durationSeconds(), factor);
          c.approachRouteObtainedAt = approach.obtainedAt();
          int dispatch = value(rules, "execution", "dispatch-deadline-seconds"),
              arrivalMargin = value(rules, "routing", "arrival-margin-seconds");
          if (c.approachDistanceM.compareTo(
                      BigDecimal.valueOf(value(rules, "routing", "max-approach-meters")))
                  > 0
              || (long) dispatch + c.adjustedApproachSeconds + arrivalMargin
                  > e.maximumArrivalSeconds) {
            c.reasonCode = "APPROACH_LIMIT";
            continue;
          }
          validation = clock.instant();
          long remaining =
              Math.min(
                  value(rules, "fleet", "hold-seconds"),
                  Duration.between(validation, input.deadline()).getSeconds());
          if (remaining < value(rules, "search", "minimum-offer-window-seconds")) {
            c.reasonCode = "SEARCH_WINDOW_TOO_SHORT";
            continue;
          }
          c.estimatedServiceEndAt =
              validation
                  .plusSeconds(
                      remaining
                          + dispatch
                          + c.adjustedApproachSeconds
                          + e.loadingSeconds
                          + e.adjustedTransportSeconds
                          + e.unloadingSeconds
                          + e.operationalMarginSeconds)
                  .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
          if (!"LIGHT_TOWING".equals(c.qualificationCategory)
              || resource.qualificationValidFrom().isAfter(validation)
              || !c.qualificationValidUntil.isAfter(c.estimatedServiceEndAt)) {
            c.reasonCode = "QUALIFICATION_EXPIRES";
            continue;
          }
          if (c.truckLocationUpdatedAt.isBefore(
                  validation.minusSeconds(value(rules, "fleet", "max-location-age-seconds")))
              || c.approachRouteObtainedAt.isBefore(validation.minusSeconds(age))
              || c.transportRouteObtainedAt.isBefore(validation.minusSeconds(age))) {
            c.reasonCode = "STALE_INPUT";
            pending = true;
            continue;
          }
          c.eligible = true;
          c.reasonCode = "ELIGIBLE";
        } catch (RoutingFailure failure) {
          c.reasonCode = failure.reason().name();
          pending |= !failure.domainRejection();
        }
      }
      Instant finished = clock.instant();
      for (var c : details)
        if (c.eligible
            && (c.truckLocationUpdatedAt.isBefore(
                    finished.minusSeconds(value(rules, "fleet", "max-location-age-seconds")))
                || c.approachRouteObtainedAt.isBefore(finished.minusSeconds(age))
                || c.transportRouteObtainedAt.isBefore(finished.minusSeconds(age)))) {
          c.eligible = false;
          c.reasonCode = "STALE_INPUT";
          pending = true;
        }
      List<CandidateEvaluation> ranked =
          details.stream()
              .filter(c -> c.eligible)
              .sorted(
                  Comparator.comparingInt((CandidateEvaluation c) -> c.adjustedApproachSeconds)
                      .thenComparing(c -> freeSince.get(c.towTruckId))
                      .thenComparing(c -> c.towTruckId.toString()))
              .toList();
      for (int i = 0; i < ranked.size(); i++) ranked.get(i).rankingPosition = i + 1;
      e.result = !ranked.isEmpty() ? "VIABLE" : pending ? "PENDING_DEPENDENCY" : "SEARCHING";
      e.reasonCode =
          !ranked.isEmpty()
              ? "VIABLE_CANDIDATE"
              : pending
                  ? "DEPENDENCY_UNAVAILABLE"
                  : resources.isEmpty() ? "NO_AVAILABLE_RESOURCE" : "NO_VIABLE_CANDIDATE";
    } catch (RoutingFailure failure) {
      e.result = failure.domainRejection() ? "REJECTED" : "PENDING_DEPENDENCY";
      e.reasonCode = failure.reason().name();
    } catch (Exception failure) {
      e.result = "PENDING_DEPENDENCY";
      e.reasonCode = "FLEET_OR_EVALUATION_UNAVAILABLE";
      e.reasonDetail = failure.getClass().getSimpleName();
    }
    store.save(input, e, details);
  }
}
