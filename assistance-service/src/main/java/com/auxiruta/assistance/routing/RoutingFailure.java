package com.auxiruta.assistance.routing;

public class RoutingFailure extends RuntimeException {
  public enum Reason {
    NO_ROUTE,
    SNAP_TOO_FAR,
    RATE_LIMIT,
    DAILY_QUOTA,
    CONFIGURATION,
    UNAVAILABLE,
    INVALID_RESPONSE
  }

  private final Reason reason;

  public RoutingFailure(Reason reason) {
    super(reason.name());
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }

  public boolean domainRejection() {
    return reason == Reason.NO_ROUTE || reason == Reason.SNAP_TOO_FAR;
  }
}
