package com.auxiruta.fleet.catalog;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import static com.auxiruta.fleet.catalog.FleetTypes.*;
@Entity @Table(name="operators")
public class Operator {
 protected Operator() {}
 @Id UUID id = UUID.randomUUID();
 @Version long version;
 @Column(nullable=false) Instant createdAt;
 @Column(nullable=false) Instant updatedAt;
 @Column(nullable=false,unique=true,length=100) String keycloakSubject;
 @Column(nullable=false,length=150) String fullName;
 @Column(length=40) String contactPhone;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) Registration registrationStatus = Registration.ACTIVE;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) Availability availabilityStatus = Availability.UNAVAILABLE;
 @Enumerated(EnumType.STRING) @Column(length=30) Qualification qualificationCategory;
 Instant qualificationValidFrom;
 Instant qualificationValidUntil;
 Instant availableSince;
 public UUID getId() { return id; }
 public String getKeycloakSubject() { return keycloakSubject; }
}
