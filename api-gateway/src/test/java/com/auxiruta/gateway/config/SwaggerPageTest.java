package com.auxiruta.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
class SwaggerPageTest {
  @Autowired WebTestClient client;
  @MockitoBean ReactiveJwtDecoder decoder;

  @Test
  void servesPageAndBundledAssetsWithoutLogin() {
    for (String path :
        new String[] {
          "/swagger-ui.html",
          "/swagger-ui/app.js",
          "/webjars/swagger-ui/5.34.0/swagger-ui.css",
          "/webjars/swagger-ui/5.34.0/swagger-ui-bundle.js"
        }) {
      client.get().uri(path).exchange().expectStatus().isOk();
    }
  }

  @Test
  void contractsAndBusinessStillRequireAuthentication() {
    for (String path :
        new String[] {
          "/docs/fleet/v3/api-docs",
          "/docs/assistance/v3/api-docs",
          "/api/v1/vehicles",
          "/internal/fleet/candidates"
        }) {
      client.get().uri(path).exchange().expectStatus().isUnauthorized();
    }
  }
}
