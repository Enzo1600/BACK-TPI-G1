# AuxiRuta — TPI Backend de Aplicaciones

Repositorio del Grupo 1 para el Trabajo Práctico Integrador 2026.

AuxiRuta coordina solicitudes de asistencia y remolque de vehículos livianos: evalúa compatibilidad y recorridos, reserva una grúa y gestiona el servicio hasta su finalización o resolución de una incidencia.

## Documentación vigente

| Documento | Contenido |
|---|---|
| [Contexto del proyecto](docs/AuxiRuta-contexto-proyecto.md) | Nombres, versiones propuestas, dependencias, seguridad, puertos y próximos pasos |
| [Resumen del dominio](docs/AuxiRuta.md) | Alcance, actores y división en microservicios |
| [Reglas de negocio AR-MVP-1](docs/AuxiRuta-reglas-de-negocio-MVP.md) | Restricciones fijas, cobertura, distancias, plazos y casos de borde |
| [Parámetros del MVP](docs/AuxiRuta-parametros-MVP.yml) | Valores de referencia para configurar los servicios |
| [Estados y transiciones](docs/AuxiRuta-estados-y-transiciones.md) | Solicitud, vehículo, grúa, reserva e incidencia |
| [Operaciones por rol y alcance](docs/AuxiRuta-operaciones-por-rol-y-alcance-MVP.md) | Endpoints propuestos, permisos y funcionalidades incluidas/excluidas |
| [Modelo persistente](docs/AuxiRuta-modelo-persistente-MVP.md) | Entidades y tablas; incluye ajustes por las reglas AR-MVP-1 |
| [Logging básico](docs/AuxiRuta-logging-basico.md) | Eventos, niveles, protección de datos y retención de logs |

Las reglas AR-MVP-1 prevalecen sobre resúmenes anteriores para restricciones y plazos. El archivo de contexto fija las convenciones técnicas. Los parámetros deben coincidir con las reglas.

## Decisiones actuales

- Cobertura comercial circular de 15 km desde el punto de referencia próximo a Plaza San Martín, Córdoba. No representa el límite municipal exacto.
- Dos microservicios funcionales: `assistance-service` y `fleet-service`, con API Gateway y Keycloak.
- Una instancia PostgreSQL con tres bases separadas: `assistance_db`, `fleet_db` y `keycloak_db`.
- OpenRouteService como API externa para distancias y tiempos estimados de recorrido.
- Logging básico incluido por decisión del equipo; monitoreo avanzado opcional.
- Notificaciones a usuarios fuera del alcance por ahora.

## Estado del proyecto

El repositorio contiene el diseño y la documentación. Los proyectos ejecutables, contratos OpenAPI, diagramas e infraestructura todavía deben implementarse. Los próximos pasos están detallados en el documento de contexto.
