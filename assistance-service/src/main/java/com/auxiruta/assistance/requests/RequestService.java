package com.auxiruta.assistance.requests;

import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.oauth2.jwt.Jwt;
import com.auxiruta.commons.security.TokenSupport;
import static com.auxiruta.assistance.requests.DomainTypes.*;
import static com.auxiruta.assistance.requests.RequestDtos.*;

@Service
@Transactional(readOnly = true)
public class RequestService {
  private final VehicleRepository vehicles;
  private final RequestRepository requests;
  private final HistoryRepository histories;
  private final RulesConfiguration rules;
  private final Clock clock;
  private final EvaluationReadService evaluations;

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.jdbc.core.JdbcTemplate jdbc;

  public RequestService(
      VehicleRepository vehicles,
      RequestRepository requests,
      HistoryRepository histories,
      RulesConfiguration rules,
      Clock clock,
      EvaluationReadService evaluations) {
    this.vehicles = vehicles;
    this.requests = requests;
    this.histories = histories;
    this.rules = rules;
    this.clock = clock;
    this.evaluations = evaluations;
  }

  private Instant now() {
    return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  }

  private static ResponseStatusException missing() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  static void require(boolean ok, HttpStatus status) {
    if (!ok) throw new ResponseStatusException(status);
  }

  private static void key(String value) {
    require(value != null && !value.isBlank() && value.length() <= 100, HttpStatus.BAD_REQUEST);
  }

  static String plate(String value) {
    String p = value.replace(" ", "").replace("-", "").toUpperCase(Locale.ROOT);
    require(p.matches("[A-Z0-9]{6,10}"), HttpStatus.BAD_REQUEST);
    return p;
  }

  private static Pageable page(int p, int size, String sort) {
    require(p >= 0 && size >= 1 && size <= 100, HttpStatus.BAD_REQUEST);
    return PageRequest.of(p, size, Sort.by(sort).and(Sort.by("id")));
  }

  private static <T> PageView<T> paged(Page<T> p) {
    return new PageView<>(
        p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
  }

  private Vehicle owned(UUID id, String subject) {
    return vehicles.findByIdAndOwnerSubject(id, subject).orElseThrow(RequestService::missing);
  }

  private Vehicle locked(UUID id, String subject) {
    return vehicles.lockOwned(id, subject).orElseThrow(RequestService::missing);
  }

  private void free(UUID id) {
    require(!requests.hasActive(id), HttpStatus.CONFLICT);
  }

  private boolean admin(Jwt jwt) {
    return TokenSupport.permits(jwt, false, "ADMIN");
  }

  private boolean driver(Jwt jwt) {
    return TokenSupport.permits(jwt, false, "DRIVER");
  }

  private AssistanceRequest visible(UUID id, Jwt jwt) {
    AssistanceRequest req = requests.findById(id).orElseThrow(RequestService::missing);
    if (!admin(jwt))
      require(
          (driver(jwt) && req.driverSubject.equals(jwt.getSubject()))
              || (TokenSupport.permits(jwt, false, "OPERATOR")
                  && jdbc.queryForObject(
                          "select count(*) from assignments where request_id=? and operator_subject=?",
                          Long.class,
                          id,
                          jwt.getSubject())
                      > 0),
          HttpStatus.NOT_FOUND);
    return req;
  }

  private VehicleView view(Vehicle v) {
    return new VehicleView(
        v.id,
        v.licensePlate,
        v.brand,
        v.model,
        v.vehicleType,
        v.declaredWeightKg,
        v.registrationStatus,
        v.createdAt,
        v.updatedAt,
        v.version);
  }

  private RequestView view(AssistanceRequest r) {
    return new RequestView(
        r.id,
        r.vehicle.getId(),
        r.status,
        r.firstAssignedAt,
        r.closureReasonCode,
        r.originLatitude,
        r.originLongitude,
        r.destinationLatitude,
        r.destinationLongitude,
        r.declaredVehicleType,
        r.declaredWeightKg,
        r.mobilityCondition,
        r.description,
        r.searchAttemptNumber,
        r.searchDeadline,
        r.configurationRevision,
        r.configurationSnapshot,
        r.createdAt,
        r.updatedAt,
        r.closedAt,
        r.version,
        r.resourceReleasePending,
        evaluations.latest(r.id));
  }

  private HistoryView view(RequestStatusHistory h) {
    return new HistoryView(
        h.id,
        h.request.getId(),
        h.previousStatus,
        h.newStatus,
        h.actorType,
        h.actorSubject,
        h.reason,
        h.details,
        h.operationKey,
        h.occurredAt);
  }

  public VehicleView vehicle(UUID id, String subject) {
    return view(owned(id, subject));
  }

  public PageView<VehicleView> vehicles(String subject, int p, int size, Registration status) {
    Pageable pageable = page(p, size, "createdAt");
    return paged(
        (status == null
                ? vehicles.findByOwnerSubject(subject, pageable)
                : vehicles.findByOwnerSubjectAndRegistrationStatus(subject, status, pageable))
            .map(this::view));
  }

  @Transactional
  public VehicleView createVehicle(String subject, CreateVehicle d) {
    Vehicle v = new Vehicle();
    v.ownerSubject = subject;
    v.licensePlate = plate(d.licensePlate());
    v.brand = d.brand();
    v.model = d.model();
    v.vehicleType = d.vehicleType();
    v.declaredWeightKg = d.declaredWeightKg();
    v.createdAt = v.updatedAt = now();
    vehicles.saveAndFlush(v);
    return view(v);
  }

  @Transactional
  public VehicleView editVehicle(UUID id, String subject, EditVehicle d) {
    require(
        d.licensePlate() != null
            || d.brand() != null
            || d.model() != null
            || d.vehicleType() != null
            || d.declaredWeightKg() != null,
        HttpStatus.BAD_REQUEST);
    Vehicle v = locked(id, subject);
    free(id);
    if (d.licensePlate() != null) v.licensePlate = plate(d.licensePlate());
    if (d.brand() != null) v.brand = d.brand();
    if (d.model() != null) v.model = d.model();
    if (d.vehicleType() != null) v.vehicleType = d.vehicleType();
    if (d.declaredWeightKg() != null) v.declaredWeightKg = d.declaredWeightKg();
    v.updatedAt = now();
    vehicles.flush();
    return view(v);
  }

  @Transactional
  public VehicleView registration(UUID id, String subject, RegistrationChange d) {
    Vehicle v = locked(id, subject);
    free(id);
    v.registrationStatus = d.status();
    v.updatedAt = now();
    vehicles.flush();
    return view(v);
  }

  static String digest(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String coordinate(BigDecimal n) {
    return n.stripTrailingZeros().toPlainString();
  }

  static String fingerprint(CreateRequest d) {
    // Length-prefixed fields prevent ambiguities when free text contains delimiters.
    List<String> fields =
        List.of(
            d.vehicleId().toString(),
            coordinate(d.originLatitude()),
            coordinate(d.originLongitude()),
            coordinate(d.destinationLatitude()),
            coordinate(d.destinationLongitude()),
            d.mobilityCondition().name(),
            d.description().strip());
    return digest(
        fields.stream()
            .map(v -> v.length() + ":" + v)
            .collect(java.util.stream.Collectors.joining()));
  }

  @Transactional
  public Created createRequest(String subject, String operationKey, CreateRequest d) {
    key(operationKey);
    require(
        d.originLatitude().compareTo(d.destinationLatitude()) != 0
            || d.originLongitude().compareTo(d.destinationLongitude()) != 0,
        HttpStatus.BAD_REQUEST);
    String fingerprint = fingerprint(d);
    // Locking the vehicle also serializes competing creation, edits and cancellation.
    Vehicle v = locked(d.vehicleId(), subject);
    var previous = requests.findByDriverSubjectAndIdempotencyKey(subject, operationKey);
    if (previous.isPresent()) {
      require(previous.get().requestFingerprint.equals(fingerprint), HttpStatus.CONFLICT);
      return new Created(view(previous.get()), false);
    }
    require(v.registrationStatus == Registration.ACTIVE, HttpStatus.CONFLICT);
    free(v.id);
    AssistanceRequest req = new AssistanceRequest();
    req.vehicle = v;
    req.driverSubject = subject;
    req.originLatitude = d.originLatitude();
    req.originLongitude = d.originLongitude();
    req.destinationLatitude = d.destinationLatitude();
    req.destinationLongitude = d.destinationLongitude();
    req.declaredVehicleType = v.vehicleType;
    req.declaredWeightKg = v.declaredWeightKg;
    req.mobilityCondition = d.mobilityCondition();
    req.description = d.description().strip();
    req.configurationRevision = rules.revision();
    req.configurationSnapshot = rules.snapshot();
    req.idempotencyKey = operationKey;
    req.requestFingerprint = fingerprint;
    req.createdAt = req.updatedAt = now();
    req.searchDeadline = req.createdAt.plusSeconds(rules.searchSeconds());
    req = requests.saveAndFlush(req);
    event(req, null, Status.REQUESTED, subject, "CREATED", null, null);
    return new Created(view(req), true);
  }

  private RequestStatusHistory event(
      AssistanceRequest req,
      Status previous,
      Status next,
      String subject,
      String reason,
      String operationKey,
      String fingerprint) {
    RequestStatusHistory h = new RequestStatusHistory();
    h.request = req;
    h.previousStatus = previous;
    h.newStatus = next;
    h.actorSubject = subject;
    h.actorType = "DRIVER";
    h.reason = reason;
    h.operationKey = operationKey;
    h.requestFingerprint = fingerprint;
    h.occurredAt = now();
    h.details = next == Status.REQUESTED ? "searchDeadline=" + req.searchDeadline : null;
    histories.saveAndFlush(h);
    org.springframework.transaction.support.TransactionSynchronizationManager
        .registerSynchronization(
            new org.springframework.transaction.support.TransactionSynchronization() {
              @Override
              public void afterCommit() {
                org.slf4j.LoggerFactory.getLogger(RequestService.class)
                    .info(
                        "Request state changed requestId={} from={} to={}", req.id, previous, next);
              }
            });
    return h;
  }

  public RequestView request(UUID id, Jwt jwt) {
    return view(visible(id, jwt));
  }

  public PageView<RequestView> requests(Jwt jwt, int p, int size, Status status) {
    Pageable pageable = page(p, size, "createdAt");
    if (!admin(jwt) && !driver(jwt))
      return paged(
          requests
              .forOperator(
                  jwt.getSubject(),
                  status == null ? null : status.name(),
                  PageRequest.of(p, size, Sort.by("created_at").and(Sort.by("id"))))
              .map(this::view));
    return paged(
        requests.visible(admin(jwt) ? null : jwt.getSubject(), status, pageable).map(this::view));
  }

  public PageView<HistoryView> history(UUID id, Jwt jwt, int p, int size) {
    visible(id, jwt);
    return paged(histories.findByRequestId(id, page(p, size, "occurredAt")).map(this::view));
  }

  static boolean cancellable(AssistanceRequest r) {
    return r.firstAssignedAt == null
        && EnumSet.of(
                Status.REQUESTED,
                Status.PENDING_EVALUATION,
                Status.AWAITING_ACCEPTANCE,
                Status.SEARCHING_ALTERNATIVE)
            .contains(r.status);
  }

  @Transactional
  public HistoryView cancel(UUID id, String subject, String operationKey, Cancellation d) {
    key(operationKey);
    UUID vehicleId = requests.vehicleId(id).orElseThrow(RequestService::missing);
    locked(vehicleId, subject);
    AssistanceRequest req = requests.lockById(id).orElseThrow(RequestService::missing);
    require(req.driverSubject.equals(subject), HttpStatus.NOT_FOUND);
    String fingerprint = digest(d.reason().strip());
    var previous = histories.findByRequestIdAndOperationKey(id, operationKey);
    if (previous.isPresent()) {
      require(
          previous.get().actorSubject.equals(subject)
              && previous.get().requestFingerprint.equals(fingerprint),
          HttpStatus.CONFLICT);
      return view(previous.get());
    }
    require(cancellable(req), HttpStatus.CONFLICT);
    if (jdbc.queryForObject(
            "select count(*) from pending_actions p join assignments a on a.id=p.assignment_id where a.request_id=? and p.kind='CONFIRM' and p.status<>'COMPLETED'",
            Long.class,
            id)
        > 0)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "ACCEPTANCE_CONFIRMATION_PENDING");
    var assignmentIds =
        jdbc.queryForList(
            "select id from assignments where request_id=? and status in ('RESERVING','PROPOSED')",
            UUID.class,
            id);
    for (UUID assignment : assignmentIds) {
      jdbc.update(
          "update assignments set status='CANCELLED',closed_at=? where id=?",
          java.sql.Timestamp.from(now()),
          assignment);
      // Resolve an uncertain RESERVE first; its completion creates the compensating RELEASE.
      long pending =
          jdbc.queryForObject(
              "select count(*) from pending_actions where assignment_id=? and kind='RESERVE' and status<>'COMPLETED'",
              Long.class,
              assignment);
      if (pending == 0)
        jdbc.update(
            "insert into pending_actions(id,assignment_id,kind,status,created_at,updated_at) values (?,?,'RELEASE','PENDING',?,?) on conflict(assignment_id,kind) do nothing",
            UUID.randomUUID(),
            assignment,
            java.sql.Timestamp.from(now()),
            java.sql.Timestamp.from(now()));
    }
    req.resourceReleasePending = req.resourceReleasePending || !assignmentIds.isEmpty();
    Status old = req.status;
    req.status = Status.CANCELLED;
    req.closureReasonCode = "DRIVER_CANCELLED";
    req.closedAt = req.updatedAt = now();
    requests.flush();
    return view(
        event(req, old, Status.CANCELLED, subject, d.reason().strip(), operationKey, fingerprint));
  }
}
