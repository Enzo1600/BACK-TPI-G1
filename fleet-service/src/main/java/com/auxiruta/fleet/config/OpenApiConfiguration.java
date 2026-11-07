package com.auxiruta.fleet.config;
import org.springframework.context.annotation.Configuration;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
@Configuration
@OpenAPIDefinition(info=@Info(title="AuxiRuta Fleet API",version="1.0.0",description="Administración de flota. Fechas UTC ISO-8601. Listados page>=0 y size 1..100. PATCH modifica campos presentes; null no borra campos. Borrar teléfono usando cadena vacía. Identidades y roles se administran previamente en Keycloak."),servers=@Server(url="/"))
@SecurityScheme(name="bearerAuth",type=SecuritySchemeType.HTTP,scheme="bearer",bearerFormat="JWT")
public class OpenApiConfiguration {}
