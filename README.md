# AuxiRuta — TPI Backend de Aplicaciones

Repositorio del Grupo 1 para el Trabajo Práctico Integrador 2026.

AuxiRuta coordina solicitudes de asistencia y remolque de vehículos livianos: evalúa compatibilidad y recorridos, reserva una grúa y gestiona su ciclo de vida.

## Arranque y entrega

La [guía de entrega](docs/ENTREGA.md) permite levantar el backend, preparar datos, autenticar los tres roles y ejecutar la colección Postman. Incluye los contratos OpenAPI y la verificación Java del proceso completo.

```powershell
.\scripts\Start-AuxiRuta.ps1
.\scripts\Verify-AuxiRuta.ps1 -Integration -IncludeDemo
```

Completar primero el `.env` local como indica la guía. Las pruebas integrales requieren ORS y uso exclusivo del entorno.

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

El repositorio contiene las fases 1 a 10: cuatro proyectos Maven, infraestructura Docker, rutas del Gateway y seguridad JWT con permisos por rol en las tres aplicaciones. Fleet ya implementa la administración de operadores y grúas. Assistance incorpora vehículos propios, solicitudes persistentes y cancelación previa a aceptación. Assistance evalúa cobertura y rutas reales, consulta candidatos de Fleet y persiste su ranking. Fleet reserva recursos exclusivamente; Assistance publica propuestas y confirma la aceptación del operador. Los diagramas vigentes de [contexto C1](docs/diagramas/AuxiRuta_C1.drawio.png), [contenedores C2](docs/diagramas/AuxiRuta_C2.png) y [proceso principal BPMN](docs/diagramas/bpmn.png) están disponibles en `docs/diagramas/`. Los próximos pasos están detallados en el documento de contexto.

## Demostración temporal

El perfil `demo` reduce los plazos de búsqueda, aceptación y despacho para la demostración, conservando los cupos. Los [parámetros de referencia](docs/documentacionPartes/AuxiRuta-parametros-demo.yml) complementan los valores normales. Las pruebas de límites utilizan Clock controlado. El perfil está implementado; sus instrucciones están en [PRUEBAS_JAVA.md](docs/PRUEBAS_JAVA.md).

## Referencias de construcción

Decisiones arquitectónicas actualizadas al 9 de noviembre de 2026, versión 2.7, sin cambiar AR-MVP-5. El [contexto del proyecto](docs/documentacionPartes/AuxiRuta-contexto-proyecto.md#preparación-para-la-construcción--revisión-del-4-de-noviembre-de-2026) detalla el orden de construcción y las verificaciones pendientes del entorno, contratos, persistencia, seguridad y API externa. La entrega de fase 10 incorpora colección ejecutable, contratos, datos reproducibles y guía de verificación.

## Construcción por fases

La [guía de cambios y aprendizaje](docs/cambios.md) registra las fases implementadas, explica el código y contiene los comandos de compilación. Instalar primero auxiruta-commons y luego ejecutar verify en cada aplicación mediante su Maven Wrapper.

## Infraestructura local — fase 2

La [guía de infraestructura](infra/README.md) contiene las instrucciones de Docker Compose, configuración local y pruebas de identidad. Con Docker Desktop iniciado y `.env` preparado, ejecutar `docker compose up -d --build`. Gateway se publica en localhost:8000 y Keycloak en localhost:8080. Los permisos por rol y la administración de Fleet están implementados; los demás procesos se incorporan según el plan de fases.

## Seguridad — fase 3

Gateway y microservicios validan firma, emisor, vigencia y audiencia del JWT. Cada ruta exige su rol y cliente autorizado; ADMIN no reemplaza a DRIVER u OPERATOR. Las rutas internas de Fleet aceptan únicamente credenciales técnicas y no se publican por Gateway.

La prueba `.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.integration=true" "-Dtest=BackendIntegrationTest"` obtiene tokens locales y verifica accesos permitidos/rechazados, enrutamiento y errores. Requiere Docker iniciado y `docker compose up -d --build`. Los documentos OpenAPI JSON se consultan con Bearer en `/docs/fleet/v3/api-docs` y `/docs/assistance/v3/api-docs`; Fleet documenta las operaciones administrativas de la fase 4; Assistance documenta vehículos y solicitudes de fase 5. No se incluye una sesión interactiva de Swagger UI en esta fase.

En instalaciones creadas con la fase 2, ejecutar una vez `.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.repairIdentity=true" "-Dtest=KeycloakScopeSetupTest"` para incorporar el identificador `sub` a los tokens humanos. La corrección es idempotente y conserva los datos; las instalaciones nuevas importan la configuración corregida. Detalles y resultados en [cambios](docs/cambios.md).

## Administración de flota — fase 4

El [plan de implementación](docs/FASES_IMPLEMENTACION.md) conserva la división acordada de las diez fases. Fleet permite a ADMIN preparar operadores, habilitaciones, grúas y vínculos; OPERATOR consulta su perfil/equipo y declara disponibilidad y ubicación. El [registro de cambios](docs/cambios.md) explica entidades, DTOs, SQL, transacciones y verificaciones.

Ejecutar `docker compose up -d --build` para construir y aplicar el esquema aditivo de Fleet. La prueba `.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.integration=true" "-Dtest=BackendIntegrationTest"` verifica la administración en el entorno local y deja recursos identificados como pruebas. Su ejecución utiliza el perfil operator.demo; consultar las condiciones en docs/cambios.md. La especificación autenticada se obtiene en `/docs/fleet/v3/api-docs` por Gateway.

## Vehículos, solicitudes y pruebas Java — fase 5

El proyecto se verifica con Java 21, JUnit y Maven Wrapper. La guía [PRUEBAS_JAVA.md](docs/PRUEBAS_JAVA.md) describe las pruebas aisladas y las de integración real, sus requisitos y los datos que generan.

DRIVER administra vehículos propios, crea solicitudes REQUESTED con snapshot de datos/reglas y consulta su historial. Crear y cancelar requieren Idempotency-Key; la propiedad, duplicados y exclusividad por vehículo se controlan transaccionalmente. ADMIN consulta solicitudes globales. Desde fase 6, la creación ejecuta la evaluación inicial; la fase 7 incorpora reservas, propuestas y aceptación del operador.

## Evaluación y rutas — fase 6

Configurar `ORS_API_KEY` en el `.env` local y ejecutar `docker compose up -d --build`. La clave no se versiona. `POST /api/v1/assistance-requests` persiste la solicitud y luego evalúa cobertura, traslado, compatibilidad, aproximación y vigencia de habilitación. La respuesta y la consulta posterior incluyen `latestEvaluation`, sus motivos y candidatos ordenados. La cobertura se comprueba localmente; las distancias y duraciones viales provienen de OpenRouteService.

Un incumplimiento confirmado de admisión produce REJECTED. Una falla temporal o falta de candidatos deja PENDING_EVALUATION. Desde fase 7, una evaluación VIABLE permite intentar una reserva y pasar a AWAITING_ACCEPTANCE; aceptar y confirmar en Fleet produce ASSIGNED. Las búsquedas y reintentos programados están implementados desde fase 9.

La [guía de pruebas Java](docs/PRUEBAS_JAVA.md) incluye el comando para verificar una evaluación real con Fleet y ORS. El [registro de cambios](docs/cambios.md) explica las clases, tablas, límites y resultados de esta fase.

## Reservas, propuestas y aceptación — fase 7

La evaluación inicial intenta reservar el primer candidato elegible. Fleet garantiza exclusividad de grúa y operador y conserva el resultado de cada intento. Una propuesta válida pasa la solicitud a AWAITING_ACCEPTANCE. OPERATOR consulta `/api/v1/operator/assignments` y responde mediante `/api/v1/assignments/{id}/acceptance` o `/rejection`, con Idempotency-Key; el rechazo requiere motivo de 10 a 500 caracteres.

Solo una confirmación durable en Fleet produce ASSIGNED y fija firstAssignedAt. Si la respuesta de confirmación se pierde, la cancelación devuelve 409 hasta resolver el resultado. Las intenciones pendientes se guardan en PostgreSQL; no se inventa una aceptación ni se libera localmente un recurso remoto desconocido. Las reservas y asignaciones activas bloquean los cambios administrativos que afectarían al servicio.

```powershell
docker compose up -d --build
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.phase7=true" "-Dtest=Phase7IntegrationTest"
```

La suite requiere el entorno local de pruebas y ORS configurado; véanse sus condiciones en [PRUEBAS_JAVA.md](docs/PRUEBAS_JAVA.md). Despacho y finalización están implementados en fase 8. La ejecución periódica de recuperación, expiración y búsqueda está implementada desde fase 9.

## Ejecución y entrega — fase 8

El operador asignado registra, en orden, `dispatch → arrival → loading → transport-start → completion` bajo `/api/v1/assistance-requests/{id}`. Cada POST exige Idempotency-Key. La entrega recibe `{"note":"Vehículo entregado en el destino solicitado"}`. Las acciones devuelven el evento durable del historial y repetir una operación idéntica no duplica la transición.

Despachar revalida el compromiso en Fleet, posición informada sin vencimiento por antigüedad después de aceptar, rutas vigentes y habilitación hasta después del fin planificado. Debe ocurrir antes de 300 segundos desde la aceptación de la asignación actual. El resto del recorrido exige el estado previo correcto; los tiempos de carga y descarga son estimaciones, no esperas obligatorias.

La entrega produce COMPLETED y registra una liberación durable. Si Fleet no responde, resourceReleasePending permanece true y el vehículo no admite otra solicitud. Repetir la entrega con su clave original permite recuperar la liberación. No cambia automáticamente el estado operativo de la grúa.

```powershell
docker compose up -d --build
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.phase8=true" "-Dtest=Phase8IntegrationTest"
```

La suite detiene y reinicia Fleet para verificar un fallo real; ejecutarla únicamente en el entorno local de pruebas y sin otra demostración simultánea. El despacho inválido no registra la salida. Desde fase 9, el vencimiento o la pérdida de elegibilidad del recurso confirmado activa la búsqueda alternativa antes del despacho; las operaciones pendientes se recuperan automáticamente.

## Automatización y recuperación — fase 9

Assistance busca candidatos periódicamente, vence propuestas y búsquedas, respeta cinco ofertas por intento y permite hasta dos reemplazos antes del despacho. Un nuevo intento comienza después de confirmar la liberación anterior. La primera aceptación impide cancelar definitivamente. Desde DISPATCHED, solo las acciones del operador hacen avanzar el recorrido.

Las operaciones remotas pendientes sobreviven a reinicios y se reintentan con espera progresiva. Una confirmación incierta se resuelve antes de cerrar por plazo; una liberación pendiente sigue procesándose incluso si la solicitud ya terminó. Fleet vence sus retenciones HELD y conserva los compromisos CONFIRMED hasta liberarlos.

El perfil normal utiliza los plazos acordados. Para demostraciones, `SPRING_PROFILES_ACTIVE=docker,demo` reduce los plazos sin acelerar el reloj ni las cuotas de OpenRouteService. Véanse los comandos y escenarios en [PRUEBAS_JAVA.md](docs/PRUEBAS_JAVA.md). La entrega de fase 10 está documentada en [ENTREGA.md](docs/ENTREGA.md).
