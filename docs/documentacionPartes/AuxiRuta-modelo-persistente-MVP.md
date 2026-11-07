# AuxiRuta — Modelo persistente del MVP

> Línea base vigente: **AR-MVP-5**, 4 de noviembre de 2026. Referencia consolidada: [Decisiones arquitectónicas](../DECISIONES_ARQUITECTONICAS.md). Diseño pendiente de implementación.

Estado de construcción: la fase 4 implementa Operator, TowTruck, TruckStatusHistory y tow_truck_supported_types en Fleet. ResourceReservation corresponde a la fase 7. La fase 5 implementa Vehicle, AssistanceRequest y RequestStatusHistory en Assistance. Las demás entidades se incorporan con evaluación, asignaciones y recuperación. El detalle de ejecución y pruebas se registra en [cambios](../cambios.md).

## 1. Alcance del modelo

El modelo comprende siete entidades de Assistance, cuatro de Fleet y una tabla auxiliar de tipos de vehículo admitidos. Keycloak administra su propio esquema. Se conserva trazabilidad de evaluaciones, reservas y recuperación de operaciones. Los datos evaluados se conservan en columnas y los parámetros efectivos en un snapshot serializado, la habilitación actual pertenece a Operator, el vínculo actual pertenece a TowTruck y los parámetros se distribuyen mediante configuración.

La cancelación previa a primera aceptación se registra en AssistanceRequest e historial; no tiene entidad de petición o autorización. No hay campos de custodia, responsable humano de resolución ni estado suspendido.

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
brand VARCHAR NULL
model VARCHAR NULL
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
closure_reason_code VARCHAR NULL
origin_latitude DECIMAL NOT NULL
origin_longitude DECIMAL NOT NULL
destination_latitude DECIMAL NOT NULL
destination_longitude DECIMAL NOT NULL
declared_vehicle_type VARCHAR NOT NULL
declared_weight_kg DECIMAL NOT NULL
mobility_condition VARCHAR NOT NULL

description VARCHAR(1000) NOT NULL
search_attempt_number INTEGER NOT NULL
search_deadline TIMESTAMPTZ NOT NULL
configuration_revision VARCHAR NOT NULL
configuration_snapshot TEXT NOT NULL
idempotency_key VARCHAR NOT NULL
request_fingerprint VARCHAR NOT NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
closed_at TIMESTAMPTZ NULL
version BIGINT NOT NULL
UNIQUE(driver_subject, idempotency_key)
```

Los datos del vehículo se copian al crear y no se corrigen durante el servicio. first_assigned_at se fija con la primera confirmación durable de Fleet y nunca se borra al reasignar. El retiro siempre coincide con el origen original; no existen ubicaciones alternativas. request_fingerprint distingue reintentos de creación incompatibles.

configuration_snapshot conserva los parámetros efectivos como JSON serializado en una columna TEXT, sin requerir consultas JSON ni una entidad adicional. La revisión sola no basta para reconstruir una decisión. El snapshot es inmutable y se usa también después de reiniciar o cambiar la configuración. Las evaluaciones referencian esta solicitud y conservan los datos concretos de cada cálculo.

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
created_at TIMESTAMPTZ NOT NULL
reserved_at TIMESTAMPTZ NULL
proposed_at TIMESTAMPTZ NULL
expires_at TIMESTAMPTZ NULL
accepted_at TIMESTAMPTZ NULL
dispatched_at TIMESTAMPTZ NULL
arrived_at TIMESTAMPTZ NULL
loaded_at TIMESTAMPTZ NULL
transport_started_at TIMESTAMPTZ NULL
completed_at TIMESTAMPTZ NULL
ended_at TIMESTAMPTZ NULL
end_reason TEXT NULL
version BIGINT NOT NULL
```

created_at se registra antes de llamar a Fleet; reserved_at y proposed_at solo se completan cuando existe reserva y se publica la propuesta. accepted_at copia confirmed_at de Fleet; first_assigned_at de la solicitud se fija una sola vez. Las fechas de ejecución pertenecen a la asignación que las produjo.

Estados: RESERVATION_PENDING → PROPOSED → ACCEPTED → CLOSED. Una reserva en conflicto puede terminar RESERVATION_FAILED; una propuesta puede terminar REJECTED, EXPIRED o CANCELLED. Una asignación aceptada termina CLOSED al completar el servicio o sustituirse antes del despacho. RESERVATION_PENDING evita perder el rastro si Fleet reserva pero se pierde la respuesta. La solicitud permanece en su etapa de evaluación/búsqueda hasta que la reserva se confirme y pasa entonces a AWAITING_ACCEPTANCE.

### Evaluation → evaluations

Cabecera de cada intento, resultado y snapshot de datos y parámetros.

```text
id UUID PK
request_id UUID FK → assistance_requests NOT NULL
search_attempt_number INTEGER NOT NULL
origin_latitude DECIMAL NOT NULL
origin_longitude DECIMAL NOT NULL
destination_latitude DECIMAL NOT NULL
destination_longitude DECIMAL NOT NULL
transport_distance_m DECIMAL NULL
transport_duration_seconds DECIMAL NULL
adjusted_transport_seconds INTEGER NULL
route_obtained_at TIMESTAMPTZ NULL

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

coverage_and_admission_summary registra el resultado legible de admisión. Los valores normativos se recuperan del snapshot inmutable de la solicitud. route_obtained_at y la ruta de traslado pueden ser NULL mientras no haya respuesta válida; una evaluación pendiente no inventa valores. Cada ronda puede generar otra Evaluation dentro del mismo search_attempt_number.

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
approach_duration_seconds DECIMAL NULL
adjusted_approach_seconds INTEGER NULL
approach_route_obtained_at TIMESTAMPTZ NULL
transport_distance_m DECIMAL NULL
transport_duration_seconds DECIMAL NULL
adjusted_transport_seconds INTEGER NULL
transport_route_obtained_at TIMESTAMPTZ NULL
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
operation_key VARCHAR NULL
request_fingerprint VARCHAR NULL
correlation_id VARCHAR NULL
occurred_at TIMESTAMPTZ NOT NULL
UNIQUE(request_id, operation_key)
```

operation_key identifica transiciones idempotentes dentro de cada solicitud; request_fingerprint permite rechazar la reutilización de una clave con otro contenido. Los eventos automáticos pueden carecer de clave. No se registra un cierre UNFULFILLED después de DISPATCHED. details conserva aperturas de búsqueda y plazos; las acciones automáticas utilizan actor_type SYSTEM y actor_subject NULL. La creación registra previous_status NULL. Los logs técnicos no reemplazan la auditoría funcional.

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
vehicle_type VARCHAR NULL
declared_weight_kg DECIMAL NULL
mobility_condition VARCHAR NULL
search_deadline_at TIMESTAMPTZ NULL
hold_seconds_requested INTEGER NULL
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

action_type: RESERVE, CONFIRM o RELEASE. RESERVE exige recursos, compatibilidad, plazo y fin estimado; CONFIRM exige el fin estimado revalidado; RELEASE identifica el intento y motivo. Los datos de cada acción son inmutables y suficientes para reconstruir su DTO de integración después de un reinicio. Los campos obligatorios dependen de la acción y se validan. Estado PENDING, COMPLETED o RETRYING; los efectos inciertos se reconcilian automáticamente sin descartarse ni exigir aprobación humana. Una nueva reserva requiere liberación confirmada del compromiso anterior. No se confirma una reserva tras cancelar su solicitud.

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

Se implementa como colección de enums con @ElementCollection y @CollectionTable. No tiene identidad ni ciclo de vida independientes de TowTruck; la clave compuesta impide tipos duplicados.

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
search_deadline_at TIMESTAMPTZ NOT NULL
hold_seconds_requested INTEGER NOT NULL
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
- Cancelación y primera aceptación comparten control transaccional; una confirmación incierta bloquea cancelar hasta reconciliar.
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
PendingFleetAction → AssistanceRequest, Assignment

FLEET
TowTruck → Operator opcional, único
ResourceReservation → TowTruck, Operator
TruckStatusHistory → TowTruck
```

Las flechas representan referencias locales N→1. Las reservas apuntan por ID externo a la solicitud y Assistance apunta por IDs externos a Fleet. Keycloak no tiene FKs hacia ninguna base funcional.

## 8. Diagrama y condiciones de implementación

El [DER editable](../diagramas/AuxiRuta_DER.md) representa estas entidades, atributos y cardinalidades. Las imágenes bd1.png y bd2.png se conservan como antecedentes; no sustituyen esta definición consolidada.

- search_attempt_number admite 1..3. El historial conserva cada apertura y su plazo.
- El vencimiento de una reserva se calcula en Fleet con el menor plazo entre retención y búsqueda. Una confirmación durable válida no pasa a vencida por recuperarse tarde su respuesta.
- Las acciones pendientes sobreviven al cierre. Ninguna nueva reserva se inicia antes de confirmar la liberación anterior; la siguiente búsqueda comienza después de esa limpieza.
- No se crean tablas para peticiones de cancelación, suspensiones, custodia, decisiones humanas ni roles.
- Antes de implementar cada entidad se concreta su DDL: longitudes, precisión decimal, nulos, checks e índices. El esquema debe coincidir con el mapeo y verificarse con ddl-auto=validate.
