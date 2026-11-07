package com.auxiruta.assistance.requests;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
public interface RequestRepository extends JpaRepository<AssistanceRequest,UUID> {
 @Query("select r.vehicle.id from AssistanceRequest r where r.id=:id") Optional<UUID> vehicleId(UUID id);
 Optional<AssistanceRequest> findByDriverSubjectAndIdempotencyKey(String subject,String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from AssistanceRequest r where r.id=:id") Optional<AssistanceRequest> lockById(UUID id);
 @Query("select count(r)>0 from AssistanceRequest r where r.vehicle.id=:id and r.closedAt is null") boolean hasActive(UUID id);
 @Query("select r from AssistanceRequest r where (:subject is null or r.driverSubject=:subject) and (:status is null or r.status=:status)") Page<AssistanceRequest> visible(String subject,DomainTypes.Status status,Pageable page);
}
