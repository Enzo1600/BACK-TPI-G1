package com.auxiruta.assistance.config;

import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;

@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "AuxiRuta Assistance API",
            version = "1.0.0",
            description =
                "Vehículos y solicitudes. Bearer JWT. Coordenadas WGS84 hasta seis decimales. Fechas UTC. page>=0, size 1..100. Creación, cancelación, respuestas a propuestas y etapas exigen Idempotency-Key de hasta 100 caracteres. Evaluación real con OpenRouteService; recuperación automática de operaciones remotas. Una respuesta exitosa puede conservar resourceReleasePending hasta confirmar liberación. Los errores usan application/problem+json."),
    servers = @Server(url = "/"))
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiConfiguration {}
