# AuxiRuta — Operaciones por rol y alcance del MVP

## 1. Convenciones y responsabilidad

Este documento y el de estados precisan la propuesta inicial de AuxiRuta y constituyen la referencia funcional del MVP. Los endpoints son el contrato propuesto para implementar, no una API ya construida.

Todos los endpoints de negocio se exponen a través del Gateway bajo `/api/v1`. Las tablas muestran rutas relativas a ese prefijo. A = assistance-service; F = fleet-service. Las rutas internas de Fleet no se publican en el Gateway.

Keycloak autentica y entrega tokens OIDC/JWT. Los usuarios no envían su identidad como un dato confiable del cuerpo: se obtiene del token. Para el MVP cada operador tiene una grúa asignada administrativamente; una grúa no tiene dos operadores asignados al mismo tiempo. DRIVER puede coexistir con otros roles si se asigna expresamente.

Los listados tienen paginación y filtros. Las acciones reciben motivo cuando corresponde y las transiciones utilizan control de concurrencia. Se aceptan claves de idempotencia en creación de solicitudes y acciones que puedan repetirse por fallos de red.

Revisión según los apuntes: microservicios MVC con Controller → Service → JpaRepository, DTOs, RestClient y transacciones locales cortas. El Gateway sigue la variante WebFlux mostrada en clase, con SecurityWebFilterChain; los microservicios usan SecurityFilterChain. El modelo vigente está en `AuxiRuta-modelo-persistente-MVP.md`.

## 2. DRIVER — Conductor

| Operación | Método y endpoint | Servicio | Comportamiento y restricciones |
|---|---|---|---|
| Registrar vehículo | POST /vehicles | A | Crea vehículo ACTIVE del usuario autenticado. Recibe patente, tipo y peso declarado positivo; controla duplicados de patente normalizada |
| Listar sus vehículos | GET /vehicles | A | Devuelve exclusivamente sus vehículos; permite filtrar por estado |
| Consultar vehículo | GET /vehicles/{id} | A | Devuelve detalle si pertenece al usuario |
| Editar vehículo | PATCH /vehicles/{id} | A | Actualiza datos permitidos; impide cambiar características de compatibilidad mientras tenga solicitud activa |
| Activar o desactivar vehículo | PATCH /vehicles/{id}/registration-status | A | Cambia ACTIVE/INACTIVE; desactivar exige ausencia de solicitud activa |
| Solicitar asistencia | POST /assistance-requests | A | Recibe vehicleId, coordenadas de origen/destino, condición ROLLABLE/NON_ROLLABLE y descripción. Verifica propiedad, vehículo activo y ausencia de otra solicitud activa. Crea REQUESTED y dispara evaluación |
| Listar sus solicitudes | GET /assistance-requests | A | Filtra automáticamente por propietario; admite estado y fechas |
| Consultar solicitud | GET /assistance-requests/{id} | A | Muestra estado, resumen de evaluación, asignación vigente y liberación pendiente si corresponde |
| Consultar historial | GET /assistance-requests/{id}/history | A | Muestra transiciones y motivos de su solicitud |
| Consultar incidencias de su solicitud | GET /assistance-requests/{id}/incidents | A | Devuelve información operativa y resolución; excluye notas internas y datos sensibles de terceros |
| Cancelar antes del despacho | POST /assistance-requests/{id}/cancellations | A | Requiere motivo y estado anterior a DISPATCHED. Cancela y gestiona liberación si existe reserva |
| Pedir cancelación tras el despacho | POST /assistance-requests/{id}/cancellation-requests | A | Permitido en DISPATCHED o ARRIVED, o suspensión previa a la carga. Crea petición OPEN para el coordinador; no cancela ni detiene automáticamente el servicio |

El conductor no elige la grúa, no acepta propuestas del operador y no cambia estados operativos. La entrega la confirma el operador; una doble confirmación del conductor queda fuera del MVP.

## 3. OPERATOR — Operador de grúa

| Operación | Método y endpoint | Servicio | Comportamiento y restricciones |
|---|---|---|---|
| Consultar su perfil operativo | GET /operators/me | F | Devuelve habilitación, disponibilidad y grúa asignada |
| Declarar disponibilidad | PATCH /operators/me/availability | F | Cambia AVAILABLE/UNAVAILABLE. Pasar a UNAVAILABLE exige no tener reserva ni asignación; con un problema activo debe informar incidencia |
| Consultar su grúa | GET /tow-trucks/assigned-to-me | F | Muestra capacidades, estado operativo y ocupación |
| Actualizar ubicación | PUT /tow-trucks/assigned-to-me/location | F | Guarda coordenadas y hora de recepción; no permite actualizar una grúa ajena. Es una actualización manual, sin GPS continuo |
| Consultar propuestas y servicios | GET /operator/assignments | A | Lista exclusivamente asignaciones propias, pendientes y anteriores |
| Consultar asignación | GET /assignments/{id} | A | Devuelve datos necesarios para realizar su servicio; exige pertenencia |
| Aceptar propuesta | POST /assignments/{id}/acceptance | A | Confirma reserva vigente con Fleet; solo después pasa la solicitud a ASSIGNED |
| Rechazar propuesta | POST /assignments/{id}/rejection | A | Solo antes de aceptar; registra motivo, libera reserva y busca otra alternativa |
| Registrar salida | POST /assistance-requests/{id}/dispatch | A | Desde ASSIGNED; verifica asignación y elegibilidad actual con Fleet antes de DISPATCHED |
| Registrar llegada | POST /assistance-requests/{id}/arrival | A | DISPATCHED → ARRIVED |
| Registrar carga | POST /assistance-requests/{id}/loading | A | ARRIVED → VEHICLE_LOADED; confirma que vehículo y condiciones coinciden con lo autorizado |
| Iniciar traslado | POST /assistance-requests/{id}/transport-start | A | VEHICLE_LOADED → IN_TRANSIT |
| Confirmar entrega | POST /assistance-requests/{id}/completion | A | IN_TRANSIT → COMPLETED; registra entrega y descarga en el destino acordado e inicia liberación de recursos |
| Informar incidencia | POST /assistance-requests/{id}/incidents | A | Recibe tipo, descripción y carácter bloqueante/informativo. Una bloqueante durante ejecución suspende. Una avería comunica OUT_OF_SERVICE a Fleet |

El operador no reasigna, no resuelve incidencias y no reactiva una grúa averiada. Antes del despacho puede informar un impedimento, que queda para coordinación; la asignación no se abandona mediante un cambio unilateral de disponibilidad.

## 4. COORDINATOR — Coordinador

Supervisa solicitudes y resuelve excepciones. No crea usuarios, no administra capacidades ni omite restricciones para forzar una asignación.

| Operación | Método y endpoint | Servicio | Comportamiento y restricciones |
|---|---|---|---|
| Listar todas las solicitudes | GET /assistance-requests | A | Visibilidad global con filtros por estado, fecha y operador |
| Consultar solicitud e historial | GET /assistance-requests/{id}; GET /assistance-requests/{id}/history | A | Acceso global a información operativa y decisiones |
| Consultar incidencias | GET /incidents; GET /incidents/{id} | A | Lista casos por estado, tipo y solicitud; muestra resolución e historial |
| Informar incidencia | POST /assistance-requests/{id}/incidents | A | Registra un problema informado a la empresa; una bloqueante durante ejecución suspende |
| Consultar flota y operadores | GET /tow-trucks; GET /tow-trucks/{id}; GET /operators; GET /operators/{id} | F | Consulta capacidades, disponibilidad, habilitación y ocupación |
| Marcar grúa fuera de servicio | POST /tow-trucks/{id}/out-of-service | F | Requiere motivo. Fleet registra el cambio; Assistance lo detecta al consultar reservas activas. Sin llamada Fleet → Assistance ni actualización instantánea garantizada |
| Consultar peticiones de cancelación | GET /cancellation-requests | A | Filtra OPEN/APPROVED/REJECTED y solicitud |
| Resolver petición de cancelación | POST /cancellation-requests/{id}/resolution | A | Acepta o rechaza con motivo. Aprobar valida que siga siendo cancelable; si ya está cargado se rechaza la petición y se trata como incidencia |
| Cancelar servicio | POST /assistance-requests/{id}/cancellations | A | Antes del despacho, cancela con motivo. Después, exige suspensión previa a carga y constancia de aviso al operador. Si cumple, cierra peticiones OPEN como APPROVED |
| Solicitar reasignación | POST /assistance-requests/{id}/reassignments | A | Desde ASSIGNED, o SUSPENDED antes de carga. Registra motivo, cierra asignación anterior y solo después busca otra. Si existe incidencia bloqueante, se utiliza su resolución REASSIGN |
| Corregir datos declarados para reevaluar | PATCH /assistance-requests/{id}/declared-vehicle-data | A | Solo SUSPENDED antes de carga por discrepancia. Permite peso, tipo y condición, con motivo y versión histórica. No cambia propietario, origen ni destino. Exige reevaluación antes de una nueva asignación |
| Resolver incidencia | POST /incidents/{id}/resolution | A | CONTINUE, REASSIGN, CANCEL o CLOSE_UNRESOLVED según condiciones del documento de estados; nunca permite saltar directamente a COMPLETED |

Al aprobar una petición de cancelación después del despacho, Assistance registra la suspensión y la cancelación como parte del mismo comando de negocio, con constancia de aviso. Si la petición deja de ser aplicable por una finalización u otra cancelación, queda REJECTED con motivo automático; no permanece abierta indefinidamente.

Las correcciones no reescriben datos históricos. CONTINUE después de una corrección exige verificar nuevamente compatibilidad con el recurso actual; REASSIGN evalúa la nueva versión al buscar candidatos.

## 5. ADMIN — Administración

ADMIN dispone de todas las operaciones de coordinación. Las operaciones personales de conductor y operador siguen exigiendo pertenencia o asignación; el administrador no puede suplantar al operador para registrar un traslado.

| Operación | Método y endpoint | Servicio | Comportamiento y restricciones |
|---|---|---|---|
| Registrar operador | POST /operators | F | Vincula un usuario existente de Keycloak mediante su subject; crea perfil operativo. No crea credenciales |
| Editar operador | PATCH /operators/{id} | F | Modifica datos operativos y contacto; no modifica su identidad de Keycloak |
| Activar/desactivar operador | PATCH /operators/{id}/registration-status | F | ACTIVE/INACTIVE; desactivar exige ausencia de reservas/asignaciones. Ante un impedimento durante servicio se utiliza incidencia |
| Registrar o renovar habilitación | PUT /operators/{id}/qualification | F | Guarda categoría y vencimiento declarado. Validación documental manual; sin integración con organismos |
| Registrar grúa | POST /tow-trucks | F | Crea equipo inicialmente INACTIVE con patente única, capacidad y compatibilidades |
| Editar grúa | PATCH /tow-trucks/{id} | F | Modifica capacidades y tipos admitidos solo sin reserva/asignación activa |
| Asignar operador a grúa | PUT /tow-trucks/{id}/operator | F | Exige ambos recursos libres y sin otro vínculo incompatible; reemplazo auditado |
| Desvincular operador | DELETE /tow-trucks/{id}/operator | F | Solo sin reserva o asignación; elimina vínculo actual, registra el cambio y conserva el vínculo utilizado en asignaciones anteriores |
| Cambiar estado operativo | PATCH /tow-trucks/{id}/operational-status | F | ACTIVE, MAINTENANCE o INACTIVE con motivo y reglas del documento de estados. OUT_OF_SERVICE utiliza la acción específica |
| Consultar parámetros | GET /configuration/assistance | A | Muestra región, plazo de búsqueda, máximo de llegada, margen y límites efectivos; excluye secretos |
| Consultar parámetros de flota | GET /configuration/fleet | F | Muestra antigüedad máxima de ubicación y duración de reserva; excluye secretos |

Los parámetros se modifican mediante application.yml/variables de entorno y reinicio, no mediante PATCH ni tablas de configuración. Las solicitudes conservan sus plazos y las evaluaciones sus límites usados; las reservas mantienen su vencimiento calculado. Cambiar la configuración no reescribe esos datos.

La región del MVP es un rectángulo geográfico configurable con límites de latitud/longitud. No incluye edición cartográfica de polígonos. Los tipos de vehículo y compatibilidades son un catálogo cerrado del MVP; el administrador configura capacidades, no crea reglas mediante programación dinámica.

La creación de usuarios, asignación de roles y recuperación de contraseñas se realizan en Keycloak por su administrador, con consola propia. Tener ADMIN en AuxiRuta no concede automáticamente administración de Keycloak.

## 6. Operaciones internas y automáticas

No corresponden a usuarios humanos ni se exponen públicamente. Usan client_credentials con permisos limitados.

| Operación | Endpoint o mecanismo | Responsable | Comportamiento |
|---|---|---|---|
| Buscar candidatos | GET /internal/fleet/candidates | A → F | Filtra por peso, tipo, condición, habilitación, disponibilidad y ubicación reciente |
| Reservar recursos | PUT /internal/reservations/{assignmentAttemptId} | A → F | Reserva grúa y operador en una transacción; devuelve vencimiento. Reintento con mismos datos retorna el resultado existente; distintos datos con mismo ID producen conflicto |
| Consultar reserva | GET /internal/reservations/{assignmentAttemptId} | A → F | Recupera resultado cuando se perdió una respuesta |
| Confirmar reserva | POST /internal/reservations/{assignmentAttemptId}/confirmation | A → F | HELD vigente → CONFIRMED; excluye aceptación posterior al vencimiento |
| Liberar reserva/asignación | POST /internal/reservations/{assignmentAttemptId}/release | A → F | Libera de manera idempotente; no reactiva una grúa fuera de servicio |
| Verificar elegibilidad antes de despacho/continuación | GET /internal/reservations/{assignmentAttemptId}/eligibility | A → F | Comprueba reserva confirmada, recurso operativo y habilitación vigente |
| Registrar avería | POST /internal/tow-trucks/{id}/out-of-service | A → F | Marca avería con referencia a incidencia y clave idempotente |
| Consultar recursos de reservas activas | POST /internal/reservations/status-queries | A → F | Lista acotada de IDs de intentos; devuelve estado de reserva, grúa y operador. Consulta sin efectos, POST para transportar filtro compuesto. Sin llamada inversa |
| Evaluar ruta | Cliente HTTP de OpenRouteService | A → API externa | Obtiene duración y distancia de aproximación y traslado; valida respuesta y límites de uso |
| Reintentar evaluación | Tarea programada | A | Reevalúa pendientes con backoff y plazo máximo; sin aprobación a ciegas |
| Vencer reservas | Tarea programada | F | HELD → EXPIRED; libera recursos atómicamente |
| Recuperar efectos pendientes | Tarea programada | A | Reintenta acciones tipadas hacia Fleet guardadas en su base; Fleet conserva resultados idempotentes en reservas |
| Reconciliar | Tarea programada | A | Consulta a Fleet, detecta vencimientos, cambios operativos y liberaciones pendientes; corrige con comandos idempotentes |

Las rutas internas usan su prefijo propio, sin `/api/v1`, y solo son accesibles en la red interna con autorización técnica. No se implementa un endpoint público de “forzar estado”.

Si un recurso deja de ser operativo, Assistance lo detecta por sondeo o revalidación. En DISPATCHED, ARRIVED, VEHICLE_LOADED o IN_TRANSIT crea una incidencia bloqueante y suspende. En ASSIGNED o AWAITING_ACCEPTANCE registra el impedimento y busca alternativa después de liberar la asignación/reserva. Las solicitudes terminales no se reabren.

Los clientes usan RestClient. Los reintentos no se aplican indiscriminadamente a errores 4xx ni a escrituras sin identificación idempotente. Resilience4j es opcional; el MVP incluye timeouts, manejo de errores y reintentos acotados. Las tareas programadas y la reconciliación son una extensión del proyecto, no una exigencia literal del material.

## 7. Respuestas y errores

| Código | Uso |
|---|---|
| 200 | Consulta, modificación o acción completada; repetir una acción idempotente devuelve su resultado existente |
| 201 | Creación de vehículo, solicitud, incidencia o petición; incluye Location |
| 202 | Acción registrada que necesita completar colaboración, por ejemplo reasignación o liberación pendiente; devuelve identificador y forma de consultar progreso |
| 204 | Desvinculación completada sin cuerpo |
| 400 | JSON inválido o datos de entrada incorrectos |
| 401 | Token ausente, inválido o vencido |
| 403 | Rol sin permiso para la operación |
| 404 | Recurso inexistente o ajeno cuando se oculta su existencia al usuario |
| 409 | Estado incompatible, versión concurrente, reserva vencida, recurso ocupado o duplicado |
| 500 | Error inesperado; mensaje externo controlado y causa técnica registrada en logs |
| 503 | Dependencia temporalmente indisponible en una acción que no pudo aceptarse ni registrarse para reintento |

Errores con ProblemDetail y código funcional estable. Un resultado REJECTED o UNFULFILLED de una solicitud ya creada se consulta con 200: el rechazo del negocio no es un error del protocolo HTTP.

Si una acción se registró durablemente para reintento, se responde 202 en lugar de afirmar que falló sin efectos. No se devuelve 503 si eso induciría a crear otra operación mientras la primera sigue en curso.

## 8. Alcance completo incluido en el MVP

### 8.1 Contexto y actores

- Una empresa de asistencia, una región de cobertura y los cuatro roles descritos.
- Solicitudes de atención inmediata; cada una traslada un vehículo desde un origen hasta un destino fijo.
- Un operador por grúa y una asignación activa por recurso.
- Vehículos livianos definidos en un catálogo cerrado, con peso y condición declarados.
- Coordenadas ingresadas manualmente y ubicaciones actualizadas por el operador.

### 8.2 Proceso principal

1. El conductor registra un vehículo activo y solicita un traslado.
2. El sistema valida propiedad, cobertura, admisión y exclusividad de solicitud activa.
3. Fleet filtra recursos compatibles, habilitados, disponibles y con ubicación vigente.
4. Assistance consulta rutas. Verifica llegada dentro del máximo y vigencia de habilitación durante el período estimado: aproximación + carga + traslado + descarga + margen. Los tiempos operativos son configurables.
5. Ordena por llegada estimada y desempata por tiempo libre; un empate restante se resuelve por identificador estable.
6. Reserva un candidato y ofrece la asignación al operador.
7. El operador acepta o rechaza; si vence o rechaza se prueba otra alternativa dentro del plazo.
8. Tras aceptar se confirma con Fleet y se permite el despacho validado.
9. El operador registra llegada, carga, inicio de traslado y entrega/descarga.
10. Se cierra la solicitud, se liberan recursos y se conserva el historial.

La ruta al destino participa en la evaluación de duración total y vigencia de habilitación, además de la aproximación usada para ordenar candidatos. No es información decorativa.

### 8.3 Excepciones incluidas

- Sin recursos, recurso incompatible, fuera de cobertura y habilitación insuficiente.
- Rechazo y vencimiento de propuesta, aceptación concurrente con vencimiento y competencia entre solicitudes.
- Fallas temporales de Fleet o API externa, datos externos inválidos y ausencia de ruta.
- Cancelación antes del despacho y solicitud de cancelación posterior antes de la carga.
- Incidencias informativas o bloqueantes, suspensión y continuación validada.
- Reasignación antes de cargar, iniciada por coordinación y seleccionada por el sistema.
- Corrección auditada de peso, tipo o condición antes de cargar y nueva evaluación obligatoria.
- Después de cargar: suspensión, continuación con el mismo recurso o cierre excepcional documentado. No hay transbordo automático.
- Recuperación tras reinicios mediante acciones pendientes persistidas, idempotencia y reconciliación.

### 8.4 Seguridad, datos y entrega técnica

- Dos microservicios Java/Spring Boot con responsabilidades separadas y colaboración REST real.
- Spring Cloud Gateway con Security Filter, Keycloak, OIDC, JWT, Spring Security y permisos por rol/propietario/asignación.
- Credenciales técnicas separadas para las integraciones internas necesarias.
- PostgreSQL con bases y usuarios separados por servicio y Keycloak, sin consultas cruzadas.
- JAR Maven común real, instalado en .m2 y consumido por ambos microservicios: seguridad reutilizable, errores y correlación; sin entidades de negocio compartidas.
- OpenAPI/Swagger, validación, errores consistentes y registros de auditoría funcional.
- Docker Compose reproducible, realm/roles/clientes y bases inicializados; datos de demostración y secretos externos al repositorio.
- Diagramas C4 C1 y C2 que representen lo implementado y README con puesta en marcha y recorrido de demostración.
- Uso de Swagger o cliente HTTP para operar el backend. No se requiere interfaz web propia.
- Spring Data JPA/Hibernate con JpaRepository, consultas derivadas/JPQL, DTOs y ResponseEntity. Transacciones cortas, sin llamadas HTTP dentro de bloqueos prolongados.
- Auditoría funcional de estados, responsables y decisiones en la base. Los logs técnicos de diagnóstico no se declaran como una funcionalidad adicional del MVP.
- Monitoreo con Actuator, métricas, correlación y rotación avanzada de logs quedan fuera del compromiso del MVP; su presencia en los apuntes no los convierte en requisitos del TPI.
- Colección de pruebas funcionales de los escenarios comprometidos. Tests unitarios y stacks externos de logs no se declaran requisitos obligatorios de la cátedra.

### 8.5 Ampliación opcional: operaciones técnicas de administración (fuera del MVP)

Las siguientes rutas son una alternativa para una etapa posterior; no se compromete su implementación ni forman parte de las operaciones mínimas de ADMIN. Si se incorporan, quedan fuera del prefijo /api/v1 y el Gateway debe permitirlas expresamente a ADMIN, conservando la autorización hasta el servicio. No se publica todo Actuator con un comodín.

| Método y ruta pública | Destino interno | Comportamiento |
|---|---|---|
| GET /ops/assistance/health; GET /ops/fleet/health | /actuator/health del servicio | Estado general sin detalles sensibles |
| GET /ops/assistance/info; GET /ops/fleet/info | /actuator/info del servicio | Información seleccionada de aplicación |
| GET /ops/assistance/metrics; GET /ops/fleet/metrics | /actuator/metrics del servicio | Lista de métricas |
| GET /ops/assistance/metrics/{name}; GET /ops/fleet/metrics/{name} | /actuator/metrics/{name} del servicio | Consulta de métrica autorizada |

Si se incorpora Actuator, el health del Gateway y los healthchecks deben tener una política técnica explícita, sin publicar detalles sensibles. Esto no afecta el requisito de documentar las APIs: Swagger/OpenAPI sí se enruta expresamente por servicio para usuarios autenticados, sin publicar rutas internas. La especificación pública excluye los controladores internos.

## 9. Fuera del MVP

| Funcionalidad excluida | Límite concreto |
|---|---|
| Frontend y aplicaciones móviles | No hay panel visual propio ni aplicación para conductor/operador |
| GPS continuo y seguimiento en vivo | No hay streaming de ubicación, WebSockets ni mapa en tiempo real |
| Tránsito real y ETA garantizada | Se usan estimaciones del proveedor, sin garantía de llegada |
| Certificación de transitabilidad | La API no certifica accesos, altura, peso permitido o seguridad de circulación de una grúa |
| Pagos, tarifas y facturación | No se calcula ni cobra un precio |
| Aseguradoras y pólizas | No hay autorización de cobertura ni reintegros |
| Emergencias y reparaciones | No hay atención médica, rescate de personas, diagnóstico mecánico ni reparación |
| Múltiples empresas | Sin marketplace, subcontratación ni aislamiento multitenant |
| Servicios futuros o recurrentes | Sin agenda, turnos futuros ni reservas periódicas |
| Traslados múltiples | Un vehículo, un origen y un destino por solicitud; sin paradas intermedias |
| Optimización de flota | Sin rutas globales, agrupación de servicios ni algoritmo de optimización multivehículo |
| Reasignación después de la carga | Sin coordinación automática de segunda grúa, transbordo ni cadena de custodia entre operadores |
| Elección manual de grúa | El coordinador inicia la búsqueda; no fuerza un candidato incompatible ni elige uno arbitrariamente |
| Cambio de origen/destino | Después de crear la solicitud se cancela y crea otra si todavía es posible; no hay desvíos en curso |
| Control mecánico del vehículo | No se certifica aptitud ni se infiere reparación después de entregar |
| Validación oficial de habilitaciones | Datos ingresados y verificados manualmente por administración |
| Fotos, firmas y documentos adjuntos | Recepción y custodia se registran mediante datos y constancias de texto, sin firma digital |
| Correos, SMS y notificaciones push | Las propuestas e incidencias se consultan mediante API |
| Geocodificación y mapas | Se ingresan coordenadas; no se convierten direcciones ni se ofrece buscador cartográfico |
| Broker y arquitectura de eventos | Sin Kafka/RabbitMQ; coordinación REST con trabajos persistidos y reintentos |
| Despliegue productivo de alta disponibilidad | Sin Kubernetes, réplicas múltiples ni operación comercial; tareas programadas diseñadas para una instancia por servicio |
| Eliminación del historial | No hay borrado físico de operaciones históricas ni edición retroactiva de auditoría |
| Edición dinámica de parámetros | Archivos y variables de entorno; sin PATCH ni tablas versionadas |
| Monitoreo y observabilidad avanzada | Actuator, métricas, correlación, rotación y stacks ELK/Loki/Grafana/Prometheus son opcionales; no se comprometen como funcionalidades del MVP |
| Motor genérico de eventos o tareas | Acciones tipadas hacia Fleet; sin bus ni payloads JSON arbitrarios |

## 10. Evidencias mínimas para demostrar el compromiso

| Escenario | Resultado verificable |
|---|---|
| Traslado normal | Solicitud COMPLETED y recursos liberados |
| Dos solicitudes compiten | Solo una reserva la misma grúa/operador; la otra busca alternativa o queda UNFULFILLED |
| Operador rechaza o no responde | Reserva liberada/vencida y siguiente candidato evaluado |
| Vehículo incompatible o fuera de cobertura | Motivo auditable y ausencia de asignación inválida |
| API externa o Fleet temporalmente caído | PENDING_EVALUATION, reintento y resultado coherente al recuperar o vencer |
| Respuesta de reserva perdida | Consulta/reintento recupera una única reserva |
| Cancelación | Estado y liberación coherentes, incluso si Fleet tarda en responder |
| Avería antes de cargar | Suspensión, grúa fuera de servicio y reasignación autorizada |
| Incidencia después de cargar | Sin reasignación automática; continuación válida o cierre documentado |
| Acceso ajeno o rol incorrecto | Operación denegada, sin cambios |
| Reinicio con acción pendiente | Recuperación sin duplicar operación ni perder la liberación |

## 11. Referencia externa

OpenRouteService: [servicios](https://openrouteservice.org/services/) y [autenticación y ejemplos](https://openrouteservice.org/dev/). La implementación debe configurar el endpoint vigente del proveedor, proteger la clave y manejar cuotas, timeouts y respuestas inválidas.
