package com.auxiruta.fleet.catalog;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.auxiruta.fleet.catalog.FleetTypes.*;
class ReservationServiceTest {
 ReservationRepository reservations;TowTruckRepository trucks;OperatorRepository operators;ReservationService service;Clock clock;
 Instant now=Instant.parse("2026-11-08T12:00:00Z");TowTruck truck;Operator operator;com.fasterxml.jackson.databind.ObjectMapper mapper;
 @BeforeEach void setup(){reservations=mock(ReservationRepository.class);trucks=mock(TowTruckRepository.class);operators=mock(OperatorRepository.class);clock=mock(Clock.class);when(clock.instant()).thenAnswer(i->now);mapper=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();service=new ReservationService(reservations,trucks,operators,clock,mapper);org.springframework.test.util.ReflectionTestUtils.setField(service,"entityManager",mock(jakarta.persistence.EntityManager.class));
  operator=new Operator();operator.registrationStatus=Registration.ACTIVE;operator.availabilityStatus=Availability.AVAILABLE;operator.qualificationCategory=Qualification.LIGHT_TOWING;operator.qualificationValidFrom=now.minusSeconds(100);operator.qualificationValidUntil=now.plusSeconds(7200);
  truck=new TowTruck();truck.operator=operator;truck.operationalStatus=Operational.ACTIVE;truck.maximumLoadKg=new BigDecimal("3000");truck.supportedTypes.add(VehicleType.CAR);truck.latitude=new BigDecimal("-31.4167");truck.longitude=new BigDecimal("-64.1833");truck.locationUpdatedAt=now;
  when(trucks.lockById(truck.id)).thenReturn(Optional.of(truck));when(trucks.findById(truck.id)).thenReturn(Optional.of(truck));when(operators.lockById(operator.id)).thenReturn(Optional.of(operator));when(operators.findById(operator.id)).thenReturn(Optional.of(operator));when(reservations.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
 }
 ReservationService.Input input(){return new ReservationService.Input(UUID.randomUUID(),truck.id,operator.id,VehicleType.CAR,new BigDecimal("1000"),false,now.plusSeconds(600),now.plusSeconds(3600));}
 ResourceReservation held(){var d=input();ResourceReservation r=new ResourceReservation();r.id=UUID.randomUUID();r.requestId=d.requestId();r.towTruckId=truck.id;r.operatorId=operator.id;r.status="HELD";r.expiresAt=now.plusSeconds(60);r.searchDeadline=d.searchDeadline();try{r.payload=mapper.writeValueAsString(d);}catch(Exception e){throw new RuntimeException(e);}when(reservations.findById(r.id)).thenReturn(Optional.of(r));return r;}
 @Test void holdExpiresAtSixtySeconds(){var result=service.reserve(UUID.randomUUID(),input());assertEquals("HELD",result.status());assertEquals(now.plusSeconds(60),result.expiresAt());}
 @Test void shortSearchWindowCreatesDurableTerminalAttempt(){var d=input();var shortInput=new ReservationService.Input(d.requestId(),d.towTruckId(),d.operatorId(),d.vehicleType(),d.weightKg(),false,now.plusSeconds(14),d.estimatedServiceEndAt());assertEquals("EXPIRED",service.reserve(UUID.randomUUID(),shortInput).status());verify(reservations).saveAndFlush(any());}
 @Test void activeCommitmentExcludesNewAttempt(){when(reservations.busy(truck.id,operator.id)).thenReturn(true);assertEquals("EXPIRED",service.reserve(UUID.randomUUID(),input()).status());}
 @Test void confirmationAtExactExpiryIsRejected(){var r=held();now=r.expiresAt;assertEquals("EXPIRED",service.confirm(r.id).status());assertNull(r.confirmedAt);}
 @Test void confirmedReservationDoesNotExpire(){var r=held();r.status="CONFIRMED";r.confirmedAt=now;now=now.plusSeconds(600);assertEquals("CONFIRMED",service.confirm(r.id).status());}
 @Test void invalidQualificationReleasesHold(){var r=held();operator.qualificationValidUntil=now;assertEquals("RELEASED",service.confirm(r.id).status());}
 @Test void releaseIsIdempotent(){var r=held();var first=service.release(r.id);now=now.plusSeconds(10);assertEquals(first,service.release(r.id));}
 @Test void confirmationCommitsBeforeDeadline(){var r=held();assertEquals("CONFIRMED",service.confirm(r.id).status());assertEquals(now,r.confirmedAt);}
 @Test void demoWindowComesFromTheRequestSnapshot(){var d=input();var demo=new ReservationService.Input(d.requestId(),d.towTruckId(),d.operatorId(),d.vehicleType(),d.weightKg(),false,now.plusSeconds(60),d.estimatedServiceEndAt(),10,3);assertEquals(now.plusSeconds(10),service.reserve(UUID.randomUUID(),demo).expiresAt());}
 @Test void legacyPayloadWithoutTimingFieldsRemainsIdempotent()throws Exception {var d=input();var old=new ResourceReservation();old.id=UUID.randomUUID();old.requestId=d.requestId();old.towTruckId=d.towTruckId();old.operatorId=d.operatorId();old.status="RELEASED";old.expiresAt=now;var json=mapper.valueToTree(d);((com.fasterxml.jackson.databind.node.ObjectNode)json).remove(List.of("holdSeconds","minimumOfferSeconds"));old.payload=mapper.writeValueAsString(json);when(reservations.lockById(old.id)).thenReturn(Optional.of(old));assertEquals("RELEASED",service.reserve(old.id,d).status());verify(reservations,never()).saveAndFlush(any());}
 @Test void confirmedLocationDoesNotExpire(){var r=held();r.status="CONFIRMED";truck.locationUpdatedAt=now.minusSeconds(900);assertTrue(service.eligibility(r.id).eligible());}
 @Test void oldLocationCannotBeConfirmed(){var r=held();truck.locationUpdatedAt=now.minusSeconds(301);assertEquals("RELEASED",service.confirm(r.id).status());}
}
