package com.auxiruta.fleet.catalog;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.time.*;
import java.math.BigDecimal;
import static com.auxiruta.fleet.catalog.FleetTypes.*;
class CandidateServiceTest {
 Instant now=Instant.parse("2026-11-08T00:00:00Z");TowTruckRepository trucks=mock(TowTruckRepository.class);CandidateService service=new CandidateService(trucks,Clock.fixed(now,ZoneOffset.UTC));
 TowTruck truck(){var t=new TowTruck();t.maximumLoadKg=new BigDecimal("3000");t.supportedTypes.add(VehicleType.CAR);t.supportsNonRollable=true;t.latitude=new BigDecimal("-31.4167");t.longitude=new BigDecimal("-64.1833");t.locationUpdatedAt=now;Operator o=new Operator();o.keycloakSubject="operator";o.qualificationCategory=Qualification.LIGHT_TOWING;o.qualificationValidFrom=now.minusSeconds(100);o.qualificationValidUntil=now.plusSeconds(10000);o.availableSince=now.minusSeconds(200);t.operator=o;return t;}
 List<CandidateService.Candidate> run(List<TowTruck> items){when(trucks.findCandidates(Operational.ACTIVE,Registration.ACTIVE,Availability.AVAILABLE)).thenReturn(items);return service.find(new BigDecimal("-31.4167"),new BigDecimal("-64.1833"),VehicleType.CAR,new BigDecimal("1000"),true);}
 @Test void locationAgeInclusive(){TowTruck t=truck();t.locationUpdatedAt=now.minusSeconds(300);assertEquals(1,run(List.of(t)).size());t.locationUpdatedAt=now.minusSeconds(301);assertTrue(run(List.of(t)).isEmpty());}
 @Test void incompatibleCapacityAndMobilityExcluded(){TowTruck t=truck();t.maximumLoadKg=new BigDecimal("999");assertTrue(run(List.of(t)).isEmpty());t.maximumLoadKg=new BigDecimal("1000");t.supportsNonRollable=false;assertTrue(run(List.of(t)).isEmpty());}
 @Test void expiredQualificationAndOutsideCoverageExcluded(){TowTruck t=truck();t.operator.qualificationValidUntil=now;assertTrue(run(List.of(t)).isEmpty());t.operator.qualificationValidUntil=now.plusSeconds(100);t.latitude=BigDecimal.ZERO;assertTrue(run(List.of(t)).isEmpty());}
 @Test void atMostFiveCandidates(){List<TowTruck> items=new ArrayList<>();for(int i=0;i<8;i++)items.add(truck());assertEquals(5,run(items).size());}
}
