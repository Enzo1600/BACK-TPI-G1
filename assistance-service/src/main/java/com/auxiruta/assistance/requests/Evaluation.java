package com.auxiruta.assistance.requests;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;
@Entity @Table(name="evaluations")
public class Evaluation {
 protected Evaluation() {}
 @Id UUID id=UUID.randomUUID();
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="request_id") AssistanceRequest request;
 @Column(nullable=false) int searchAttemptNumber;
 @Column(nullable=false,precision=9,scale=6) BigDecimal originLatitude;
 @Column(nullable=false,precision=9,scale=6) BigDecimal originLongitude;
 @Column(nullable=false,precision=9,scale=6) BigDecimal destinationLatitude;
 @Column(nullable=false,precision=9,scale=6) BigDecimal destinationLongitude;
 @Column(name="transport_distance_m",columnDefinition="numeric") BigDecimal transportDistanceM;
 @Column(nullable=true,columnDefinition="numeric") BigDecimal transportDurationSeconds;
 @Column(nullable=true) Integer adjustedTransportSeconds;
 @Column(nullable=true) Instant routeObtainedAt;
 @Column(nullable=false,length=20) String vehicleType;
 @Column(nullable=false,precision=9,scale=2) BigDecimal declaredWeightKg;
 @Column(nullable=false,length=20) String mobilityCondition;
 @Column(nullable=false) int maximumArrivalSeconds;
 @Column(nullable=false) int loadingSeconds;
 @Column(nullable=false) int unloadingSeconds;
 @Column(nullable=false) int operationalMarginSeconds;
 @Column(nullable=false,length=30) String configurationRevision;
 @Column(nullable=false,columnDefinition="text") String coverageAndAdmissionSummary;
 @Column(nullable=false,length=30) String result;
 @Column(nullable=true,length=100) String reasonCode;
 @Column(nullable=true,columnDefinition="text") String reasonDetail;
 @Column(nullable=false) Instant evaluatedAt;
}
