package com.auxiruta.assistance.requests;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;
import static com.auxiruta.assistance.requests.DomainTypes.*;

@Entity
@Table(
    name = "request_status_history",
    uniqueConstraints = @UniqueConstraint(columnNames = {"request_id", "operation_key"}))
public class RequestStatusHistory {
  protected RequestStatusHistory() {}

  @Id UUID id = UUID.randomUUID();

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "request_id")
  AssistanceRequest request;

  @Enumerated(EnumType.STRING)
  @Column(length = 30)
  Status previousStatus;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  Status newStatus;

  @Column(length = 100)
  String actorSubject;

  @Column(nullable = false, length = 20)
  String actorType;

  @Column(columnDefinition = "text")
  String reason;

  @Column(columnDefinition = "text")
  String details;

  @Column(length = 100)
  String operationKey;

  @Column(length = 64)
  String requestFingerprint;

  @Column(length = 100)
  String correlationId;

  @Column(nullable = false)
  Instant occurredAt;
}
