# AuxiRuta — TPI Backend de Aplicaciones

Repositorio del Grupo 1 para el Trabajo Práctico Integrador 2026.

AuxiRuta coordina solicitudes de asistencia y remolque de vehículos livianos: evalúa compatibilidad y recorridos, reserva una grúa y gestiona su ciclo de vida.

## Documentación vigente

Documento principal: **[Decisiones arquitectónicas](docs/DECISIONES_ARQUITECTONICAS.md)**. Consolida el dominio, la arquitectura, los contratos propuestos, el glosario y la correspondencia con los requisitos mínimos.

- `docs/documentacionPartes/`: documentación funcional y técnica.

- `docs/diagramas/`: diagramas vigentes [C1 — Contexto](docs/diagramas/AuxiRuta_C1.drawio.png), [C2 — Contenedores](docs/diagramas/AuxiRuta_C2.png) y [BPMN — Proceso principal](docs/diagramas/bpmn.png).

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

El repositorio contiene las fases 1 a 4: cuatro proyectos Maven, infraestructura Docker, rutas del Gateway y seguridad JWT con permisos por rol en las tres aplicaciones. Fleet ya implementa la administración de operadores y grúas. Los vehículos, solicitudes y reservas se incorporan en las siguientes fases. Los diagramas vigentes de [contexto C1](docs/diagramas/AuxiRuta_C1.drawio.png), [contenedores C2](docs/diagramas/AuxiRuta_C2.png) y [proceso principal BPMN](docs/diagramas/bpmn.png) están disponibles en `docs/diagramas/`. Los próximos pasos están detallados en el documento de contexto.

## Demostración temporal prevista

El perfil `demo` reducirá los plazos de búsqueda, aceptación y despacho para la demostración, conservando los cupos. Los [parámetros de referencia](docs/documentacionPartes/AuxiRuta-parametros-demo.yml) complementan los valores normales. Las pruebas de límites utilizarán Clock controlado. Esta configuración está documentada y pendiente de integrar en las aplicaciones.

## Preparación para implementar

Revisión documental del 4 de noviembre de 2026 completada, versión 2.5, sin cambiar AR-MVP-5. El [contexto del proyecto](docs/documentacionPartes/AuxiRuta-contexto-proyecto.md#preparación-para-la-construcción--revisión-del-4-de-noviembre-de-2026) detalla el orden de construcción y las verificaciones pendientes del entorno, contratos, persistencia, seguridad y API externa. Los requisitos están contemplados en el diseño; su cumplimiento se demostrará con ejecución y pruebas.

## Construcción por fases

La [guía de cambios y aprendizaje](docs/cambios.md) registra las fases implementadas, explica el código y contiene los comandos de compilación. Instalar primero auxiruta-commons y luego ejecutar verify en cada aplicación mediante su Maven Wrapper.

## Infraestructura local — fase 2

La [guía de infraestructura](infra/README.md) contiene las instrucciones de Docker Compose, configuración local y pruebas de identidad. Con Docker Desktop iniciado y `.env` preparado, ejecutar `docker compose up -d --build`. Gateway se publica en localhost:8000 y Keycloak en localhost:8080. Los permisos por rol y la administración de Fleet están implementados; los demás procesos se incorporan según el plan de fases.

## Seguridad — fase 3

Gateway y microservicios validan firma, emisor, vigencia y audiencia del JWT. Cada ruta exige su rol y cliente autorizado; ADMIN no reemplaza a DRIVER u OPERATOR. Las rutas internas de Fleet aceptan únicamente credenciales técnicas y no se publican por Gateway.

La prueba `python infra/tests/verify_security.py` obtiene tokens locales y verifica accesos permitidos/rechazados, enrutamiento y errores. Requiere Docker iniciado y `docker compose up -d --build`. Los documentos OpenAPI JSON se consultan con Bearer en `/docs/fleet/v3/api-docs` y `/docs/assistance/v3/api-docs`; Fleet documenta las operaciones administrativas de la fase 4; Assistance incorporará sus operaciones a partir de la fase 5. No se incluye una sesión interactiva de Swagger UI en esta fase.

En instalaciones creadas con la fase 2, ejecutar una vez `python infra/keycloak/ensure_basic_scope.py` para incorporar el identificador `sub` a los tokens humanos. La corrección es idempotente y conserva los datos; las instalaciones nuevas importan la configuración corregida. Detalles y resultados en [cambios](docs/cambios.md).

## Administración de flota — fase 4

El [plan de implementación](docs/FASES_IMPLEMENTACION.md) conserva la división acordada de las diez fases. Fleet permite a ADMIN preparar operadores, habilitaciones, grúas y vínculos; OPERATOR consulta su perfil/equipo y declara disponibilidad y ubicación. El [registro de cambios](docs/cambios.md) explica entidades, DTOs, SQL, transacciones y verificaciones.

Ejecutar `docker compose up -d --build` para construir y aplicar el esquema aditivo de Fleet. La prueba `python infra/tests/verify_fleet.py` verifica la administración en el entorno local y deja recursos identificados como pruebas. Su ejecución utiliza el perfil operator.demo; consultar las condiciones en docs/cambios.md. La especificación autenticada se obtiene en `/docs/fleet/v3/api-docs` por Gateway.
