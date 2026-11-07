package com.auxiruta.gateway.config;

import java.util.Set;
import com.auxiruta.commons.security.TokenSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.authorization.ReactiveAuthorizationManager;
import org.springframework.security.web.server.authorization.AuthorizationContext;

@Configuration
public class SecurityConfiguration {
    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String keys) {
        var decoder = NimbusReactiveJwtDecoder.withJwkSetUri(keys).build();
        decoder.setJwtValidator(TokenSupport.validator(issuer, Set.of("auxiruta-api")));
        return decoder;
    }
    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http, SecurityErrors errors)  {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> new java.util.ArrayList<>(TokenSupport.roles(jwt)));
        http.csrf(ServerHttpSecurity.CsrfSpec::disable).httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
            .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
            .oauth2ResourceServer(o -> o.authenticationEntryPoint(errors).accessDeniedHandler(errors)
                .jwt(j -> j.jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(converter))))
            .authorizeExchange(auth -> auth
                .pathMatchers(HttpMethod.GET, "/api/v1/operators/me").access(allowed(false, "OPERATOR"))
                .pathMatchers(HttpMethod.PATCH, "/api/v1/operators/me/availability").access(allowed(false, "OPERATOR"))
                .pathMatchers(HttpMethod.GET, "/api/v1/tow-trucks/assigned-to-me").access(allowed(false, "OPERATOR"))
                .pathMatchers(HttpMethod.PUT, "/api/v1/tow-trucks/assigned-to-me/location").access(allowed(false, "OPERATOR"))
                .pathMatchers("/api/v1/operators/me", "/api/v1/operators/me/**", "/api/v1/tow-trucks/assigned-to-me", "/api/v1/tow-trucks/assigned-to-me/**").denyAll()
                .pathMatchers(HttpMethod.GET, "/api/v1/operators").access(allowed(false, "ADMIN"))
                .pathMatchers(HttpMethod.GET, "/api/v1/operators/{id}").access(allowed(false, "ADMIN"))
                .pathMatchers(HttpMethod.POST, "/api/v1/operators").access(allowed(false, "ADMIN"))
                .pathMatchers(HttpMethod.PATCH, "/api/v1/operators/{id}").access(allowed(false, "ADMIN"))
                .pathMatchers(HttpMethod.PATCH, "/api/v1/operators/{id}/registration-status").access(allowed(false, "ADMIN"))
                .pathMatchers(HttpMethod.PUT, "/api/v1/operators/{id}/qualification").access(allowed(false, "ADMIN"))
                .pathMatchers(HttpMethod.GET, "/api/v1/tow-trucks").access(allowed(false, "ADMIN"))
                .pathMatchers(HttpMethod.GET, "/api/v1/tow-trucks/{id}").access(allowed(false, "ADMIN"))
                .pathMatchers(HttpMethod.POST, "/api/v1/tow-trucks").access(allowed(false, "ADMIN"))
                .pathMatchers(HttpMethod.PATCH, "/api/v1/tow-trucks/{id}").access(allowed(false, "ADMIN"))
                .pathMatchers(HttpMethod.PUT, "/api/v1/tow-trucks/{id}/operator").access(allowed(false, "ADMIN"))
                .pathMatchers(HttpMethod.DELETE, "/api/v1/tow-trucks/{id}/operator").access(allowed(false, "ADMIN"))
                .pathMatchers(HttpMethod.PATCH, "/api/v1/tow-trucks/{id}/operational-status").access(allowed(false, "ADMIN"))
                .pathMatchers(HttpMethod.GET, "/api/v1/configuration/fleet").access(allowed(false, "ADMIN"))
                .pathMatchers(HttpMethod.POST, "/api/v1/vehicles").access(allowed(false, "DRIVER"))
                .pathMatchers(HttpMethod.GET, "/api/v1/vehicles").access(allowed(false, "DRIVER"))
                .pathMatchers(HttpMethod.GET, "/api/v1/vehicles/{id}").access(allowed(false, "DRIVER"))
                .pathMatchers(HttpMethod.PATCH, "/api/v1/vehicles/{id}").access(allowed(false, "DRIVER"))
                .pathMatchers(HttpMethod.PATCH, "/api/v1/vehicles/{id}/registration-status").access(allowed(false, "DRIVER"))
                .pathMatchers(HttpMethod.POST, "/api/v1/assistance-requests").access(allowed(false, "DRIVER"))
                .pathMatchers(HttpMethod.POST, "/api/v1/assistance-requests/{id}/cancellations").access(allowed(false, "DRIVER"))
                .pathMatchers(HttpMethod.GET, "/api/v1/assistance-requests").access(allowed(false, "DRIVER", "OPERATOR", "ADMIN"))
                .pathMatchers(HttpMethod.GET, "/api/v1/assistance-requests/{id}").access(allowed(false, "DRIVER", "OPERATOR", "ADMIN"))
                .pathMatchers(HttpMethod.GET, "/api/v1/assistance-requests/{id}/history").access(allowed(false, "DRIVER", "OPERATOR", "ADMIN"))
                .pathMatchers(HttpMethod.GET, "/api/v1/operator/assignments").access(allowed(false, "OPERATOR"))
                .pathMatchers(HttpMethod.GET, "/api/v1/assignments/{id}").access(allowed(false, "OPERATOR", "ADMIN"))
                .pathMatchers(HttpMethod.POST, "/api/v1/assignments/{id}/acceptance").access(allowed(false, "OPERATOR"))
                .pathMatchers(HttpMethod.POST, "/api/v1/assignments/{id}/rejection").access(allowed(false, "OPERATOR"))
                .pathMatchers(HttpMethod.POST, "/api/v1/assistance-requests/{id}/dispatch").access(allowed(false, "OPERATOR"))
                .pathMatchers(HttpMethod.POST, "/api/v1/assistance-requests/{id}/arrival").access(allowed(false, "OPERATOR"))
                .pathMatchers(HttpMethod.POST, "/api/v1/assistance-requests/{id}/loading").access(allowed(false, "OPERATOR"))
                .pathMatchers(HttpMethod.POST, "/api/v1/assistance-requests/{id}/transport-start").access(allowed(false, "OPERATOR"))
                .pathMatchers(HttpMethod.POST, "/api/v1/assistance-requests/{id}/completion").access(allowed(false, "OPERATOR"))
                .pathMatchers(HttpMethod.GET, "/api/v1/configuration/assistance").access(allowed(false, "ADMIN"))
                .pathMatchers(HttpMethod.GET, "/docs/fleet/v3/api-docs").access(allowed(false, "DRIVER", "OPERATOR", "ADMIN"))
                .pathMatchers(HttpMethod.GET, "/docs/assistance/v3/api-docs").access(allowed(false, "DRIVER", "OPERATOR", "ADMIN"))
                .anyExchange().denyAll());
        return http.build();
    }
    private ReactiveAuthorizationManager<AuthorizationContext> allowed(boolean technical, String... roles) {
        return (authentication, context) -> authentication.map(auth -> new AuthorizationDecision(
            auth instanceof JwtAuthenticationToken token && TokenSupport.permits(token.getToken(), technical, roles)))
            .defaultIfEmpty(new AuthorizationDecision(false));
    }
}
