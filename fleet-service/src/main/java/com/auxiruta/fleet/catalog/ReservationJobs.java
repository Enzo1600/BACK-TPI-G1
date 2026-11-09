package com.auxiruta.fleet.catalog;
import java.util.UUID;
import java.time.Clock;
import java.sql.Timestamp;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component @EnableScheduling @ConditionalOnProperty(name="auxiruta.automation.enabled",havingValue="true",matchIfMissing=true)
public class ReservationJobs {
 private final JdbcTemplate jdbc;private final ReservationService reservations;private final Clock clock;
 public ReservationJobs(JdbcTemplate jdbc,ReservationService reservations,Clock clock){this.jdbc=jdbc;this.reservations=reservations;this.clock=clock;}
 @Scheduled(fixedDelayString="${auxiruta.automation.fleet-delay-ms:15000}",initialDelay=15000)
 public void expire(){for(UUID id:jdbc.queryForList("select id from resource_reservations where status='HELD' and expires_at<=? order by expires_at,id limit 100",UUID.class,Timestamp.from(clock.instant())))try{reservations.expireDue(id);}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Reservation expiration deferred id={} type={}",id,e.getClass().getSimpleName());}}
}
