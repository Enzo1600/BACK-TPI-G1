package com.auxiruta.fleet.catalog;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resource_reservations")
public class ResourceReservation {
  @Id UUID id;
  @Version long version;

  @Column(nullable = false)
  boolean createdHold;

  @Column(nullable = false)
  UUID requestId;

  @Column(nullable = false)
  UUID towTruckId;

  @Column(nullable = false)
  UUID operatorId;

  @Column(nullable = false, length = 20)
  String status;

  @Column(nullable = false, columnDefinition = "text")
  String payload;

  @Column(nullable = false)
  Instant createdAt;

  @Column(nullable = false)
  Instant expiresAt;

  @Column(nullable = false)
  Instant searchDeadline;

  @Column(nullable = false)
  Instant estimatedServiceEndAt;

  Instant confirmedAt;
  Instant releasedAt;

  protected ResourceReservation() {}
}
