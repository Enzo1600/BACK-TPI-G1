package com.auxiruta.fleet.catalog;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface ReservationRepository extends JpaRepository<ResourceReservation,UUID> {
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from ResourceReservation r where r.id=:id") Optional<ResourceReservation> lockById(UUID id);
 @Query("select count(r)>0 from ResourceReservation r where (r.towTruckId=:truck or r.operatorId=:operator) and r.status in ('HELD','CONFIRMED')") boolean busy(UUID truck,UUID operator);
}
