package com.auxiruta.assistance.config;

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
    JwtDecoder jwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String keys) {
        var decoder = NimbusJwtDecoder.withJwkSetUri(keys).build();
        decoder.setJwtValidator(TokenSupport.validator(issuer, Set.of("auxiruta-api")));
        return decoder;
    }
    @Bean
    SecurityFilterChain security(HttpSecurity http, SecurityErrors errors) throws Exception {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> new java.util.ArrayList<>(TokenSupport.roles(jwt)));
        http.csrf(c -> c.disable()).httpBasic(c -> c.disable()).formLogin(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
            .oauth2ResourceServer(o -> o.authenticationEntryPoint(errors).accessDeniedHandler(errors)
                .jwt(j -> j.jwtAuthenticationConverter(converter)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/v1/vehicles").access(allowed(false, "DRIVER"))
                .requestMatchers(HttpMethod.GET, "/api/v1/vehicles").access(allowed(false, "DRIVER"))
                .requestMatchers(HttpMethod.GET, "/api/v1/vehicles/{id}").access(allowed(false, "DRIVER"))
                .requestMatchers(HttpMethod.PATCH, "/api/v1/vehicles/{id}").access(allowed(false, "DRIVER"))
                .requestMatchers(HttpMethod.PATCH, "/api/v1/vehicles/{id}/registration-status").access(allowed(false, "DRIVER"))
                .requestMatchers(HttpMethod.POST, "/api/v1/assistance-requests").access(allowed(false, "DRIVER"))
                .requestMatchers(HttpMethod.POST, "/api/v1/assistance-requests/{id}/cancellations").access(allowed(false, "DRIVER"))
                .requestMatchers(HttpMethod.GET, "/api/v1/assistance-requests").access(allowed(false, "DRIVER", "OPERATOR", "ADMIN"))
                .requestMatchers(HttpMethod.GET, "/api/v1/assistance-requests/{id}").access(allowed(false, "DRIVER", "OPERATOR", "ADMIN"))
                .requestMatchers(HttpMethod.GET, "/api/v1/assistance-requests/{id}/history").access(allowed(false, "DRIVER", "OPERATOR", "ADMIN"))
                .requestMatchers(HttpMethod.GET, "/api/v1/operator/assignments").access(allowed(false, "OPERATOR"))
                .requestMatchers(HttpMethod.GET, "/api/v1/assignments/{id}").access(allowed(false, "OPERATOR", "ADMIN"))
                .requestMatchers(HttpMethod.POST, "/api/v1/assignments/{id}/acceptance").access(allowed(false, "OPERATOR"))
                .requestMatchers(HttpMethod.POST, "/api/v1/assignments/{id}/rejection").access(allowed(false, "OPERATOR"))
                .requestMatchers(HttpMethod.POST, "/api/v1/assistance-requests/{id}/dispatch").access(allowed(false, "OPERATOR"))
                .requestMatchers(HttpMethod.POST, "/api/v1/assistance-requests/{id}/arrival").access(allowed(false, "OPERATOR"))
                .requestMatchers(HttpMethod.POST, "/api/v1/assistance-requests/{id}/loading").access(allowed(false, "OPERATOR"))
                .requestMatchers(HttpMethod.POST, "/api/v1/assistance-requests/{id}/transport-start").access(allowed(false, "OPERATOR"))
                .requestMatchers(HttpMethod.POST, "/api/v1/assistance-requests/{id}/completion").access(allowed(false, "OPERATOR"))
                .requestMatchers(HttpMethod.GET, "/api/v1/configuration/assistance").access(allowed(false, "ADMIN"))
                .requestMatchers(HttpMethod.GET, "/v3/api-docs/**").access(allowed(false, "DRIVER", "OPERATOR", "ADMIN"))
                .requestMatchers(HttpMethod.GET, "/swagger-ui/**").access(allowed(false, "DRIVER", "OPERATOR", "ADMIN"))
                .requestMatchers(HttpMethod.GET, "/swagger-ui.html").access(allowed(false, "DRIVER", "OPERATOR", "ADMIN"))
                .anyRequest().denyAll());
        return http.build();
    }
    private AuthorizationManager<RequestAuthorizationContext> allowed(boolean technical, String... roles) {
        return (authentication, context) -> new AuthorizationDecision(
            authentication.get() instanceof JwtAuthenticationToken token && TokenSupport.permits(token.getToken(), technical, roles));
    }
}
