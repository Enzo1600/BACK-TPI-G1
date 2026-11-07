package com.auxiruta.fleet.catalog;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;
import static com.auxiruta.fleet.catalog.FleetTypes.*;
@Entity @Table(name="tow_trucks")
public class TowTruck {
 protected TowTruck() {}
 @Id UUID id = UUID.randomUUID();
 @Version long version;
 @Column(nullable=false) Instant createdAt;
 @Column(nullable=false) Instant updatedAt;
 @Column(nullable=false,unique=true,length=10) String licensePlate;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="operator_id",unique=true) Operator operator;
 @Column(nullable=false,precision=9,scale=2) BigDecimal maximumLoadKg;
 @Column(nullable=false) boolean supportsNonRollable;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) Operational operationalStatus = Operational.INACTIVE;
 @Column(precision=10,scale=7) BigDecimal latitude;
 @Column(precision=10,scale=7) BigDecimal longitude;
 Instant locationUpdatedAt;
 @ElementCollection @CollectionTable(name="tow_truck_supported_types",joinColumns=@JoinColumn(name="tow_truck_id"))
 @Enumerated(EnumType.STRING) @Column(name="vehicle_type",length=20) Set<VehicleType> supportedTypes = new HashSet<>();
 public UUID getId() { return id; }
}
