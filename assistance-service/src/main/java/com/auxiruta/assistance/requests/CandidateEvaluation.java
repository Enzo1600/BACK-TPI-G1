package com.auxiruta.assistance.requests;
import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;
@Entity @Table(name="candidate_evaluations")
public class CandidateEvaluation {
 protected CandidateEvaluation() {}
 @Id UUID id=UUID.randomUUID();
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="evaluation_id") Evaluation evaluation;
 @Column(nullable=false) UUID towTruckId;
 @Column(nullable=false) UUID operatorId;
 @Column(nullable=false,length=100) String operatorSubject;
 @Column(nullable=false,precision=9,scale=2) BigDecimal truckCapacityKg;
 @Column(nullable=false,length=30) String qualificationCategory;
 @Column(nullable=false) Instant qualificationValidUntil;
 @Column(nullable=false,precision=10,scale=7) BigDecimal truckLatitude;
 @Column(nullable=false,precision=10,scale=7) BigDecimal truckLongitude;
 @Column(nullable=false) Instant truckLocationUpdatedAt;
 @Column(nullable=false) boolean compatible;
 @Column(nullable=false) boolean eligible;
 @Column(nullable=true,length=100) String reasonCode;
 @Column(name="approach_distance_m",columnDefinition="numeric") BigDecimal approachDistanceM;
 @Column(nullable=true,columnDefinition="numeric") BigDecimal approachDurationSeconds;
 @Column(nullable=true) Integer adjustedApproachSeconds;
 @Column(nullable=true) Instant approachRouteObtainedAt;
 @Column(name="transport_distance_m",columnDefinition="numeric") BigDecimal transportDistanceM;
 @Column(nullable=true,columnDefinition="numeric") BigDecimal transportDurationSeconds;
 @Column(nullable=true) Integer adjustedTransportSeconds;
 @Column(nullable=true) Instant transportRouteObtainedAt;
 @Column(nullable=true) Instant estimatedServiceEndAt;
 @Column(nullable=true) Integer rankingPosition;
}
