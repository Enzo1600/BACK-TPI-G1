package com.auxiruta.assistance.routing;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
@Service
public class RoutingBudget {
 private final JdbcTemplate jdbc;private final Clock clock;private final int perMinute,perDay;
 public RoutingBudget(JdbcTemplate jdbc,Clock clock,@Value("${ors.per-minute:40}") int minute,@Value("${ors.per-day:2000}") int day) { this.jdbc=jdbc;this.clock=clock;this.perMinute=minute;this.perDay=day; }
 @Transactional
 public boolean reserve() {
  Instant now=clock.instant();java.sql.Timestamp blocked=jdbc.queryForObject("select blocked_until from routing_budget where id=1 for update",java.sql.Timestamp.class);
  if(blocked!=null && blocked.toInstant().isAfter(now))return false;
  jdbc.update("delete from routing_calls where called_at < ?",java.sql.Timestamp.from(now.minusSeconds(86400)));
  long daily=jdbc.queryForObject("select count(*) from routing_calls",Long.class);
  long minute=jdbc.queryForObject("select count(*) from routing_calls where called_at > ?",Long.class,java.sql.Timestamp.from(now.minusSeconds(60)));
  if(daily>=perDay || minute>=perMinute)return false;
  jdbc.update("insert into routing_calls(id,called_at) values (?,?)",java.util.UUID.randomUUID(),java.sql.Timestamp.from(now));return true;
 }
 @Transactional
 public void defer(Instant until) { jdbc.update("update routing_budget set blocked_until=greatest(blocked_until,?) where id=1",java.sql.Timestamp.from(until)); }
}
