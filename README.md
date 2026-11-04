# AuxiRuta — TPI Backend de Aplicaciones

Repositorio del Grupo 1 para el Trabajo Práctico Integrador 2026.

AuxiRuta coordina solicitudes de asistencia y remolque de vehículos livianos: evalúa compatibilidad y recorridos, reserva una grúa y gestiona el servicio hasta su finalización o resolución de una incidencia.

## Documentación vigente

Documento principal: **[Decisiones arquitectónicas](docs/DECISIONES_ARQUITECTONICAS.md)**. Consolida el dominio, la arquitectura, los contratos propuestos, el glosario y la correspondencia con los requisitos mínimos.

- `docs/documentacionPartes/`: documentación funcional y técnica.
- `docs/diagramas/`: diagramas del sistema, [C1](docs/diagramas/AuxiRuta_C1.png) y [C2](docs/diagramas/AuxiRuta_C2.png).

| Documento | Contenido |
|---|---|
| [Contexto del proyecto](docs/documentacionPartes/AuxiRuta-contexto-proyecto.md) | Nombres, versiones propuestas, dependencias, seguridad, puertos y próximos pasos |
| [Resumen del dominio](docs/documentacionPartes/AuxiRuta.md) | Alcance, actores y división en microservicios |
| [Reglas de negocio AR-MVP-1](docs/documentacionPartes/AuxiRuta-reglas-de-negocio-MVP.md) | Restricciones fijas, cobertura, distancias, plazos y casos de borde |
| [Parámetros del MVP](docs/documentacionPartes/AuxiRuta-parametros-MVP.yml) | Valores de referencia para configurar los servicios |
| [Estados y transiciones](docs/documentacionPartes/AuxiRuta-estados-y-transiciones.md) | Solicitud, vehículo, grúa, reserva e incidencia |
| [Operaciones por rol y alcance](docs/documentacionPartes/AuxiRuta-operaciones-por-rol-y-alcance-MVP.md) | Endpoints propuestos, permisos y funcionalidades incluidas/excluidas |
| [Modelo persistente](docs/documentacionPartes/AuxiRuta-modelo-persistente-MVP.md) | Entidades y tablas; incluye ajustes por las reglas AR-MVP-1 |
| [Logging básico](docs/documentacionPartes/AuxiRuta-logging-basico.md) | Eventos, niveles, protección de datos y retención de logs |

DECISIONES_ARQUITECTONICAS.md constituye la referencia consolidada vigente. Los documentos parciales se conservan como material de detalle; ante contradicciones prevalece la especificación consolidada. Los parámetros deben coincidir con la línea base AR-MVP-1 allí definida.

## Decisiones actuales

- Cobertura comercial circular de 15 km desde el punto de referencia próximo a Plaza San Martín, Córdoba. No representa el límite municipal exacto.
- Dos microservicios funcionales: `assistance-service` y `fleet-service`, con API Gateway y Keycloak.
- Una instancia PostgreSQL con tres bases separadas: `assistance_db`, `fleet_db` y `keycloak_db`.
- OpenRouteService como API externa para distancias y tiempos estimados de recorrido.
- Logging básico incluido por decisión del equipo; monitoreo avanzado opcional.
- Notificaciones a usuarios fuera del alcance por ahora.

## Estado del proyecto

El repositorio contiene el diseño y la documentación. Los proyectos ejecutables, contratos OpenAPI e infraestructura todavía deben implementarse. Los archivos de C1 y C2 están disponibles en `docs/diagramas/` y deben mantenerse alineados con la solución. Los próximos pasos están detallados en el documento de contexto.
