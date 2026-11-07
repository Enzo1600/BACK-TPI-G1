package com.auxiruta.fleet.catalog;
import jakarta.validation.constraints.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import static com.auxiruta.fleet.catalog.FleetTypes.*;
public final class FleetDtos {
 private FleetDtos() {}
 public record CreateOperator(@NotBlank @Size(max=100) String keycloakSubject,@NotBlank @Size(max=150) String fullName,@Size(max=40) String contactPhone) {}
 public record EditOperator(@Size(min=1,max=150) String fullName,@Size(max=40) String contactPhone) {}
 public record RegistrationChange(@NotNull Registration status) {}
 public record AvailabilityChange(@NotNull Availability status) {}
 public record QualificationChange(@NotNull Qualification category,@NotNull Instant validFrom,@NotNull Instant validUntil) {}
 public record CreateTruck(@NotBlank @Size(max=30) String licensePlate,
  @NotNull @DecimalMin("500") @DecimalMax("5000") @Digits(integer=4,fraction=2) BigDecimal maximumLoadKg,
  @NotNull Boolean supportsNonRollable,@NotEmpty Set<@NotNull VehicleType> supportedTypes) {}
 public record EditTruck(@Size(max=30) String licensePlate,
  @DecimalMin("500") @DecimalMax("5000") @Digits(integer=4,fraction=2) BigDecimal maximumLoadKg,
  Boolean supportsNonRollable,@Size(min=1) Set<@NotNull VehicleType> supportedTypes) {}
 public record LinkOperator(@NotNull UUID operatorId) {}
 public record StatusChange(@NotNull Operational status,@NotBlank @Size(max=500) String reason) {}
 public record Location(@NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer=3,fraction=6) BigDecimal latitude,
  @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer=3,fraction=6) BigDecimal longitude) {}
 public record StatusView(UUID id,UUID towTruckId,Operational previousStatus,Operational newStatus,String reason,String changedBySubject,String operationKey,Instant changedAt) {}
 public record OperatorView(UUID id,String keycloakSubject,String fullName,String contactPhone,Registration registrationStatus,
  Availability availabilityStatus,Qualification qualificationCategory,Instant qualificationValidFrom,Instant qualificationValidUntil,
  Instant availableSince,Instant createdAt,Instant updatedAt,long version) {}
 public record TruckView(UUID id,String licensePlate,UUID operatorId,BigDecimal maximumLoadKg,boolean supportsNonRollable,
  Set<VehicleType> supportedTypes,Operational operationalStatus,BigDecimal latitude,BigDecimal longitude,Instant locationUpdatedAt,
  Instant createdAt,Instant updatedAt,long version) {}
 public record PageView<T>(List<T> content,int page,int size,long totalElements,int totalPages) {}
}
