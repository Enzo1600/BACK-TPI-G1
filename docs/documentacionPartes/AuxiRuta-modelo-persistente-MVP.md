# AuxiRuta — Modelo persistente del MVP

> Línea base vigente: **AR-MVP-4**, 4 de noviembre de 2026. Referencia consolidada: [Decisiones arquitectónicas](../DECISIONES_ARQUITECTONICAS.md). Diseño pendiente de implementación.

## 1. Alcance del modelo

El modelo comprende ocho entidades de Assistance, cuatro de Fleet y una tabla auxiliar de tipos de vehículo admitidos. Keycloak administra su propio esquema. Se conserva trazabilidad de evaluaciones, reservas y recuperación de operaciones. Los snapshots son columnas, la habilitación actual pertenece a Operator, el vínculo actual pertenece a TowTruck y los parámetros se distribuyen mediante configuración.

La cancelación previa a primera aceptación se registra en AssistanceRequest e historial; no tiene entidad de petición o autorización. Incident representa exclusivamente demoras informativas generadas por el sistema. No hay campos de custodia, responsable humano de resolución ni estado suspendido.

## 2. Forma de implementar persistencia

Cada entidad utiliza jakarta.persistence: @Entity, @Table, @Id y @Column; constructor sin argumentos; enums como @Enumerated(EnumType.STRING). Se conservan UUID como identificadores. Para simplificar control de identidad, pueden generarse antes de persistir desde Java; se debe definir una única estrategia coherente por entidad.

Repositorios que extienden JpaRepository<Entidad, UUID>, consultas derivadas para casos simples y @Query/JPQL cuando corresponde. Controllers reciben/devuelven DTOs, no entidades.

Las referencias dentro de la base se modelan preferentemente desde N hacia 1 con @ManyToOne(fetch = LAZY) y @JoinColumn. No hace falta agregar listas @OneToMany a todas las entidades; los historiales se consultan por repositorio y paginación.

Los IDs de otro servicio y los subjects de Keycloak son campos simples, sin relaciones JPA ni FKs entre bases. No se usa cascade REMOVE para borrar auditoría. Spring administra EntityManager y transacciones; no se abre una conexión JDBC ni un EntityManager manual por petición.

Tipos: UUID para IDs, BigDecimal para pesos/coordenadas, Instant para instantes y PostgreSQL TIMESTAMPTZ para fechas absolutas. Los ejemplos de columnas siguientes son un esquema lógico; el DDL definitivo debe completar longitudes, nulos, precisión e índices.

@Version y bloqueos JPA deben comprobarse junto con las restricciones de exclusividad.

## 3. Assistance: assistance_db

### Vehicle → vehicles

Registro del vehículo. No implica acreditación legal de propiedad.

```text
id UUID PK
owner_subject VARCHAR NOT NULL
license_plate VARCHAR UNIQUE NOT NULL
vehicle_type VARCHAR NOT NULL
declared_weight_kg DECIMAL NOT NULL
registration_status VARCHAR NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
version BIGINT NOT NULL
```

### AssistanceRequest → assistance_requests

Estado actual y datos declarados vigentes de la solicitud.

```text
id UUID PK
vehicle_id UUID FK → vehicles NOT NULL
driver_subject VARCHAR NOT NULL
status VARCHAR NOT NULL
first_assigned_at TIMESTAMPTZ NULL
last_vehicle_latitude DECIMAL NOT NULL
last_vehicle_longitude DECIMAL NOT NULL
last_vehicle_location_at TIMESTAMPTZ NOT NULL
execution_started_at TIMESTAMPTZ NULL
closure_reason_code VARCHAR NULL
origin_latitude DECIMAL NOT NULL
origin_longitude DECIMAL NOT NULL
destination_latitude DECIMAL NOT NULL
destination_longitude DECIMAL NOT NULL
declared_vehicle_type VARCHAR NOT NULL
declared_weight_kg DECIMAL NOT NULL
mobility_condition VARCHAR NOT NULL

description TEXT
search_attempt_number INTEGER NOT NULL
search_deadline TIMESTAMPTZ NOT NULL
configuration_revision VARCHAR NOT NULL
idempotency_key VARCHAR NOT NULL
request_fingerprint VARCHAR NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
closed_at TIMESTAMPTZ NULL
version BIGINT NOT NULL
UNIQUE(driver_subject, idempotency_key)
```

Los datos del vehículo se copian al crear y no se corrigen durante el servicio. first_assigned_at se fija con la primera confirmación durable de Fleet y nunca se borra al reasignar. El retiro siempre coincide con el origen original; no existen ubicaciones alternativas. La última ubicación conocida se actualiza con hechos declarados (llegada, carga o entrega), sin GPS continuo. request_fingerprint distingue reintentos de creación incompatibles.

No es necesario guardar resourceReleasePending como otra verdad duplicada: la respuesta puede calcularlo desde las acciones de liberación pendientes.

### Assignment → assignments

Cada propuesta, aceptada o no. Su ID coincide con assignmentAttemptId usado al reservar en Fleet.

```text
id UUID PK
request_id UUID FK → assistance_requests NOT NULL
candidate_evaluation_id UUID FK → candidate_evaluations NOT NULL
fleet_reservation_id UUID NULL
tow_truck_id UUID NOT NULL
operator_id UUID NOT NULL
operator_subject VARCHAR NOT NULL
status VARCHAR NOT NULL
proposed_at TIMESTAMPTZ NOT NULL
expires_at TIMESTAMPTZ NULL
accepted_at TIMESTAMPTZ NULL
ended_at TIMESTAMPTZ NULL
end_reason TEXT NULL
version BIGINT NOT NULL
```

Estados: RESERVATION_PENDING → PROPOSED → ACCEPTED → CLOSED. Una reserva en conflicto puede terminar RESERVATION_FAILED; una propuesta puede terminar REJECTED, EXPIRED o CANCELLED. Una asignación aceptada termina CLOSED al completar el servicio o sustituirse antes del despacho. RESERVATION_PENDING evita perder el rastro si Fleet reserva pero se pierde la respuesta. La solicitud permanece en su etapa de evaluación/búsqueda hasta que la reserva se confirme y pasa entonces a AWAITING_ACCEPTANCE.

### Evaluation → evaluations

Cabecera de cada intento, resultado y snapshot de datos y parámetros.

```text
id UUID PK
request_id UUID FK → assistance_requests NOT NULL
search_attempt_number INTEGER NOT NULL

vehicle_type VARCHAR NOT NULL
declared_weight_kg DECIMAL NOT NULL
mobility_condition VARCHAR NOT NULL
maximum_arrival_seconds INTEGER NOT NULL
loading_seconds INTEGER NOT NULL
unloading_seconds INTEGER NOT NULL
operational_margin_seconds INTEGER NOT NULL
configuration_revision VARCHAR NOT NULL
coverage_and_admission_summary TEXT NOT NULL
result VARCHAR NOT NULL
reason_code VARCHAR NULL
reason_detail TEXT NULL
evaluated_at TIMESTAMPTZ NOT NULL
```

coverage_and_admission_summary conserva límites de cobertura y admisión usados con una representación legible acordada. Si necesitan consultarlos numéricamente, se convierten en columnas individuales; no hace falta un motor de configuración histórica.

### CandidateEvaluation → candidate_evaluations

Una fila por recurso evaluado.

```text
id UUID PK
evaluation_id UUID FK → evaluations NOT NULL
tow_truck_id UUID NOT NULL
operator_id UUID NOT NULL
operator_subject VARCHAR NOT NULL
truck_capacity_kg DECIMAL NOT NULL
qualification_category VARCHAR NOT NULL
qualification_valid_until TIMESTAMPTZ NOT NULL
truck_latitude DECIMAL NOT NULL
truck_longitude DECIMAL NOT NULL
truck_location_updated_at TIMESTAMPTZ NOT NULL
compatible BOOLEAN NOT NULL
eligible BOOLEAN NOT NULL
reason_code VARCHAR NULL
approach_distance_m DECIMAL NULL
approach_duration_seconds INTEGER NULL
transport_distance_m DECIMAL NULL
transport_duration_seconds INTEGER NULL
estimated_service_end_at TIMESTAMPTZ NULL
ranking_position INTEGER NULL
```

No se almacena todo el JSON de OpenRouteService. Si falló la consulta, las duraciones pueden ser NULL y el motivo debe diferenciar un error temporal de la ausencia real de ruta.

### RequestStatusHistory → request_status_history

```text
id UUID PK
request_id UUID FK → assistance_requests NOT NULL
previous_status VARCHAR NULL
new_status VARCHAR NOT NULL
actor_subject VARCHAR NULL
actor_type VARCHAR NOT NULL
reason TEXT NULL
details TEXT NULL
correlation_id VARCHAR NULL
occurred_at TIMESTAMPTZ NOT NULL
```

details conserva aperturas de búsqueda y plazos; las acciones automáticas utilizan actor_type SYSTEM y actor_subject NULL. La creación registra previous_status NULL. Los logs técnicos no reemplazan la auditoría funcional.

### Incident → incidents

Registro informativo de demora; no es un reporte operativo ni una decisión pendiente.

```text
id UUID PK
request_id UUID FK → assistance_requests NOT NULL
assignment_id UUID FK → assignments NOT NULL
type VARCHAR NOT NULL
stage VARCHAR NOT NULL
status VARCHAR NOT NULL
opened_at TIMESTAMPTZ NOT NULL
resolved_at TIMESTAMPTZ NULL
generated_key VARCHAR UNIQUE NOT NULL
version BIGINT NOT NULL
```

Tipos DELAYED_ARRIVAL, LOADING_DELAY y TRANSPORT_DELAY. Estados OPEN/RESOLVED. SYSTEM genera y cierra los registros. generated_key deduplica asignación/tipo. No existe endpoint para crearlos o resolverlos manualmente.

### PendingFleetAction → pending_fleet_actions

Recuperación concreta de operaciones sobre Fleet. No es una cola distribuida ni un motor genérico.

```text
id UUID PK
operation_key VARCHAR UNIQUE NOT NULL
request_id UUID FK → assistance_requests NOT NULL
assignment_id UUID FK → assignments NOT NULL
action_type VARCHAR NOT NULL
tow_truck_id UUID NOT NULL
operator_id UUID NULL
estimated_service_end_at TIMESTAMPTZ NULL
reason TEXT NULL
status VARCHAR NOT NULL
attempt_count INTEGER NOT NULL
next_attempt_at TIMESTAMPTZ NOT NULL
last_error TEXT NULL
correlation_id VARCHAR NULL
created_at TIMESTAMPTZ NOT NULL
completed_at TIMESTAMPTZ NULL
version BIGINT NOT NULL
```

action_type: RESERVE, CONFIRM o RELEASE. Los campos obligatorios dependen de la acción y se validan. Estado PENDING, COMPLETED o RETRYING; los efectos inciertos se reconcilian automáticamente sin descartarse ni exigir aprobación humana. Una nueva reserva requiere liberación confirmada del compromiso anterior. No se confirma una reserva tras cancelar su solicitud.

Se guarda la intención en una transacción local, se llama a Fleet fuera de ella y se confirma el resultado en otra transacción. La tarea consulta resultados ante respuestas perdidas. Un reinicio no puede perder una reserva creada después de cancelarse: antes de cerrar la recuperación se consulta/libera el intento idempotente correspondiente.

## 4. Fleet: fleet_db

### Operator → operators

```text
id UUID PK
keycloak_subject VARCHAR UNIQUE NOT NULL
full_name VARCHAR NOT NULL
contact_phone VARCHAR
registration_status VARCHAR NOT NULL
availability_status VARCHAR NOT NULL
qualification_category VARCHAR NULL
qualification_valid_from TIMESTAMPTZ NULL
qualification_valid_until TIMESTAMPTZ NULL
available_since TIMESTAMPTZ NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
version BIGINT NOT NULL
```

Habilitación vigente en la misma fila. Sin habilitación completa y válida no es candidato. El endpoint PUT /operators/{id}/qualification actualiza estos campos con registro técnico del cambio; las evaluaciones ya realizadas no se reescriben.

### TowTruck → tow_trucks

```text
id UUID PK
license_plate VARCHAR UNIQUE NOT NULL
operator_id UUID FK → operators UNIQUE NULL
maximum_load_kg DECIMAL NOT NULL
supports_non_rollable BOOLEAN NOT NULL
operational_status VARCHAR NOT NULL
latitude DECIMAL NULL
longitude DECIMAL NULL
location_updated_at TIMESTAMPTZ NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
version BIGINT NOT NULL
```

Un operador actual por grúa y una grúa por operador. El vínculo es @ManyToOne LAZY con unicidad en operator_id, evitando navegación bidireccional. UNIQUE admite múltiples filas sin operador en PostgreSQL.

```text
tow_truck_supported_types
------------------------
tow_truck_id UUID FK → tow_trucks
vehicle_type VARCHAR
PK(tow_truck_id, vehicle_type)
```

Se implementa como colección de enums con @ElementCollection, una ampliación pequeña del mapeo visto. Si prefieren evitarla, una entidad de compatibilidad con ID propio y @ManyToOne a TowTruck permite usar únicamente el patrón N→1 enseñado.

### ResourceReservation → resource_reservations

```text
id UUID PK
assignment_attempt_id UUID UNIQUE NOT NULL
assistance_request_id UUID NOT NULL
tow_truck_id UUID FK → tow_trucks NOT NULL
operator_id UUID FK → operators NOT NULL
status VARCHAR NOT NULL
held_at TIMESTAMPTZ NOT NULL
expires_at TIMESTAMPTZ NOT NULL
estimated_service_end_at TIMESTAMPTZ NOT NULL
confirmed_at TIMESTAMPTZ NULL
released_at TIMESTAMPTZ NULL
release_reason TEXT NULL
request_fingerprint VARCHAR NOT NULL
version BIGINT NOT NULL
```

Fleet recibe la duración/fin estimados y revalida habilitación al reservar, confirmar y despachar. La ocupación se deriva de reservas HELD/CONFIRMED: no hay ninguna → FREE; HELD → HELD; CONFIRMED → ASSIGNED. Al liberar no se cambia el estado operativo.

La consulta de estado devuelve también la situación actual del recurso; no es necesario duplicar DISPATCHED o IN_TRANSIT en Fleet.

### TruckStatusHistory → truck_status_history

```text
id UUID PK
tow_truck_id UUID FK → tow_trucks NOT NULL
previous_status VARCHAR NOT NULL
new_status VARCHAR NOT NULL
reason TEXT NOT NULL
changed_by_subject VARCHAR NOT NULL
operation_key VARCHAR UNIQUE NULL
correlation_id VARCHAR NULL
changed_at TIMESTAMPTZ NOT NULL
```

operation_key deduplica cambios administrativos de estado operativo. Ningún cambio se aplica sobre un equipo con reserva activa.

## 5. Consistencia y restricciones

- @Version detecta actualizaciones concurrentes sobre una misma fila; por sí solo no impide dos INSERT de reservas distintas.
- Reservar requiere una transacción local: bloquear operador y grúa en orden fijo, revalidar datos, comprobar ausencia de reservas activas e insertar. Todos los caminos que reservan/confirman/liberan deben respetar el mismo protocolo.
- La exclusividad puede reforzarse mediante índices únicos parciales PostgreSQL por recurso para HELD/CONFIRMED; no es una característica portable generada por @UniqueConstraint.
- Para crear una solicitud se bloquea su Vehicle y se verifica ausencia de otra activa dentro de la misma transacción. Puede reforzarse con índice parcial. El patrón consultar y luego guardar sin control no basta.
- Un registro informativo por asignación/tipo; bloqueo e idempotencia evitan duplicados. Cancelación y primera aceptación comparten control transaccional; una confirmación incierta bloquea cancelar hasta reconciliar.
- CHECK para pesos/capacidades positivos, coordenadas válidas y fechas coherentes; NOT NULL, FK, UNIQUE y longitudes explícitos.
- Índices de consulta: solicitud por driver/status/fecha, asignación por operator/status, historial por request/fecha, reserva por status/expires_at, acciones pendientes por status/next_attempt_at.
- Las transacciones no mantienen bloqueos mientras se espera una respuesta HTTP. @Transactional coordina una base local; no hace rollback de una llamada remota que ya tuvo éxito.

Los métodos transaccionales se invocan a través de beans de Spring; evitar depender de autoinvocaciones que eludan el proxy. La política de rollback debe contemplar las excepciones utilizadas. Un resultado optimista en memoria no equivale a un commit exitoso.

## 6. Configuración y despliegue de la base

Una instancia PostgreSQL, bases y usuarios separados para Assistance, Fleet y Keycloak. Volumen persistente y scripts de inicialización. H2 queda para prácticas y pruebas aisladas; la demostración de concurrencia se verifica sobre PostgreSQL.

Para entrega: scripts SQL controlados y ddl-auto=validate. create-drop solo en entornos descartables. Flyway/Liquibase son opciones posteriores, no dependencias nuevas necesarias. Los scripts de inicialización de la imagen PostgreSQL no reemplazan un proceso de actualización de una base ya poblada.

Cada servicio tiene su DataSource/pool administrado por Spring. No se comparte Connection en singleton. El JAR común no contiene entidades ni repositorios de negocio.

## 7. Relaciones de referencia

```text
ASSISTANCE
AssistanceRequest → Vehicle
Assignment → AssistanceRequest, CandidateEvaluation
Evaluation → AssistanceRequest
CandidateEvaluation → Evaluation
RequestStatusHistory → AssistanceRequest
Incident → AssistanceRequest, Assignment
PendingFleetAction → AssistanceRequest, Assignment

FLEET
TowTruck → Operator opcional, único
ResourceReservation → TowTruck, Operator
TruckStatusHistory → TowTruck
```

Las flechas representan referencias locales N→1. Las reservas apuntan por ID externo a la solicitud y Assistance apunta por IDs externos a Fleet. Keycloak no tiene FKs hacia ninguna base funcional.

## 8. Ajustes necesarios por las reglas fijas AR-MVP-4

- Vehicle incorpora brand/model y restricciones de tipo, patente y peso.
- AssistanceRequest conserva first_assigned_at, origen original como retiro, última ubicación y cierre. El origen/destino y snapshot de vehículo son inmutables. search_attempt_number admite 1..3; cada apertura registra plazo en historial. Se conserva snapshot de parámetros, no solo su versión.
- Assignment persiste antes del HTTP; accepted_at procede de confirmed_at durable. Incorporar reserved_at, dispatched_at, arrived_at, loaded_at, transport_started_at, completed_at y planned_arrival_at. Cada sustitución conserva las fechas del intento previo. La primera aceptación se copia a la solicitud una sola vez.
- Evaluation conserva coordenadas del retiro vigente y destino, reglas, ruta y route_obtained_at. CandidateEvaluation conserva valores crudos/ajustados y fecha de ubicación. El resultado de recuperación distingue ruta imposible de dependencia temporal.
- ResourceReservation incluye search_deadline_at y hold_seconds_requested; expires_at se calcula en Fleet con el menor plazo. La confirmación durable no se convierte en vencida por recuperar tarde su respuesta.
- Incident solo conserva demora, etapa, fechas y clave de deduplicación; no contiene reportante humano, coordenadas de eventos ni resolución manual.
- Historial incluye operation_key única en su ámbito y la etapa/ubicación al cerrar UNFULFILLED después de una ejecución iniciada.
- PendingFleetAction mantiene explícitos los datos de compatibilidad, ruta, plazo y operación. Se confirma RELEASE antes de reservar otro recurso; la nueva búsqueda comienza después de esa limpieza.
- Un estado terminal no elimina tareas ni habilita nueva solicitud sobre el vehículo mientras existan efectos inciertos. La liberación no modifica el estado operativo de la grúa.

No se crean tablas para peticiones de cancelación, suspensiones, custodia, decisiones humanas ni roles. El DDL definitivo debe concretar nulos, longitudes, checks e índices de estas reglas antes de implementarse.
