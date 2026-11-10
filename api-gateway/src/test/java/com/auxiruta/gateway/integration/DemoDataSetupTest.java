package com.auxiruta.gateway.integration;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import static com.auxiruta.gateway.integration.BackendClient.*;

@EnabledIfSystemProperty(named = "auxiruta.seed", matches = "true")
class DemoDataSetupTest {
  @Test
  void prepareOperator() throws Exception {
    initialize();
    prepare();
    PrivacyDemoSetupTest.prepare();
  }

  static String prepare() throws Exception {
    var response = raw("OPERATOR", "GET", "/api/v1/operators/me", null, null);
    String operator;
    if (response.status() == 404)
      operator =
          id(
              call(
                  "ADMIN",
                  "POST",
                  "/api/v1/operators",
                  object(
                      "keycloakSubject",
                      claims(TOKENS.get("OPERATOR")).path("sub").asText(),
                      "fullName",
                      "Operador de demostracion AuxiRuta"),
                  201));
    else {
      assertEquals(200, response.status());
      operator = id(response.body());
    }
    assertEquals(
        404,
        raw("OPERATOR", "GET", "/api/v1/tow-trucks/assigned-to-me", null, null).status(),
        "El operador demo tiene una grua vinculada. Finalizar el servicio o desvincular el recurso libre antes de preparar otra demostracion.");
    call(
        "ADMIN",
        "PATCH",
        "/api/v1/operators/" + operator + "/registration-status",
        object("status", "ACTIVE"),
        200);
    call(
        "ADMIN",
        "PUT",
        "/api/v1/operators/" + operator + "/qualification",
        object(
            "category",
            "LIGHT_TOWING",
            "validFrom",
            Instant.now().minusSeconds(3600).toString(),
            "validUntil",
            Instant.now().plusSeconds(86400).toString()),
        200);
    call(
        "OPERATOR",
        "PATCH",
        "/api/v1/operators/me/availability",
        object("status", "UNAVAILABLE"),
        200);
    return operator;
  }
}
