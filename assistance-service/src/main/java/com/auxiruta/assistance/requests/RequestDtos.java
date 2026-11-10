package com.auxiruta.assistance.requests;

import java.util.*;
import java.time.Instant;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import static com.auxiruta.assistance.requests.DomainTypes.*;

public final class RequestDtos {
  private RequestDtos() {}

  public record CreateVehicle(
      @NotBlank @Size(max = 30) String licensePlate,
      @Size(max = 100) String brand,
      @Size(max = 100) String model,
      @NotNull VehicleType vehicleType,
      @NotNull @DecimalMin("500") @DecimalMax("3000") @Digits(integer = 4, fraction = 2)
          BigDecimal declaredWeightKg) {}

  public record EditVehicle(
      @Size(max = 30) String licensePlate,
      @Size(max = 100) String brand,
      @Size(max = 100) String model,
      VehicleType vehicleType,
      @DecimalMin("500") @DecimalMax("3000") @Digits(integer = 4, fraction = 2)
          BigDecimal declaredWeightKg) {}

  public record RegistrationChange(@NotNull Registration status) {}

  public record CreateRequest(
      @NotNull UUID vehicleId,
      @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6)
          BigDecimal originLatitude,
      @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6)
          BigDecimal originLongitude,
      @NotNull @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6)
          BigDecimal destinationLatitude,
      @NotNull @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6)
          BigDecimal destinationLongitude,
      @NotNull Mobility mobilityCondition,
      @NotBlank @Size(max = 1000) String description) {}

  public record Cancellation(@NotBlank @Size(max = 500) String reason) {}

  public record VehicleView(
      UUID id,
      String licensePlate,
      String brand,
      String model,
      VehicleType vehicleType,
      BigDecimal declaredWeightKg,
      Registration registrationStatus,
      Instant createdAt,
      Instant updatedAt,
      long version) {}

  public record RequestView(
      UUID id,
      UUID vehicleId,
      Status status,
      Instant firstAssignedAt,
      String closureReasonCode,
      BigDecimal originLatitude,
      BigDecimal originLongitude,
      BigDecimal destinationLatitude,
      BigDecimal destinationLongitude,
      VehicleType declaredVehicleType,
      BigDecimal declaredWeightKg,
      Mobility mobilityCondition,
      String description,
      int searchAttemptNumber,
      Instant searchDeadline,
      String configurationRevision,
      String configurationSnapshot,
      Instant createdAt,
      Instant updatedAt,
      Instant closedAt,
      long version,
      boolean resourceReleasePending,
      EvaluationReadService.EvaluationView latestEvaluation) {}

  public record HistoryView(
      UUID id,
      UUID requestId,
      Status previousStatus,
      Status newStatus,
      String actorType,
      String actorSubject,
      String reason,
      String details,
      String operationKey,
      Instant occurredAt) {}

  public record PageView<T>(
      List<T> content, int page, int size, long totalElements, int totalPages) {}

  public record Created(RequestView request, boolean created) {}
}
