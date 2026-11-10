package com.auxiruta.fleet.config;

import java.util.Set;
import com.auxiruta.commons.security.TokenSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

@Configuration
public class SecurityConfiguration {
  @Bean
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String keys) {
    var decoder = NimbusJwtDecoder.withJwkSetUri(keys).build();
    decoder.setJwtValidator(
        TokenSupport.validator(issuer, Set.of("auxiruta-api", "auxiruta-fleet")));
    return decoder;
  }

  @Bean
  SecurityFilterChain security(HttpSecurity http, SecurityErrors errors) throws Exception {
    var converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(
        jwt -> new java.util.ArrayList<>(TokenSupport.roles(jwt)));
    http.csrf(c -> c.disable())
        .httpBasic(c -> c.disable())
        .formLogin(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
        .oauth2ResourceServer(
            o ->
                o.authenticationEntryPoint(errors)
                    .accessDeniedHandler(errors)
                    .jwt(j -> j.jwtAuthenticationConverter(converter)))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/api/v1/operators/me")
                    .access(allowed(false, "OPERATOR"))
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/operators/me/availability")
                    .access(allowed(false, "OPERATOR"))
                    .requestMatchers(HttpMethod.GET, "/api/v1/tow-trucks/assigned-to-me")
                    .access(allowed(false, "OPERATOR"))
                    .requestMatchers(HttpMethod.PUT, "/api/v1/tow-trucks/assigned-to-me/location")
                    .access(allowed(false, "OPERATOR"))
                    .requestMatchers(
                        "/api/v1/operators/me",
                        "/api/v1/operators/me/**",
                        "/api/v1/tow-trucks/assigned-to-me",
                        "/api/v1/tow-trucks/assigned-to-me/**")
                    .denyAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/operators")
                    .access(allowed(false, "ADMIN"))
                    .requestMatchers(HttpMethod.GET, "/api/v1/operators/{id}")
                    .access(allowed(false, "ADMIN"))
                    .requestMatchers(HttpMethod.POST, "/api/v1/operators")
                    .access(allowed(false, "ADMIN"))
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/operators/{id}")
                    .access(allowed(false, "ADMIN"))
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/operators/{id}/registration-status")
                    .access(allowed(false, "ADMIN"))
                    .requestMatchers(HttpMethod.PUT, "/api/v1/operators/{id}/qualification")
                    .access(allowed(false, "ADMIN"))
                    .requestMatchers(HttpMethod.GET, "/api/v1/tow-trucks")
                    .access(allowed(false, "ADMIN"))
                    .requestMatchers(HttpMethod.GET, "/api/v1/tow-trucks/{id}")
                    .access(allowed(false, "ADMIN"))
                    .requestMatchers(HttpMethod.POST, "/api/v1/tow-trucks")
                    .access(allowed(false, "ADMIN"))
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/tow-trucks/{id}")
                    .access(allowed(false, "ADMIN"))
                    .requestMatchers(HttpMethod.PUT, "/api/v1/tow-trucks/{id}/operator")
                    .access(allowed(false, "ADMIN"))
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/tow-trucks/{id}/operator")
                    .access(allowed(false, "ADMIN"))
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/tow-trucks/{id}/operational-status")
                    .access(allowed(false, "ADMIN"))
                    .requestMatchers(HttpMethod.GET, "/api/v1/configuration/fleet")
                    .access(allowed(false, "ADMIN"))
                    .requestMatchers(HttpMethod.GET, "/internal/fleet/candidates")
                    .access(allowed(true, "FLEET_SERVICE"))
                    .requestMatchers(HttpMethod.PUT, "/internal/reservations/{id}")
                    .access(allowed(true, "FLEET_SERVICE"))
                    .requestMatchers(HttpMethod.GET, "/internal/reservations/{id}")
                    .access(allowed(true, "FLEET_SERVICE"))
                    .requestMatchers(HttpMethod.POST, "/internal/reservations/{id}/confirmation")
                    .access(allowed(true, "FLEET_SERVICE"))
                    .requestMatchers(HttpMethod.POST, "/internal/reservations/{id}/release")
                    .access(allowed(true, "FLEET_SERVICE"))
                    .requestMatchers(HttpMethod.GET, "/internal/reservations/{id}/eligibility")
                    .access(allowed(true, "FLEET_SERVICE"))
                    .requestMatchers(HttpMethod.POST, "/internal/reservations/status-queries")
                    .access(allowed(true, "FLEET_SERVICE"))
                    .requestMatchers(HttpMethod.GET, "/v3/api-docs/**")
                    .access(allowed(false, "DRIVER", "OPERATOR", "ADMIN"))
                    .requestMatchers(HttpMethod.GET, "/swagger-ui/**")
                    .access(allowed(false, "DRIVER", "OPERATOR", "ADMIN"))
                    .requestMatchers(HttpMethod.GET, "/swagger-ui.html")
                    .access(allowed(false, "DRIVER", "OPERATOR", "ADMIN"))
                    .anyRequest()
                    .denyAll());
    return http.build();
  }

  private AuthorizationManager<RequestAuthorizationContext> allowed(
      boolean technical, String... roles) {
    return (authentication, context) ->
        new AuthorizationDecision(
            authentication.get() instanceof JwtAuthenticationToken token
                && TokenSupport.permits(token.getToken(), technical, roles));
  }
}
