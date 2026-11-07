package com.auxiruta.assistance.config;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
@Configuration
@OpenAPIDefinition(info=@Info(title="AuxiRuta Assistance API",version="1.0.0",description="Vehículos y solicitudes. Bearer JWT. Coordenadas WGS84 hasta seis decimales. Fechas UTC. page>=0, size 1..100. Creación y cancelación exigen Idempotency-Key de hasta 100 caracteres. Sin evaluación de rutas hasta fase 6."),servers=@Server(url="/"))
@SecurityScheme(name="bearerAuth",type=SecuritySchemeType.HTTP,scheme="bearer",bearerFormat="JWT")
public class OpenApiConfiguration {}
