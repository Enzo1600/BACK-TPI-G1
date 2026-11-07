package com.auxiruta.fleet.config;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
@WebMvcTest(SecurityConfigurationTest.Probe.class)
@Import({SecurityConfiguration.class,SecurityErrors.class,SecurityConfigurationTest.Probe.class})
class SecurityConfigurationTest {
 @Autowired MockMvc mvc;
 @MockitoBean JwtDecoder decoder;
 @RestController static class Probe {
  @GetMapping("/api/v1/operators") String publicRoute() { return "ok"; }
  @GetMapping("/internal/fleet/candidates") String internalRoute() { return "ok"; }
 }
 @Test void noTokenRejected() throws Exception { mvc.perform(get("/api/v1/operators")).andExpect(status().isUnauthorized()); }
 @Test void authorizedRoleAccepted() throws Exception {
  mvc.perform(get("/api/v1/operators").with(jwt().jwt(j->j.audience(List.of("auxiruta-api")).claim("azp","auxiruta-api").claim("realm_access",Map.of("roles",List.of("ADMIN")))))).andExpect(status().isOk());
 }
 @Test void wrongRoleRejected() throws Exception {
  mvc.perform(get("/api/v1/operators").with(jwt().jwt(j->j.audience(List.of("auxiruta-api")).claim("azp","auxiruta-api").claim("realm_access",Map.of("roles",List.of("OPERATOR")))))).andExpect(status().isForbidden());
 }
 @Test void humanCannotCallInternal() throws Exception {
  mvc.perform(get("/internal/fleet/candidates").with(jwt().jwt(j->j.audience(List.of("auxiruta-api")).claim("azp","auxiruta-api").claim("realm_access",Map.of("roles",List.of("ADMIN")))))).andExpect(status().isForbidden());
 }
 @Test void technicalClientAcceptedInternally() throws Exception {
 mvc.perform(get("/internal/fleet/candidates").with(jwt().jwt(j->j.audience(List.of("auxiruta-fleet")).claim("azp","assistance-service").claim("realm_access",Map.of("roles",List.of("FLEET_SERVICE")))))).andExpect(status().isOk());
}
}
