package com.auxiruta.commons;

/** Pure geographic calculation; coordinates are latitude/longitude in degrees. */
public final class GeoMath {
  private GeoMath() {}

  public static double distance(double lat1, double lon1, double lat2, double lon2, double radius) {
    double p1 = Math.toRadians(lat1),
        p2 = Math.toRadians(lat2),
        dp = p2 - p1,
        dl = Math.toRadians(lon2 - lon1);
    double a =
        Math.pow(Math.sin(dp / 2), 2) + Math.cos(p1) * Math.cos(p2) * Math.pow(Math.sin(dl / 2), 2);
    return 2 * radius * Math.asin(Math.sqrt(Math.max(0, Math.min(1, a))));
  }
}
