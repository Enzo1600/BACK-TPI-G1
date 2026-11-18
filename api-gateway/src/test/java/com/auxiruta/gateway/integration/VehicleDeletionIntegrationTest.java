package com.auxiruta.gateway.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static com.auxiruta.gateway.integration.BackendClient.*;

@EnabledIfSystemProperty(named = "auxiruta.integration", matches = "true")
class VehicleDeletionIntegrationTest {
  @Test
  void deletesThroughGatewayAndEnforcesRole() throws Exception {
    initialize();
    String path =
        "/api/v1/vehicles/"
            + call(
                    "DRIVER",
                    "POST",
                    "/api/v1/vehicles",
                    object(
                        "licensePlate",
                        "DEL"
                            + java.util
                                .UUID
                                .randomUUID()
                                .toString()
                                .replace("-", "")
                                .substring(0, 7),
                        "vehicleType",
                        "CAR",
                        "declaredWeightKg",
                        1000),
                    201)
                .path("id")
                .asText();
    call("OPERATOR", "DELETE", path, null, 403);
    call("DRIVER", "DELETE", path, null, 204);
    call("DRIVER", "GET", path, null, 404);
    call("DRIVER", "DELETE", path, null, 404);
  }
}
