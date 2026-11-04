# AuxiRuta — Operaciones por rol y alcance del MVP

> Línea base vigente: **AR-MVP-5**, 4 de noviembre de 2026. Referencia consolidada: [Decisiones arquitectónicas](../DECISIONES_ARQUITECTONICAS.md). Diseño pendiente de implementación.

### 12.1 Convenciones

El acceso público utiliza `/api/v1`, representaciones JSON, DTOs validados y ResponseEntity. Los listados tienen paginación y filtros. Los endpoints de consulta no cambian el estado. Las acciones específicas representan transiciones de negocio, sin permitir saltos arbitrarios.

Los identificadores y las fechas de autoridad son del servidor. La API filtra por propietario/asignación salvo permisos globales expresos. Una operación idempotente devuelve su resultado durable y no repite efectos.

### 12.2 Operaciones públicas

Las rutas son relativas a `/api/v1`. A = Assistance; F = Fleet. ADMIN posee consulta global y administración de recursos, sin heredar operaciones personales ni permisos técnicos. Los listados globales y su detalle se filtran según rol.

| Métodos y rutas | Servicio | Actor / comportamiento |
|---|---|---|
| POST /vehicles; GET /vehicles; GET /vehicles/{id}; PATCH /vehicles/{id} | A | DRIVER: crear, consultar y editar vehículos propios; edición de datos identificativos/compatibilidad solo sin solicitud activa ni limpieza pendiente |
| PATCH /vehicles/{id}/registration-status | A | DRIVER: activar/desactivar propios sin compromiso activo |
| POST /assistance-requests | A | DRIVER: crear solicitud propia y disparar evaluación |
| GET /assistance-requests; GET /assistance-requests/{id} | A | DRIVER propias; OPERATOR vinculadas a sus asignaciones; ADMIN consulta global |
| GET /assistance-requests/{id}/history | A | Mismos límites de propiedad/asignación; ADMIN global; filtrar datos personales innecesarios |
| POST /assistance-requests/{id}/cancellations | A | DRIVER propietario: cancelar solo antes de la primera aceptación; 409 si hubo asignación o confirmación incierta |
| GET /operators/me; PATCH /operators/me/availability | F | OPERATOR: perfil propio y disponibilidad sin compromiso activo |
| GET /tow-trucks/assigned-to-me; PUT /tow-trucks/assigned-to-me/location | F | OPERATOR: consultar equipo vinculado y actualizar su ubicación |
| GET /operator/assignments; GET /assignments/{id} | A | OPERATOR: propuestas/servicios propios; ADMIN puede consultar detalle global por ID |
| POST /assignments/{id}/acceptance; POST /assignments/{id}/rejection | A | OPERATOR destinatario: responder propuesta vigente |
| POST /assistance-requests/{id}/dispatch | A | OPERATOR asignado: declarar salida, revalidando condiciones y plazo |
| POST /assistance-requests/{id}/arrival | A | OPERATOR asignado: declarar llegada al retiro vigente |
| POST /assistance-requests/{id}/loading | A | OPERATOR asignado: declarar carga |
| POST /assistance-requests/{id}/transport-start | A | OPERATOR asignado: declarar inicio de traslado |
| POST /assistance-requests/{id}/completion | A | OPERATOR asignado: confirmar entrega/descarga y gestionar liberación |
| GET /operators; GET /operators/{id}; GET /tow-trucks; GET /tow-trucks/{id} | F | ADMIN: consultar catálogo global y capacidades |
| POST /operators; PATCH /operators/{id}; PATCH /operators/{id}/registration-status | F | ADMIN: administrar perfil de subject existente; cambios solo sin compromiso activo |
| PUT /operators/{id}/qualification | F | ADMIN: registrar/renovar habilitación solo sin compromiso activo |
| POST /tow-trucks; PATCH /tow-trucks/{id} | F | ADMIN: crear equipo INACTIVE y administrar datos/capacidades si está libre |
| PUT /tow-trucks/{id}/operator; DELETE /tow-trucks/{id}/operator | F | ADMIN: vincular/desvincular recursos libres |
| PATCH /tow-trucks/{id}/operational-status | F | ADMIN: administrar equipo libre, activar, pasar a mantenimiento o inactivar con motivo; no interrumpir servicios activos |
| GET /configuration/assistance; GET /configuration/fleet | A / F | ADMIN: consultar parámetros efectivos sin secretos; sin edición por API |

No se implementan rutas públicas de reasignación manual, cancelación administrativa, petición de cancelación ni corrección de datos declarados. Tampoco hay edición libre del estado de una solicitud. La gestión de usuarios, contraseñas y roles pertenece a la consola de Keycloak; no se agrega un CRUD propio de identidad.

### 12.3 Contrato interno de Fleet

Estas rutas no utilizan el prefijo público y requieren autorización técnica:

| Método y ruta | Responsabilidad |
|---|---|
| GET /internal/fleet/candidates | Consultar candidatos operativamente compatibles |
| PUT /internal/reservations/{assignmentAttemptId} | Crear/recuperar reserva idempotente de grúa y operador |
| GET /internal/reservations/{assignmentAttemptId} | Consultar resultado, estado y confirmación durable |
| POST /internal/reservations/{assignmentAttemptId}/confirmation | Confirmar antes de vencimiento, revalidando elegibilidad |
| POST /internal/reservations/{assignmentAttemptId}/release | Liberar idempotentemente sin reactivar equipo |
| GET /internal/reservations/{assignmentAttemptId}/eligibility | Revalidar compromiso, operación y habilitación |
| POST /internal/reservations/status-queries | Consultar lista acotada de reservas y recursos actuales; POST transporta filtro compuesto, sin efectos de negocio |

La reserva recibe el deadline de búsqueda, datos necesarios de compatibilidad y fin estimado. La respuesta incluye identificadores, expiresAt y confirmedAt cuando corresponda. El consumidor define DTOs propios y no comparte entidades de Fleet.

### 12.4 Respuestas, errores y documentación

| HTTP | Uso |
|---|---|
| 200 | Consulta/modificación/acción completada o recuperación idempotente |
| 201 | Recurso creado, con Location |
| 202 | Acción registrada durablemente que requiere completar colaboración; incluye referencia de seguimiento |
| 204 | Desvinculación sin contenido |
| 400 | Entrada estructuralmente inválida |
| 401 | Autenticación ausente, inválida o vencida |
| 403 | Rol/permiso insuficiente |
| 404 | Recurso inexistente o ajeno cuando se oculta su existencia |
| 409 | Estado, concurrencia, exclusividad, duplicado o idempotencia incompatibles |
| 500 | Fallo inesperado, con mensaje público controlado |
| 503 | Dependencia indisponible cuando la acción no pudo aceptarse ni registrarse para recuperación |

Los errores usan ProblemDetail y código funcional estable. Un resultado REJECTED/UNFULFILLED de una solicitud creada se consulta con éxito HTTP; el resultado del negocio no es un fallo del protocolo. No se devuelve un error que sugiera ausencia de efectos si ya existe una acción durable pendiente.

Los contratos OpenAPI de ambos servicios deben incluir esquemas, parámetros, roles, ejemplos, respuestas y errores; springdoc publica especificación y Swagger UI. El Gateway enruta documentación por servicio para usuarios autenticados y excluye controladores internos de la especificación pública. Las URLs documentadas deben corresponder al acceso externo real.

## Actores, supuestos y exclusiones

AuxiRuta administra solicitudes inmediatas de traslado de vehículos livianos para una única empresa, todos los días durante las 24 horas. El sistema valida solicitudes, selecciona candidatos, reserva recursos, solicita aceptación al operador y gestiona automáticamente búsquedas, vencimientos y cierres.

| Actor / rol | Responsabilidad y alcance de acceso |
|---|---|
| Conductor — DRIVER | Registrar vehículos propios, crear y consultar solicitudes propias y cancelar únicamente antes de la primera asignación aceptada |
| Operador — OPERATOR | Declarar disponibilidad y ubicación, aceptar/rechazar propuestas, registrar las etapas de sus servicios |
| Administrador — ADMIN | Administrar operadores, habilitaciones, grúas y vínculos; consultar globalmente solicitudes, historial y parámetros |

ADMIN no es un permiso universal: no puede aceptar propuestas, registrar etapas por otro operador, cancelar solicitudes ajenas, elegir una grúa ni forzar estados. Si una persona reúne varios roles, cada operación sigue exigiendo la propiedad o asignación del rol correspondiente. La identidad procede del subject del JWT.

### 2.1 Supuestos y exclusiones

Se asumen correctos los datos declarados del vehículo, accesible el punto de retiro y disponible el destino. Los operadores pueden cumplir su tarea sin imprevistos personales. El alta de operadores es administrada por la empresa: se crea su identidad en Keycloak, se asigna OPERATOR y se registra en Fleet su perfil, habilitación y vínculo con una grúa. No existe registro libre de prestadores independientes. La habilitación y disponibilidad se controlan para admitir cada asignación; el vencimiento durante una ejecución ya despachada no requiere una decisión humana. Se conserva el snapshot utilizado y la exclusividad del recurso hasta liberar.

Se asume continuidad operativa desde el despacho hasta la entrega. Las etapas de ejecución son declaradas por el operador y no requieren evaluación administrativa.

Se excluyen pagos, tarifas, facturación, aseguradoras, reparaciones, emergencias, rescate de personas, sanciones, reputación, servicios futuros, múltiples empresas, paradas intermedias, optimización global, GPS continuo, mapas interactivos, geocodificación, fotografías, firmas y documentación adjunta. No se permite elegir grúa ni modificar origen o destino. El origen y el destino se conservan durante toda la solicitud; una sustitución retira el vehículo en el origen original.

No hay coordinación humana del servicio, peticiones de cancelación, correcciones de datos durante la ejecución. Las notificaciones a usuarios continúan excluidas: el seguimiento se consulta por API. El mantenimiento administrativo y el diagnóstico técnico no constituyen aprobaciones del flujo de asistencia.

## Automatización

Cada reasignación posterior a una aceptación abre automáticamente un nuevo intento de búsqueda de 600 s y hasta cinco propuestas. Se admiten dos reasignaciones, es decir, tres intentos totales contando el inicial. Los rechazos anteriores a aceptar no consumen reasignación. No hay endpoint humano para iniciar una búsqueda alternativa ni seleccionar su candidato.

Antes de despachar se revalidan reserva confirmada, estado operativo, ubicación, rutas y habilitación. El vencimiento de los 300 s para despachar o la pérdida confirmada de elegibilidad antes del despacho genera una búsqueda alternativa, previa liberación. Una falla temporal no extiende el plazo ni habilita el despacho a ciegas.

Si se agotan las reasignaciones, vence la búsqueda o se agotan sus propuestas sin aceptación, la solicitud termina UNFULFILLED con motivo. Una confirmación remota incierta se reconcilia antes de concluir que no existe aceptación válida. La nueva búsqueda comienza al confirmar la liberación anterior; su plazo no se consume durante esa limpieza técnica.

Una vez en DISPATCHED no se reasigna ni se cancela el servicio: el operador registra llegada, carga, traslado y entrega. Se conservan el origen, el destino y los datos declarados.

## Entrega técnica

Dos microservicios funcionales Java/Spring Boot, API Gateway, Keycloak, OpenRouteService, JAR común, persistencia JPA/PostgreSQL con tres bases, REST/OpenAPI, Docker Compose y logging básico. La matriz de evidencias está en el documento consolidado.
