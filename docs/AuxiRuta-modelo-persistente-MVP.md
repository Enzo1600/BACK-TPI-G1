# AuxiRuta — Modelo persistente del MVP revisado según los apuntes

## 1. Qué cambia respecto de la explicación inicial

La explicación anterior de tablas era una alternativa ampliada. Este documento es el modelo propuesto para implementar el MVP: nueve entidades de Assistance, cuatro de Fleet y una tabla auxiliar de tipos admitidos. Keycloak administra su propio esquema.

Se mantienen la trazabilidad de evaluaciones, exclusividad de reservas y recuperación de operaciones incompletas. Se eliminan tablas que no son necesarias para demostrar el proceso:

- RequestVehicleSnapshot: sus datos se guardan como columnas de la solicitud y de cada evaluación.
- OperatorQualification: la habilitación vigente se guarda en Operator; las evaluaciones conservan la utilizada para decidir. Un historial completo de renovaciones es una ampliación.
- TruckOperatorLink: el vínculo actual se guarda en TowTruck; las reservas y asignaciones conservan los recursos que intervinieron. Un historial consultable de vínculos administrativos es una ampliación; el cambio se registra en logs.
- AssistanceConfiguration y FleetConfiguration: parámetros en application.yml/variables de entorno. Los límites usados y vencimientos calculados quedan persistidos donde corresponda.
- PendingAction genérica en ambos servicios: se reemplaza por PendingFleetAction, una acción tipada en Assistance con columnas explícitas. Fleet no consume Assistance.

Esto es una decisión de alcance y modelado, no una exigencia textual de los apuntes. Reducir tablas no justifica quitar información que una regla necesita.

## 2. Forma de implementar persistencia

Cada entidad utiliza jakarta.persistence: @Entity, @Table, @Id y @Column; constructor sin argumentos; enums como @Enumerated(EnumType.STRING). Se conservan UUID como identificadores, ya contemplados en la introducción a JPA. Para simplificar control de identidad, pueden generarse antes de persistir desde Java; se debe definir una única estrategia coherente por entidad.

Repositorios que extienden JpaRepository<Entidad, UUID>, consultas derivadas para casos simples y @Query/JPQL cuando corresponde. Controllers reciben/devuelven DTOs, no entidades.

Las referencias dentro de la base se modelan preferentemente desde N hacia 1 con @ManyToOne(fetch = LAZY) y @JoinColumn. No hace falta agregar listas @OneToMany a todas las entidades; los historiales se consultan por repositorio y paginación.

Los IDs de otro servicio y los subjects de Keycloak son campos simples, sin relaciones JPA ni FKs entre bases. No se usa cascade REMOVE para borrar auditoría. Spring administra EntityManager y transacciones; no se abre una conexión JDBC ni un EntityManager manual por petición.

Tipos: UUID para IDs, BigDecimal para pesos/coordenadas, Instant para instantes y PostgreSQL TIMESTAMPTZ para fechas absolutas. Los ejemplos de columnas siguientes son un esquema lógico; el DDL definitivo debe completar longitudes, nulos, precisión e índices.

@Version y bloqueos JPA son una extensión puntual de concurrencia del proyecto, que debe comprenderse y verificarse, no una técnica desarrollada explícitamente en los apuntes provistos.

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
suspended_from_status VARCHAR NULL
origin_latitude DECIMAL NOT NULL
origin_longitude DECIMAL NOT NULL
destination_latitude DECIMAL NOT NULL
destination_longitude DECIMAL NOT NULL
declared_vehicle_type VARCHAR NOT NULL
declared_weight_kg DECIMAL NOT NULL
mobility_condition VARCHAR NOT NULL
declared_data_revision INTEGER NOT NULL
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

Se copian datos del vehículo al crear la solicitud. La corrección autorizada incrementa declared_data_revision; la evaluación previa y el historial conservan los valores originales. request_fingerprint permite rechazar la misma clave idempotente con un cuerpo distinto.

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

Estados: RESERVATION_PENDING → PROPOSED → ACCEPTED → CLOSED. Una reserva en conflicto puede terminar RESERVATION_FAILED; una propuesta puede terminar REJECTED, EXPIRED o CANCELLED. RESERVATION_PENDING evita perder el rastro si Fleet reserva pero se pierde la respuesta. La solicitud permanece en su etapa de evaluación/búsqueda hasta que la reserva se confirme y pasa entonces a AWAITING_ACCEPTANCE.

### Evaluation → evaluations

Cabecera de cada intento, resultado y snapshot de datos y parámetros.

```text
id UUID PK
request_id UUID FK → assistance_requests NOT NULL
search_attempt_number INTEGER NOT NULL
declared_data_revision INTEGER NOT NULL
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

details puede registrar una corrección con valores anteriores y nuevos aun cuando el estado permanezca igual. La creación registra previous_status NULL. Los logs técnicos no reemplazan estas filas de auditoría funcional.

### Incident → incidents

```text
id UUID PK
request_id UUID FK → assistance_requests NOT NULL
assignment_id UUID FK → assignments NOT NULL
type VARCHAR NOT NULL
description TEXT NOT NULL
blocking BOOLEAN NOT NULL
status VARCHAR NOT NULL
request_status_at_report VARCHAR NOT NULL
reported_by_subject VARCHAR NOT NULL
reported_at TIMESTAMPTZ NOT NULL
resolution_type VARCHAR NULL
resolution_notes TEXT NULL
resolved_by_subject VARCHAR NULL
resolved_at TIMESTAMPTZ NULL
final_vehicle_latitude DECIMAL NULL
final_vehicle_longitude DECIMAL NULL
custody_responsible VARCHAR NULL
version BIGINT NOT NULL
```

Una resolución final por incidencia; no necesita tabla aparte. CLOSED_UNRESOLVED exige constancia de ubicación, custodia y resultado.

### CancellationRequest → cancellation_requests

```text
id UUID PK
request_id UUID FK → assistance_requests NOT NULL
requested_by_subject VARCHAR NOT NULL
reason TEXT NOT NULL
status VARCHAR NOT NULL
requested_at TIMESTAMPTZ NOT NULL
resolved_by_subject VARCHAR NULL
resolution_reason TEXT NULL
resolved_at TIMESTAMPTZ NULL
version BIGINT NOT NULL
```

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
incident_id UUID FK → incidents NULL
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

action_type: RESERVE, CONFIRM, RELEASE o MARK_OUT_OF_SERVICE. Los campos obligatorios dependen de la acción y se validan. Estado PENDING, COMPLETED o NEEDS_ATTENTION; un agotamiento de reintentos no se descarta silenciosamente: permanece visible para diagnóstico y reconciliación. Las dependencias entre acciones se resuelven con reglas explícitas: primero marcar avería y liberar; luego buscar otra grúa. No se confirma una reserva tras cancelar su solicitud.

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

operation_key deduplica la comunicación de avería desde Assistance incluso si una respuesta se pierde. Una repetición no vuelve a crear el cambio ni reabre una avería resuelta después; se devuelve el resultado de esa operación ya registrada.

## 5. Consistencia y restricciones

- @Version detecta actualizaciones concurrentes sobre una misma fila; por sí solo no impide dos INSERT de reservas distintas.
- Reservar requiere una transacción local: bloquear operador y grúa en orden fijo, revalidar datos, comprobar ausencia de reservas activas e insertar. Todos los caminos que reservan/confirman/liberan deben respetar el mismo protocolo.
- La exclusividad puede reforzarse mediante índices únicos parciales PostgreSQL por recurso para HELD/CONFIRMED; es una ampliación de SQL, no una característica portable generada por @UniqueConstraint.
- Para crear una solicitud se bloquea su Vehicle y se verifica ausencia de otra activa dentro de la misma transacción. Puede reforzarse con índice parcial. El patrón consultar y luego guardar sin control no basta.
- Una sola incidencia bloqueante OPEN y una sola petición de cancelación OPEN por solicitud; el servicio bloquea/versiona la solicitud para coordinar esos comandos.
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
CancellationRequest → AssistanceRequest
PendingFleetAction → AssistanceRequest, Assignment, Incident opcional

FLEET
TowTruck → Operator opcional, único
ResourceReservation → TowTruck, Operator
TruckStatusHistory → TowTruck
```

Las flechas representan referencias locales N→1. Las reservas apuntan por ID externo a la solicitud y Assistance apunta por IDs externos a Fleet. Keycloak no tiene FKs hacia ninguna base funcional.
