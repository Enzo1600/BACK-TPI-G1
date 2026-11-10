package com.auxiruta.assistance.requests;

import jakarta.persistence.*;
import java.util.UUID;
import java.time.Instant;
import java.math.BigDecimal;
import static com.auxiruta.assistance.requests.DomainTypes.*;

@Entity
@Table(name = "vehicles")
public class Vehicle {
  protected Vehicle() {}

  @Id UUID id = UUID.randomUUID();
  @Version long version;

  @Column(nullable = false)
  Instant createdAt;

  @Column(nullable = false)
  Instant updatedAt;

  public UUID getId() {
    return id;
  }

  @Column(nullable = false, length = 100)
  String ownerSubject;

  @Column(nullable = false, unique = true, length = 10)
  String licensePlate;

  @Column(length = 100)
  String brand;

  @Column(length = 100)
  String model;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  VehicleType vehicleType;

  @Column(nullable = false, precision = 9, scale = 2)
  BigDecimal declaredWeightKg;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  Registration registrationStatus = Registration.ACTIVE;
}
