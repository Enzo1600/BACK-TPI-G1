package com.auxiruta.assistance.requests;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import jakarta.persistence.LockModeType;
public interface RequestRepository extends JpaRepository<AssistanceRequest,UUID> {
 @Query("select r.vehicle.id from AssistanceRequest r where r.id=:id") Optional<UUID> vehicleId(UUID id);
 Optional<AssistanceRequest> findByDriverSubjectAndIdempotencyKey(String subject,String key);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from AssistanceRequest r where r.id=:id") Optional<AssistanceRequest> lockById(UUID id);
 @Query("select count(r)>0 from AssistanceRequest r where r.vehicle.id=:id and (r.closedAt is null or r.resourceReleasePending=true)") boolean hasActive(UUID id);
 @Query("select r from AssistanceRequest r where (:subject is null or r.driverSubject=:subject) and (:status is null or r.status=:status)") Page<AssistanceRequest> visible(String subject,DomainTypes.Status status,Pageable page);
 @Query(value="select r.* from assistance_requests r where exists(select 1 from assignments a where a.request_id=r.id and a.operator_subject=:subject) and (:status is null or r.status=:status)",countQuery="select count(*) from assistance_requests r where exists(select 1 from assignments a where a.request_id=r.id and a.operator_subject=:subject) and (:status is null or r.status=:status)",nativeQuery=true)
 Page<AssistanceRequest> forOperator(String subject,String status,Pageable page);
}
