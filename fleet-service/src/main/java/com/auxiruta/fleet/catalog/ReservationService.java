package com.auxiruta.fleet.catalog;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import static com.auxiruta.fleet.catalog.FleetTypes.*;
@Service
@Transactional
public class ReservationService {
 public record Input(@NotNull UUID requestId,@NotNull UUID towTruckId,@NotNull UUID operatorId,@NotNull VehicleType vehicleType,@NotNull @DecimalMin("500") @DecimalMax("3000") BigDecimal weightKg,boolean nonRollable,@NotNull Instant searchDeadline,@NotNull Instant estimatedServiceEndAt,Integer holdSeconds,Integer minimumOfferSeconds) {
  public Input {if(holdSeconds==null)holdSeconds=60;if(minimumOfferSeconds==null)minimumOfferSeconds=15;}
  public Input(UUID requestId,UUID towTruckId,UUID operatorId,VehicleType vehicleType,BigDecimal weightKg,boolean nonRollable,Instant searchDeadline,Instant estimatedServiceEndAt){this(requestId,towTruckId,operatorId,vehicleType,weightKg,nonRollable,searchDeadline,estimatedServiceEndAt,60,15);}
 }
 public record View(UUID assignmentAttemptId,UUID requestId,UUID towTruckId,UUID operatorId,String status,Instant expiresAt,Instant confirmedAt,Instant releasedAt,boolean createdHold) {}
 private final ReservationRepository reservations;private final TowTruckRepository trucks;private final OperatorRepository operators;private final Clock clock;private final ObjectMapper mapper;
 public ReservationService(ReservationRepository reservations,TowTruckRepository trucks,OperatorRepository operators,Clock clock,ObjectMapper mapper){this.reservations=reservations;this.trucks=trucks;this.operators=operators;this.clock=clock;this.mapper=mapper;}
 @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
 private Instant now(){return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);}
 private static void require(boolean ok){if(!ok)throw new ResponseStatusException(HttpStatus.CONFLICT);}
 private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND);}
 private View view(ResourceReservation r){return new View(r.id,r.requestId,r.towTruckId,r.operatorId,r.status,r.expiresAt,r.confirmedAt,r.releasedAt,r.createdHold);}
 private String payload(Input d){try{return mapper.writeValueAsString(new Input(d.requestId(),d.towTruckId(),d.operatorId(),d.vehicleType(),d.weightKg().stripTrailingZeros(),d.nonRollable(),d.searchDeadline(),d.estimatedServiceEndAt(),d.holdSeconds(),d.minimumOfferSeconds()));}catch(Exception e){throw new IllegalArgumentException("Invalid reservation");}}
 private String normalizeStored(String stored){try{return payload(mapper.readValue(stored,Input.class));}catch(Exception e){throw new IllegalStateException("Invalid reservation payload");}}
 private void eligible(TowTruck t,Operator o,Input d,Instant now){eligible(t,o,d,now,true);}
 private void eligible(TowTruck t,Operator o,Input d,Instant now,boolean freshLocation){
  require(t.operator!=null && t.operator.getId().equals(o.id));
  require(t.operationalStatus==Operational.ACTIVE && o.registrationStatus==Registration.ACTIVE && o.availabilityStatus==Availability.AVAILABLE);
  require(t.maximumLoadKg.compareTo(d.weightKg())>=0 && t.supportedTypes.contains(d.vehicleType()) && (!d.nonRollable() || t.supportsNonRollable));
  require(t.locationUpdatedAt!=null && !t.locationUpdatedAt.isAfter(now) && (!freshLocation || !t.locationUpdatedAt.isBefore(now.minusSeconds(300))));
  require(t.latitude!=null && t.longitude!=null && com.auxiruta.commons.GeoMath.distance(-31.4167,-64.1833,t.latitude.doubleValue(),t.longitude.doubleValue(),6371000)<=15000);
  require(o.qualificationCategory==Qualification.LIGHT_TOWING && o.qualificationValidFrom!=null && !o.qualificationValidFrom.isAfter(now) && o.qualificationValidUntil!=null && o.qualificationValidUntil.isAfter(d.estimatedServiceEndAt()));
 }
 public View reserve(UUID id,Input d){
  require((d.holdSeconds()==60 && d.minimumOfferSeconds()==15) || (d.holdSeconds()==10 && d.minimumOfferSeconds()==3));
  // Every operation involving both resources locks truck, then operator, then reservation.
  TowTruck t=trucks.lockById(d.towTruckId()).orElseThrow(ReservationService::missing);
  Operator o=org.hibernate.Hibernate.unproxy(operators.lockById(d.operatorId()).orElseThrow(ReservationService::missing),Operator.class);
  var previous=reservations.lockById(id);String payload=payload(d);
  if(previous.isPresent()){require(normalizeStored(previous.get().payload).equals(payload));expire(previous.get());return view(previous.get());}
  Instant now=now();boolean eligible=true;
  try{require(Duration.between(now,d.searchDeadline()).getSeconds()>=d.minimumOfferSeconds() && d.estimatedServiceEndAt().isAfter(now));eligible(t,o,d,now);require(!reservations.busy(t.id,o.id));}catch(ResponseStatusException e){eligible=false;}
  ResourceReservation r=new ResourceReservation();r.id=id;r.requestId=d.requestId();r.towTruckId=t.id;r.operatorId=o.id;r.payload=payload;r.status="HELD";r.createdAt=now;r.searchDeadline=d.searchDeadline();r.estimatedServiceEndAt=d.estimatedServiceEndAt();r.expiresAt=now.plusSeconds(d.holdSeconds()).isBefore(d.searchDeadline())?now.plusSeconds(d.holdSeconds()):d.searchDeadline();r.createdHold=eligible;if(!eligible){r.status="EXPIRED";r.releasedAt=now;}return view(reservations.saveAndFlush(r));
 }
 private ResourceReservation lock(UUID id){
  // Read only IDs before taking resource locks; refresh state under reservation lock.
  ResourceReservation first=reservations.findById(id).orElseThrow(ReservationService::missing);
  trucks.lockById(first.towTruckId).orElseThrow(ReservationService::missing);operators.lockById(first.operatorId).orElseThrow(ReservationService::missing);
  entityManager.refresh(first,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);return first;
 }
 private void expire(ResourceReservation r){if(r.status.equals("HELD") && !r.expiresAt.isAfter(now())){r.status="EXPIRED";r.releasedAt=now();}}
 public void expireDue(UUID id){expire(lock(id));}
 public record StatusQuery(UUID assignmentAttemptId,String status,Eligibility resource) {}
 public StatusQuery statusQuery(UUID id){var found=reservations.findById(id);if(found.isEmpty())return new StatusQuery(id,"MISSING",null);var r=found.get();String state=r.status.equals("HELD") && !r.expiresAt.isAfter(now())?"EXPIRED":r.status;return new StatusQuery(id,state,state.equals("CONFIRMED")?eligibility(id):null);}
 public View get(UUID id){ResourceReservation r=lock(id);expire(r);return view(r);}
 public View confirm(UUID id){ResourceReservation r=lock(id);if(r.status.equals("CONFIRMED"))return view(r);if(!r.status.equals("HELD") || !r.expiresAt.isAfter(now()) || !r.searchDeadline.isAfter(now())){expire(r);return view(r);}
  Input d;try{d=mapper.readValue(r.payload,Input.class);}catch(Exception e){throw new IllegalStateException("Invalid reservation payload");}
  TowTruck t=trucks.findById(r.towTruckId).orElseThrow();Operator o=org.hibernate.Hibernate.unproxy(operators.findById(r.operatorId).orElseThrow(),Operator.class);
  try{eligible(t,o,d,now());}catch(ResponseStatusException e){r.status="RELEASED";r.releasedAt=now();return view(r);}
  if(!r.expiresAt.isAfter(now())){expire(r);return view(r);}
  r.status="CONFIRMED";r.confirmedAt=now();reservations.flush();return view(r);
 }
 public record Eligibility(UUID assignmentAttemptId,UUID requestId,UUID towTruckId,UUID operatorId,String operatorSubject,boolean eligible,String reason,BigDecimal latitude,BigDecimal longitude,Instant locationUpdatedAt,Instant qualificationValidUntil,Instant checkedAt) {}
 public Eligibility eligibility(UUID id){
  ResourceReservation r=lock(id);TowTruck t=trucks.findById(r.towTruckId).orElseThrow();Operator o=org.hibernate.Hibernate.unproxy(operators.findById(r.operatorId).orElseThrow(),Operator.class);boolean valid=r.status.equals("CONFIRMED");String reason=valid?"ELIGIBLE":"RESERVATION_NOT_CONFIRMED";
  if(valid)try{Input input=mapper.readValue(r.payload,Input.class);eligible(t,o,input,now(),false);}catch(ResponseStatusException e){valid=false;reason="RESOURCE_NOT_ELIGIBLE";}catch(Exception e){throw new IllegalStateException("Invalid reservation payload");}
  return new Eligibility(r.id,r.requestId,r.towTruckId,r.operatorId,o.keycloakSubject,valid,reason,t.latitude,t.longitude,t.locationUpdatedAt,o.qualificationValidUntil,now());
 }
 public View release(UUID id){ResourceReservation r=lock(id);if(!Set.of("RELEASED","EXPIRED").contains(r.status)){if(r.status.equals("CONFIRMED")){Operator o=org.hibernate.Hibernate.unproxy(operators.findById(r.operatorId).orElseThrow(),Operator.class);if(o.availabilityStatus==Availability.AVAILABLE){o.availableSince=now();o.updatedAt=now();}}r.status="RELEASED";r.releasedAt=now();}return view(r);}
}
