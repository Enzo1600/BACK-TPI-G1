package com.auxiruta.commons.security;

import java.util.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

public final class TokenSupport {
  private TokenSupport() {}

  public static Collection<SimpleGrantedAuthority> roles(Jwt jwt) {
    Object realm = jwt.getClaims().get("realm_access");
    if (!(realm instanceof Map<?, ?> map) || !(map.get("roles") instanceof Collection<?> roles))
      return List.of();
    return roles.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .filter(Set.of("DRIVER", "OPERATOR", "ADMIN", "FLEET_SERVICE")::contains)
        .distinct()
        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
        .toList();
  }

  public static OAuth2TokenValidator<Jwt> validator(String issuer, Set<String> audiences) {
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer),
        jwt -> {
          boolean valid =
              jwt.getExpiresAt() != null
                  && jwt.getSubject() != null
                  && !jwt.getSubject().isBlank()
                  && jwt.getAudience() != null
                  && jwt.getAudience().stream().anyMatch(audiences::contains);
          return valid
              ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(
                  new OAuth2Error("invalid_token", "Token no valido para este servicio", null));
        });
  }

  public static boolean permits(Jwt jwt, boolean technical, String... requiredRoles) {
    String audience = technical ? "auxiruta-fleet" : "auxiruta-api";
    String client = technical ? "assistance-service" : "auxiruta-api";
    return jwt.getAudience() != null
        && jwt.getAudience().contains(audience)
        && client.equals(jwt.getClaimAsString("azp"))
        && roles(jwt).stream()
            .anyMatch(
                a ->
                    Arrays.stream(requiredRoles)
                        .anyMatch(role -> a.getAuthority().equals("ROLE_" + role)));
  }

  public static String subject(Jwt jwt) {
    return jwt.getSubject();
  }

  public static void requireOwner(Jwt jwt, String ownerSubject) {
    if (ownerSubject == null || !ownerSubject.equals(subject(jwt)))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado");
  }
}
