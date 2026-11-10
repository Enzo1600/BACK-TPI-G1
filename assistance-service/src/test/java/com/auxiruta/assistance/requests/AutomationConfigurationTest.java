package com.auxiruta.assistance.requests;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class AutomationConfigurationTest {
  @Test
  void demoChangesOnlyWaitsNotTravelPlanningOrQuotas() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles("docker", "demo");
    var demo = mapper.readTree(new RulesConfiguration(mapper, env).snapshot());
    var normal = mapper.readTree(new RulesConfiguration(mapper).snapshot());
    assertEquals("AR-MVP-5-DEMO", demo.path("rules-version").asText());
    assertEquals(60, demo.path("search").path("attempt-timeout-seconds").asInt());
    assertEquals(2, demo.path("search").path("evaluation-interval-seconds").asInt());
    assertEquals(3, demo.path("search").path("minimum-offer-window-seconds").asInt());
    assertEquals(10, demo.path("fleet").path("hold-seconds").asInt());
    assertEquals(20, demo.path("execution").path("dispatch-deadline-seconds").asInt());
    assertEquals(normal.path("routing"), demo.path("routing"));
    assertEquals(normal.path("integration"), demo.path("integration"));
    assertEquals(900, demo.path("execution").path("rollable-loading-seconds").asInt());
    assertEquals(5, demo.path("search").path("max-offers-per-attempt").asInt());
    assertEquals(2, demo.path("search").path("max-reassignments").asInt());
  }

  @Test
  void recoveryBackoffRemainsBoundedAndNeverDiscards() {
    assertEquals(5, AssignmentStore.retryDelay(0));
    assertEquals(15, AssignmentStore.retryDelay(1));
    assertEquals(30, AssignmentStore.retryDelay(2));
    assertEquals(60, AssignmentStore.retryDelay(3));
    assertEquals(60, AssignmentStore.retryDelay(10000));
  }
}
