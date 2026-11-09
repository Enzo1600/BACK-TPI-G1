package com.auxiruta.fleet.catalog;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.LoggerFactory;
import static com.auxiruta.fleet.catalog.FleetDtos.*;
import static com.auxiruta.fleet.catalog.FleetTypes.*;

@Service
@Transactional(readOnly=true)
public class FleetService {
 private final OperatorRepository operators;
 private final TowTruckRepository trucks;
 private final TruckStatusHistoryRepository history;
 private final Clock clock;
 @org.springframework.beans.factory.annotation.Autowired private ReservationRepository reservations;
 private void freeTruck(UUID id){require(!reservations.busy(id,null),HttpStatus.CONFLICT);}
 private void freeOperator(UUID id){require(!reservations.busy(null,id),HttpStatus.CONFLICT);}
 public FleetService(OperatorRepository operators,TowTruckRepository trucks,TruckStatusHistoryRepository history,Clock clock) {
  this.operators=operators;this.trucks=trucks;this.history=history;this.clock=clock;
 }
 private void committed(String event,UUID id) {
  org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
   new org.springframework.transaction.support.TransactionSynchronization() {
    @Override public void afterCommit() { LoggerFactory.getLogger(FleetService.class).info("Fleet event={} resourceId={}",event,id); }
   });
 }
 private Instant now() { return clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
 private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }
 private static void require(boolean condition,HttpStatus status) { if(!condition) throw new ResponseStatusException(status); }
 private Operator operator(UUID id) { return operators.findById(id).orElseThrow(FleetService::missing); }
 private Operator own(String subject) { return operators.findByKeycloakSubject(subject).orElseThrow(FleetService::missing); }
 private Operator lockedOperator(UUID id) { return org.hibernate.Hibernate.unproxy(operators.lockById(id).orElseThrow(FleetService::missing),Operator.class); }
 private TowTruck truck(UUID id) { return trucks.findById(id).orElseThrow(FleetService::missing); }
 private TowTruck lockedTruck(UUID id) { return trucks.lockById(id).orElseThrow(FleetService::missing); }
 private TowTruck ownTruck(String subject) { return trucks.findByOperatorId(own(subject).id).orElseThrow(FleetService::missing); }
 private static String plate(String value) {
  String normalized=value.replace(" ","").replace("-","").toUpperCase(Locale.ROOT);
  require(normalized.matches("[A-Z0-9]{6,10}"),HttpStatus.BAD_REQUEST); return normalized;
 }
 private static String name(String value) { require(!value.isBlank(),HttpStatus.BAD_REQUEST); return value.strip(); }
 private static Pageable page(int page,int size) {
  require(page>=0 && size>=1 && size<=100,HttpStatus.BAD_REQUEST);
  return PageRequest.of(page,size,Sort.by("createdAt").ascending().and(Sort.by("id")));
 }
 private static <T> PageView<T> result(Page<T> p) { return new PageView<>(p.getContent(),p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages()); }
 private OperatorView view(Operator o) { return new OperatorView(o.id,o.keycloakSubject,o.fullName,o.contactPhone,o.registrationStatus,o.availabilityStatus,o.qualificationCategory,o.qualificationValidFrom,o.qualificationValidUntil,o.availableSince,o.createdAt,o.updatedAt,o.version); }
 private TruckView view(TowTruck t) { return new TruckView(t.id,t.licensePlate,t.operator==null?null:t.operator.getId(),t.maximumLoadKg,t.supportsNonRollable,Set.copyOf(t.supportedTypes),t.operationalStatus,t.latitude,t.longitude,t.locationUpdatedAt,t.createdAt,t.updatedAt,t.version); }
 public OperatorView getOperator(UUID id) { return view(operator(id)); }
 public OperatorView me(String subject) { return view(own(subject)); }
 public TruckView getTruck(UUID id) { return view(truck(id)); }
 public TruckView assigned(String subject) { return view(ownTruck(subject)); }
 public PageView<OperatorView> operators(int p,int s,Registration status) { Pageable pageable=page(p,s);return result((status==null?operators.findAll(pageable):operators.findByRegistrationStatus(status,pageable)).map(this::view)); }
 public PageView<TruckView> trucks(int p,int s,Operational status) { Pageable pageable=page(p,s);return result((status==null?trucks.findAll(pageable):trucks.findByOperationalStatus(status,pageable)).map(this::view)); }
 @Transactional public OperatorView create(CreateOperator d) {
  Operator o=new Operator();o.keycloakSubject=d.keycloakSubject().strip();o.fullName=name(d.fullName());o.contactPhone=d.contactPhone();o.createdAt=o.updatedAt=now();
  operators.saveAndFlush(o);committed("OPERATOR_CREATED",o.id);return view(o);
 }
 @Transactional public OperatorView edit(UUID id,EditOperator d) {
  require(d.fullName()!=null || d.contactPhone()!=null,HttpStatus.BAD_REQUEST);
  Operator o=lockedOperator(id);if(d.fullName()!=null)o.fullName=name(d.fullName());if(d.contactPhone()!=null)o.contactPhone=d.contactPhone();
  o.updatedAt=now();operators.flush();return view(o);
 }
 @Transactional public OperatorView registration(UUID id,RegistrationChange d) {
  Operator o=lockedOperator(id);freeOperator(id);o.registrationStatus=d.status();
  if(d.status()==Registration.INACTIVE) { o.availabilityStatus=Availability.UNAVAILABLE;o.availableSince=null; }
  o.updatedAt=now();operators.flush();return view(o);
 }
 @Transactional public OperatorView qualification(UUID id,QualificationChange d) {
  require(d.validFrom().isBefore(d.validUntil()),HttpStatus.BAD_REQUEST);
  Operator o=lockedOperator(id);freeOperator(id);o.qualificationCategory=d.category();o.qualificationValidFrom=d.validFrom();o.qualificationValidUntil=d.validUntil();o.updatedAt=now();operators.flush();
  committed("QUALIFICATION_UPDATED",o.id);return view(o);
 }
 @Transactional public OperatorView availability(String subject,AvailabilityChange d) {
  Operator o=operators.lockBySubject(subject).orElseThrow(FleetService::missing);
  freeOperator(o.id);
  require(d.status()!=Availability.AVAILABLE || o.registrationStatus==Registration.ACTIVE,HttpStatus.CONFLICT);
  if(o.availabilityStatus!=d.status()) { o.availabilityStatus=d.status();o.availableSince=d.status()==Availability.AVAILABLE?now():null;o.updatedAt=now(); }
  operators.flush();return view(o);
 }
 @Transactional public TruckView create(CreateTruck d) {
  TowTruck t=new TowTruck();t.licensePlate=plate(d.licensePlate());t.maximumLoadKg=d.maximumLoadKg();t.supportsNonRollable=d.supportsNonRollable();t.supportedTypes.addAll(d.supportedTypes());t.createdAt=t.updatedAt=now();trucks.saveAndFlush(t);committed("TRUCK_CREATED",t.id);return view(t);
 }
 @Transactional public TruckView edit(UUID id,EditTruck d) {
  require(d.licensePlate()!=null || d.maximumLoadKg()!=null || d.supportsNonRollable()!=null || d.supportedTypes()!=null,HttpStatus.BAD_REQUEST);
  TowTruck t=lockedTruck(id);freeTruck(id);
  if(d.licensePlate()!=null)t.licensePlate=plate(d.licensePlate());
  if(d.maximumLoadKg()!=null)t.maximumLoadKg=d.maximumLoadKg();
  if(d.supportsNonRollable()!=null)t.supportsNonRollable=d.supportsNonRollable();
  if(d.supportedTypes()!=null) { t.supportedTypes.clear();t.supportedTypes.addAll(d.supportedTypes()); }
  t.updatedAt=now();trucks.flush();return view(t);
 }
 @Transactional public TruckView link(UUID id,LinkOperator d) {
  // All operations needing both locks take the truck first, then the operator.
  TowTruck t=lockedTruck(id);Operator o=lockedOperator(d.operatorId());freeTruck(id);freeOperator(o.id);
  require(t.operator==null || t.operator.getId().equals(o.id),HttpStatus.CONFLICT);
  var assigned=trucks.findByOperatorId(o.id);
  require(assigned.isEmpty() || assigned.get().id.equals(t.id),HttpStatus.CONFLICT);
  t.operator=o;t.updatedAt=now();trucks.flush();return view(t);
 }
 @Transactional public void unlink(UUID id) { TowTruck t=lockedTruck(id);freeTruck(id);t.operator=null;t.updatedAt=now();trucks.flush(); }
 @Transactional public StatusView status(UUID id,StatusChange d,String subject,String key) {
  require(key!=null && !key.isBlank() && key.length()<=100,HttpStatus.BAD_REQUEST);
  TowTruck t=lockedTruck(id);
  var previous=history.findByOperationKey(key);
  if(previous.isPresent()) {
   TruckStatusHistory h=previous.get();
   require(h.truck.getId().equals(id) && h.newStatus==d.status() && h.reason.equals(d.reason().strip()) && h.changedBySubject.equals(subject),HttpStatus.CONFLICT);
   return statusView(h);
  }
  freeTruck(id);
  require(t.operationalStatus!=d.status(),HttpStatus.CONFLICT);
  TruckStatusHistory h=new TruckStatusHistory();h.truck=t;h.previousStatus=t.operationalStatus;h.newStatus=d.status();h.reason=d.reason().strip();h.changedBySubject=subject;h.operationKey=key;h.changedAt=now();
  history.save(h);t.operationalStatus=d.status();t.updatedAt=h.changedAt;trucks.flush();
  committed("TRUCK_"+h.previousStatus+"_TO_"+h.newStatus,t.id);return statusView(h);
 }
 private StatusView statusView(TruckStatusHistory h) { return new StatusView(h.id,h.truck.getId(),h.previousStatus,h.newStatus,h.reason,h.changedBySubject,h.operationKey,h.changedAt); }
 @Transactional public TruckView location(String subject,Location d) {
  TowTruck t=lockedTruck(trucks.assignedId(subject).orElseThrow(FleetService::missing));
  require(t.operator!=null && t.operator.getKeycloakSubject().equals(subject),HttpStatus.NOT_FOUND);
  freeTruck(t.id);
  t.latitude=d.latitude();t.longitude=d.longitude();t.locationUpdatedAt=t.updatedAt=now();trucks.flush();return view(t);
 }
}
