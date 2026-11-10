package com.auxiruta.assistance.routing;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.*;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import com.fasterxml.jackson.databind.*;
import com.auxiruta.commons.GeoMath;
import static com.auxiruta.assistance.routing.RoutingFailure.Reason.*;

@Component
public class OpenRouteServiceClient implements RouteProvider {
  private final RestClient http;
  private final String key;
  private final RoutingBudget budget;
  private final Clock clock;
  private final ObjectMapper mapper;
  private final Map<String, Route> cache = new ConcurrentHashMap<>();

  public OpenRouteServiceClient(
      @Value("${ors.base-url:https://api.heigit.org/openrouteservice/v2}") String url,
      @Value("${ors.api-key:}") String key,
      RoutingBudget budget,
      Clock clock,
      ObjectMapper mapper) {
    this.key = key;
    this.budget = budget;
    this.clock = clock;
    this.mapper = mapper;
    var client =
        java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
            .build();
    var factory = new JdkClientHttpRequestFactory(client);
    factory.setReadTimeout(Duration.ofSeconds(5));
    this.http = RestClient.builder().baseUrl(url).requestFactory(factory).build();
  }

  private static String coordinate(BigDecimal n) {
    return n.stripTrailingZeros().toPlainString();
  }

  @Override
  public Route route(Point from, Point to, String revision, int maxAgeSeconds, int maxSnapMeters) {
    String cacheKey =
        coordinate(from.latitude())
            + ","
            + coordinate(from.longitude())
            + ":"
            + coordinate(to.latitude())
            + ","
            + coordinate(to.longitude())
            + ":driving-car:"
            + revision
            + ":"
            + maxSnapMeters;
    Route existing = cache.get(cacheKey);
    Instant now = clock.instant();
    if (existing != null
        && !existing.obtainedAt().isAfter(now)
        && !existing.obtainedAt().isBefore(now.minusSeconds(maxAgeSeconds))) return existing;
    if (key.isBlank()) throw new RoutingFailure(CONFIGURATION);
    if (!budget.reserve()) throw new RoutingFailure(RATE_LIMIT);
    try {
      ResponseEntity<JsonNode> response =
          http.post()
              .uri("/directions/driving-car/geojson")
              .header(HttpHeaders.AUTHORIZATION, key)
              .contentType(MediaType.APPLICATION_JSON)
              .body(
                  Map.of(
                      "coordinates",
                      List.of(
                          List.of(from.longitude(), from.latitude()),
                          List.of(to.longitude(), to.latitude())),
                      "preference",
                      "recommended",
                      "units",
                      "m"))
              .retrieve()
              .toEntity(JsonNode.class);
      if ("0".equals(response.getHeaders().getFirst("x-ratelimit-remaining")))
        budget.defer(reset(response.getHeaders(), now, now.plusSeconds(86400)));
      Route result = parse(response.getBody(), from, to, maxSnapMeters, clock.instant());
      if (cache.size() >= 500) cache.clear();
      cache.put(cacheKey, result);
      return result;
    } catch (RestClientResponseException e) {
      HttpHeaders headers =
          e.getResponseHeaders() == null ? new HttpHeaders() : e.getResponseHeaders();
      String body = e.getResponseBodyAsString();
      int code = 0;
      try {
        code = mapper.readTree(body).path("error").path("code").asInt();
      } catch (Exception ignored) {
      }
      if ((e.getStatusCode().value() == 400 || e.getStatusCode().value() == 404)
          && (code == 2009 || code == 2010)) throw new RoutingFailure(NO_ROUTE);
      if (e.getStatusCode().value() == 429) {
        budget.defer(retryAfter(headers, now));
        throw new RoutingFailure(RATE_LIMIT);
      }
      if (e.getStatusCode().value() == 403
          && (body.toLowerCase(Locale.ROOT).contains("quota")
              || "0".equals(headers.getFirst("x-ratelimit-remaining")))) {
        budget.defer(reset(headers, now, now.plusSeconds(86400)));
        throw new RoutingFailure(DAILY_QUOTA);
      }
      if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
        budget.defer(now.plusSeconds(300));
        throw new RoutingFailure(CONFIGURATION);
      }
      throw new RoutingFailure(UNAVAILABLE);
    } catch (ResourceAccessException e) {
      throw new RoutingFailure(UNAVAILABLE);
    } catch (RestClientException e) {
      throw new RoutingFailure(INVALID_RESPONSE);
    }
  }

  static Instant retryAfter(HttpHeaders headers, Instant now) {
    String value = headers.getFirst("Retry-After");
    if (value != null)
      try {
        return now.plusSeconds(Math.max(1, Long.parseLong(value)));
      } catch (NumberFormatException ignored) {
        try {
          return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                  .toInstant()
                  .isAfter(now)
              ? ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
              : now.plusSeconds(60);
        } catch (Exception ignoredDate) {
        }
      }
    return now.plusSeconds(60);
  }

  static Instant reset(HttpHeaders headers, Instant now, Instant fallback) {
    try {
      Instant reset = Instant.ofEpochSecond(Long.parseLong(headers.getFirst("x-ratelimit-reset")));
      return reset.isAfter(now) ? reset : fallback;
    } catch (Exception e) {
      return fallback;
    }
  }

  static Route parse(JsonNode root, Point from, Point to, int maxSnap, Instant now) {
    try {
      JsonNode feature = root.path("features").path(0),
          summary = feature.path("properties").path("summary"),
          geometry = feature.path("geometry").path("coordinates"),
          way = feature.path("properties").path("way_points");
      if (!summary.path("distance").isNumber()
          || !summary.path("duration").isNumber()
          || geometry.size() < 2
          || way.size() != 2) throw new RoutingFailure(INVALID_RESPONSE);
      BigDecimal distance = summary.get("distance").decimalValue(),
          duration = summary.get("duration").decimalValue();
      if (distance.signum() < 0 || duration.signum() < 0)
        throw new RoutingFailure(INVALID_RESPONSE);
      int first = way.get(0).asInt(-1), last = way.get(1).asInt(-1);
      if (first < 0 || last < first || last >= geometry.size())
        throw new RoutingFailure(INVALID_RESPONSE);
      JsonNode a = geometry.get(first), z = geometry.get(last);
      if (a.size() < 2
          || z.size() < 2
          || !a.get(0).isNumber()
          || !a.get(1).isNumber()
          || !z.get(0).isNumber()
          || !z.get(1).isNumber()) throw new RoutingFailure(INVALID_RESPONSE);
      for (JsonNode point : List.of(a, z))
        if (!Double.isFinite(point.get(0).asDouble())
            || !Double.isFinite(point.get(1).asDouble())
            || Math.abs(point.get(0).asDouble()) > 180
            || Math.abs(point.get(1).asDouble()) > 90) throw new RoutingFailure(INVALID_RESPONSE);
      if (GeoMath.distance(
                  from.latitude().doubleValue(),
                  from.longitude().doubleValue(),
                  a.get(1).asDouble(),
                  a.get(0).asDouble(),
                  6371000)
              > maxSnap
          || GeoMath.distance(
                  to.latitude().doubleValue(),
                  to.longitude().doubleValue(),
                  z.get(1).asDouble(),
                  z.get(0).asDouble(),
                  6371000)
              > maxSnap) throw new RoutingFailure(SNAP_TOO_FAR);
      return new Route(distance, duration, now.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    } catch (RoutingFailure e) {
      throw e;
    } catch (Exception e) {
      throw new RoutingFailure(INVALID_RESPONSE);
    }
  }
}
