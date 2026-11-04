# AuxiRuta — TPI Backend de Aplicaciones

Repositorio del Grupo 1 para el Trabajo Práctico Integrador 2026.

AuxiRuta coordina solicitudes de asistencia y remolque de vehículos livianos: evalúa compatibilidad y recorridos, reserva una grúa y gestiona su ciclo de vida.

## Documentación vigente

Documento principal: **[Decisiones arquitectónicas](docs/DECISIONES_ARQUITECTONICAS.md)**. Consolida el dominio, la arquitectura, los contratos propuestos, el glosario y la correspondencia con los requisitos mínimos.

- `docs/documentacionPartes/`: documentación funcional y técnica.
- `docs/diagramas/`: diagramas vigentes [C1 y C2 en Mermaid](docs/diagramas/AuxiRuta_C1_C2.md). Los PNG anteriores se conservan como antecedentes históricos y no describen la línea base actual.

| Documento | Contenido |
|---|---|
| [Contexto del proyecto](docs/documentacionPartes/AuxiRuta-contexto-proyecto.md) | Nombres, versiones propuestas, dependencias, seguridad, puertos y próximos pasos |
| [Resumen del dominio](docs/documentacionPartes/AuxiRuta.md) | Alcance, actores y división en microservicios |
| [Reglas de negocio AR-MVP-5](docs/documentacionPartes/AuxiRuta-reglas-de-negocio-MVP.md) | Restricciones fijas, cobertura, distancias, plazos y casos de borde |
| [Parámetros del MVP](docs/documentacionPartes/AuxiRuta-parametros-MVP.yml) | Valores de referencia para configurar los servicios |
| [Estados y transiciones](docs/documentacionPartes/AuxiRuta-estados-y-transiciones.md) | Solicitud, vehículo, grúa y reserva |
| [Operaciones por rol y alcance](docs/documentacionPartes/AuxiRuta-operaciones-por-rol-y-alcance-MVP.md) | Endpoints propuestos, permisos y funcionalidades incluidas/excluidas |
| [Modelo persistente](docs/documentacionPartes/AuxiRuta-modelo-persistente-MVP.md) | Entidades y tablas consolidadas de AR-MVP-5 |
| [DER editable](docs/diagramas/AuxiRuta_DER.md) | Atributos, relaciones y cardinalidades de las bases funcionales |
| [Logging básico](docs/documentacionPartes/AuxiRuta-logging-basico.md) | Eventos, niveles, protección de datos y retención de logs |

DECISIONES_ARQUITECTONICAS.md constituye la referencia consolidada vigente. Los documentos parciales se conservan como material de detalle; ante contradicciones prevalece la especificación consolidada. Los parámetros deben coincidir con la línea base AR-MVP-5 allí definida.

## Decisiones actuales

- Cobertura comercial circular de 15 km desde el punto de referencia próximo a Plaza San Martín, Córdoba. No representa el límite municipal exacto.
- Dos microservicios funcionales: `assistance-service` y `fleet-service`, con API Gateway y Keycloak.
- Una instancia PostgreSQL con tres bases separadas: `assistance_db`, `fleet_db` y `keycloak_db`.
- OpenRouteService como API externa para distancias y tiempos estimados de recorrido.
- Logging básico incluido por decisión del equipo; monitoreo avanzado opcional.
- Una única empresa administra el alta de operadores, sus habilitaciones y su vínculo con una grúa. No hay registro libre de prestadores.
- Roles humanos: DRIVER, OPERATOR y ADMIN. ADMIN administra recursos y consulta; no interviene en decisiones de asistencia.
- Cancelación por el conductor solo antes de la primera aceptación. Búsquedas alternativas automáticas antes del despacho, con dos reasignaciones como máximo.
- Datos correctos, vehículo accesible, destino disponible y continuidad personal del operador asumidos.
- Notificaciones a usuarios fuera del alcance por ahora.

## Estado del proyecto

El repositorio contiene el diseño y la documentación. Los proyectos ejecutables, contratos OpenAPI e infraestructura todavía deben implementarse. La fuente C1/C2 vigente está disponible en `docs/diagramas/AuxiRuta_C1_C2.md`; los PNG previos son históricos. Los próximos pasos están detallados en el documento de contexto.

## Demostración temporal prevista

El perfil `demo` reducirá los plazos de búsqueda, aceptación y despacho para la demostración, conservando los cupos. Los [parámetros de referencia](docs/documentacionPartes/AuxiRuta-parametros-demo.yml) complementan los valores normales. Las pruebas de límites utilizarán Clock controlado. Esta configuración está documentada y pendiente de integrar en las aplicaciones.

## Preparación para implementar

Revisión documental del 4 de noviembre de 2026 completada, versión 2.5, sin cambiar AR-MVP-5. El [contexto del proyecto](docs/documentacionPartes/AuxiRuta-contexto-proyecto.md#preparación-para-la-construcción--revisión-del-4-de-noviembre-de-2026) detalla el orden de construcción y las verificaciones pendientes del entorno, contratos, persistencia, seguridad y API externa. Los requisitos están contemplados en el diseño; su cumplimiento se demostrará con ejecución y pruebas.
