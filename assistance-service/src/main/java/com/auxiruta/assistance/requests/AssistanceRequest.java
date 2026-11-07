package com.auxiruta.assistance.requests;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;
import static com.auxiruta.assistance.requests.DomainTypes.*;
@Entity @Table(name="assistance_requests",uniqueConstraints=@UniqueConstraint(columnNames={"driver_subject","idempotency_key"}))
public class AssistanceRequest {
 protected AssistanceRequest() {}
 @Id UUID id=UUID.randomUUID();
 @Version long version;
 @Column(nullable=false) Instant createdAt;
 @Column(nullable=false) Instant updatedAt;
 public UUID getId() { return id; }
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="vehicle_id") Vehicle vehicle;
 @Column(nullable=false,length=100) String driverSubject;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) Status status=Status.REQUESTED;
 Instant firstAssignedAt;
 @Column(length=100) String closureReasonCode;
 @Column(nullable=false,precision=9,scale=6) BigDecimal originLatitude;
 @Column(nullable=false,precision=9,scale=6) BigDecimal originLongitude;
 @Column(nullable=false,precision=9,scale=6) BigDecimal destinationLatitude;
 @Column(nullable=false,precision=9,scale=6) BigDecimal destinationLongitude;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) VehicleType declaredVehicleType;
 @Column(nullable=false,precision=9,scale=2) BigDecimal declaredWeightKg;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) Mobility mobilityCondition;
 @Column(nullable=false,length=1000) String description;
 @Column(nullable=false) int searchAttemptNumber=1;
 @Column(nullable=false) Instant searchDeadline;
 @Column(nullable=false,length=30) String configurationRevision;
 @Column(nullable=false,columnDefinition="text") String configurationSnapshot;
 @Column(nullable=false,length=100) String idempotencyKey;
 @Column(nullable=false,length=64) String requestFingerprint;
 Instant closedAt;
}
