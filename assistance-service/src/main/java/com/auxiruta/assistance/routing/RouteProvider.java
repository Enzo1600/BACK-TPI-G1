package com.auxiruta.assistance.routing;

import java.math.BigDecimal;
import java.time.Instant;

public interface RouteProvider {
  record Point(BigDecimal latitude, BigDecimal longitude) {}

  record Route(BigDecimal distanceM, BigDecimal durationSeconds, Instant obtainedAt) {}

  Route route(Point from, Point to, String revision, int maxAgeSeconds, int maxSnapMeters);
}
