package com.auxiruta.assistance.routing;

import java.time.*;
import java.sql.Timestamp;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RoutingBudgetTest {
  JdbcTemplate jdbc;
  RoutingBudget budget;
  Instant now = Instant.parse("2026-11-08T12:00:00Z");

  @BeforeEach
  void setup() {
    jdbc = mock(JdbcTemplate.class);
    budget = new RoutingBudget(jdbc, Clock.fixed(now, ZoneOffset.UTC), 40, 2000);
    when(jdbc.queryForObject(anyString(), eq(Timestamp.class)))
        .thenReturn(Timestamp.from(Instant.EPOCH));
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(0L);
    when(jdbc.queryForObject(anyString(), eq(Long.class), any(Timestamp.class))).thenReturn(0L);
  }

  @Test
  void consumesBudgetBeforeNetworkCall() {
    assertTrue(budget.reserve());
    verify(jdbc)
        .update(
            startsWith("insert into routing_calls"),
            any(java.util.UUID.class),
            eq(Timestamp.from(now)));
  }

  @Test
  void minuteLimitPreventsReservation() {
    when(jdbc.queryForObject(anyString(), eq(Long.class), any(Timestamp.class))).thenReturn(40L);
    assertFalse(budget.reserve());
    verify(jdbc, never()).update(startsWith("insert into routing_calls"), any(), any());
  }

  @Test
  void dailyLimitPreventsReservation() {
    when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(2000L);
    assertFalse(budget.reserve());
    verify(jdbc, never()).update(startsWith("insert into routing_calls"), any(), any());
  }

  @Test
  void providerBackoffPreventsReservation() {
    when(jdbc.queryForObject(anyString(), eq(Timestamp.class)))
        .thenReturn(Timestamp.from(now.plusSeconds(60)));
    assertFalse(budget.reserve());
    verify(jdbc, never()).update(anyString(), any(), any());
  }
}
