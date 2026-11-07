package com.auxiruta.fleet.catalog;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import static com.auxiruta.fleet.catalog.FleetTypes.*;
@Entity @Table(name="truck_status_history")
public class TruckStatusHistory {
 protected TruckStatusHistory() {}
 @Id UUID id=UUID.randomUUID();
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="tow_truck_id") TowTruck truck;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) Operational previousStatus;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) Operational newStatus;
 @Column(nullable=false,length=500) String reason;
 @Column(nullable=false,length=100) String changedBySubject;
 @Column(unique=true,length=100) String operationKey;
 @Column(length=100) String correlationId;
 @Column(nullable=false) Instant changedAt;
}
