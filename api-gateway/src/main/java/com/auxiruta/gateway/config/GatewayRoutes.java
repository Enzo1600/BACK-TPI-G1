package com.auxiruta.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
@Configuration
public class GatewayRoutes {
    @Bean
    org.springframework.cloud.gateway.config.HttpClientCustomizer containerDns() {
        return client -> client.resolver(spec -> spec.cacheMaxTimeToLive(java.time.Duration.ofSeconds(10)).cacheNegativeTimeToLive(java.time.Duration.ZERO));
    }
    @Bean
    RouteLocator routes(RouteLocatorBuilder builder,
            @Value("${FLEET_URI:http://fleet-service:8081}") String fleet,
            @Value("${ASSISTANCE_URI:http://assistance-service:8082}") String assistance) {
        return builder.routes()
            .route("fleet", r -> r.path("/api/v1/operators/**", "/api/v1/tow-trucks/**", "/api/v1/configuration/fleet").uri(fleet))
            .route("assistance", r -> r.path("/api/v1/vehicles/**", "/api/v1/assistance-requests/**", "/api/v1/assignments/**", "/api/v1/operator/assignments", "/api/v1/configuration/assistance").uri(assistance))
            .route("fleet-docs", r -> r.path("/docs/fleet/v3/api-docs").filters(f -> f.stripPrefix(2)).uri(fleet))
            .route("assistance-docs", r -> r.path("/docs/assistance/v3/api-docs").filters(f -> f.stripPrefix(2)).uri(assistance))
            .build();
    }
}
