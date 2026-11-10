package com.auxiruta.assistance.requests;

public final class DomainTypes {
  private DomainTypes() {}

  public enum VehicleType {
    CAR,
    SUV,
    PICKUP
  }

  public enum Registration {
    ACTIVE,
    INACTIVE
  }

  public enum Mobility {
    ROLLABLE,
    NON_ROLLABLE
  }

  public enum Status {
    REQUESTED,
    PENDING_EVALUATION,
    AWAITING_ACCEPTANCE,
    ASSIGNED,
    SEARCHING_ALTERNATIVE,
    DISPATCHED,
    ARRIVED,
    VEHICLE_LOADED,
    IN_TRANSIT,
    COMPLETED,
    REJECTED,
    UNFULFILLED,
    CANCELLED
  }
}
