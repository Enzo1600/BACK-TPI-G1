package com.auxiruta.fleet.catalog;
public final class FleetTypes {
 private FleetTypes() {}
 public enum Registration { ACTIVE, INACTIVE }
 public enum Availability { AVAILABLE, UNAVAILABLE }
 public enum Operational { ACTIVE, MAINTENANCE, INACTIVE }
 public enum VehicleType { CAR, SUV, PICKUP }
 public enum Qualification { LIGHT_TOWING }
}
