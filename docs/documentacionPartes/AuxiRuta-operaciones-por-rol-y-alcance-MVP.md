# AuxiRuta — Operaciones por rol y alcance del MVP

> Línea base vigente: **AR-MVP-3**, 4 de noviembre de 2026. Referencia consolidada: [Decisiones arquitectónicas](../DECISIONES_ARQUITECTONICAS.md). Diseño pendiente de implementación.

## Permisos

DRIVER opera exclusivamente sobre sus vehículos y solicitudes. OPERATOR responde propuestas y declara hechos de sus propias asignaciones. ADMIN administra recursos y consulta globalmente; no hereda operaciones del conductor u operador. Las tareas automáticas utilizan identidad técnica y no el rol ADMIN.

El acceso público utiliza `/api/v1`, representaciones JSON, DTOs validados y ResponseEntity. Los listados tienen paginación y filtros. Los endpoints de consulta no cambian el estado. Las acciones específicas representan transiciones de negocio, sin permitir saltos arbitrarios.

Los identificadores y las fechas de autoridad son del servidor. La API filtra por propietario/asignación salvo permisos globales expresos. Una operación idempotente devuelve su resultado durable y no repite efectos.

## Endpoints públicos y comportamiento

Las rutas son relativas a `/api/v1`. A = Assistance; F = Fleet. ADMIN posee consulta global y administración de recursos, sin heredar operaciones personales ni permisos técnicos. Los listados globales y su detalle se filtran según rol.

| Métodos y rutas | Servicio | Actor / comportamiento |
|---|---|---|
| POST /vehicles; GET /vehicles; GET /vehicles/{id}; PATCH /vehicles/{id} | A | DRIVER: crear, consultar y editar vehículos propios; edición de datos identificativos/compatibilidad solo sin solicitud activa ni limpieza pendiente |
| PATCH /vehicles/{id}/registration-status | A | DRIVER: activar/desactivar propios sin compromiso activo |
| POST /assistance-requests | A | DRIVER: crear solicitud propia y disparar evaluación |
| GET /assistance-requests; GET /assistance-requests/{id} | A | DRIVER propias; OPERATOR vinculadas a sus asignaciones; ADMIN consulta global |
| GET /assistance-requests/{id}/history; GET /assistance-requests/{id}/incidents | A | Mismos límites de propiedad/asignación; ADMIN global; filtrar datos personales innecesarios |
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
| POST /assistance-requests/{id}/incidents | A | OPERATOR de asignación ACCEPTED: comunicar TOW_TRUCK_BREAKDOWN solo en ASSIGNED, DISPATCHED, ARRIVED o VEHICLE_LOADED; exige descripción/ubicación y dispara sustitución automática. En IN_TRANSIT devuelve 409 sin efectos |
| GET /incidents; GET /incidents/{id} | A | ADMIN: consultar incidencias y resultados automáticos |
| GET /operators; GET /operators/{id}; GET /tow-trucks; GET /tow-trucks/{id} | F | ADMIN: consultar catálogo global y capacidades |
| POST /operators; PATCH /operators/{id}; PATCH /operators/{id}/registration-status | F | ADMIN: administrar perfil de subject existente; cambios solo sin compromiso activo |
| PUT /operators/{id}/qualification | F | ADMIN: registrar/renovar habilitación solo sin compromiso activo |
| POST /tow-trucks; PATCH /tow-trucks/{id} | F | ADMIN: crear equipo INACTIVE y administrar datos/capacidades si está libre |
| PUT /tow-trucks/{id}/operator; DELETE /tow-trucks/{id}/operator | F | ADMIN: vincular/desvincular recursos libres |
| PATCH /tow-trucks/{id}/operational-status | F | ADMIN: administrar equipo libre, incluido reactivar OUT_OF_SERVICE con motivo; no interrumpir servicios activos |
| GET /configuration/assistance; GET /configuration/fleet | A / F | ADMIN: consultar parámetros efectivos sin secretos; sin edición por API |

No se implementan rutas públicas de resolución de incidencias, reasignación manual, cancelación administrativa, petición de cancelación ni corrección de datos declarados. Tampoco hay edición libre del estado de una solicitud. La gestión de usuarios, contraseñas y roles pertenece a la consola de Keycloak; no se agrega un CRUD propio de identidad.

## Colaboración técnica

Estas rutas no utilizan el prefijo público y requieren autorización técnica:

| Método y ruta | Responsabilidad |
|---|---|
| GET /internal/fleet/candidates | Consultar candidatos operativamente compatibles |
| PUT /internal/reservations/{assignmentAttemptId} | Crear/recuperar reserva idempotente de grúa y operador |
| GET /internal/reservations/{assignmentAttemptId} | Consultar resultado, estado y confirmación durable |
| POST /internal/reservations/{assignmentAttemptId}/confirmation | Confirmar antes de vencimiento, revalidando elegibilidad |
| POST /internal/reservations/{assignmentAttemptId}/release | Liberar idempotentemente sin reactivar equipo |
| GET /internal/reservations/{assignmentAttemptId}/eligibility | Revalidar compromiso, operación y habilitación |
| POST /internal/tow-trucks/{id}/out-of-service | Registrar avería idempotente vinculada a incidencia |
| POST /internal/reservations/status-queries | Consultar lista acotada de reservas y recursos actuales; POST transporta filtro compuesto, sin efectos de negocio |

La reserva recibe el deadline de búsqueda, datos necesarios de compatibilidad y fin estimado. La respuesta incluye identificadores, expiresAt y confirmedAt cuando corresponda. El consumidor define DTOs propios y no comparte entidades de Fleet.

## Respuestas y documentación

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

## Funciones automáticas incluidas

- Validación de cobertura, vehículos, habilitaciones, recorridos y exclusividad.
- Selección y ordenamiento de candidatos, retención y generación de propuestas.
- Vencimiento/rechazo de propuestas, reintentos de evaluación y búsquedas alternativas.
- Reasignación por falta de despacho y por avería, solo antes de iniciar IN_TRANSIT.
- Cambio a OUT_OF_SERVICE y liberación controlada del equipo averiado.
- Cierre UNFULFILLED al agotar límites o confirmar recuperación inviable.
- Registro informativo y cierre automático de demoras.
- Historial persistente, idempotencia y reconciliación de efectos remotos inciertos.

Aceptar una propuesta, declarar etapas y comunicar una avería siguen siendo acciones del operador: son hechos de la prestación, no evaluaciones administrativas. Crear una solicitud y cancelar antes de aceptar son acciones del conductor.

## Alcance técnico comprometido

Dos microservicios Java/Spring Boot con datos independientes y colaboración real; Gateway; Keycloak; API externa de rutas; JAR común; JPA/PostgreSQL con tres bases; REST/OpenAPI; Docker Compose; logging básico. La matriz de requisitos y evidencias figura en el documento consolidado. No se afirma que estén implementados.

## Fuera del MVP

Se asumen correctos los datos declarados del vehículo, accesible el punto de retiro y disponible el destino. Los operadores pueden cumplir su tarea sin imprevistos personales. La habilitación y disponibilidad se controlan para admitir cada asignación; el vencimiento durante una ejecución ya despachada no abre una incidencia ni requiere una decisión humana. Se conserva el snapshot utilizado y la exclusividad del recurso hasta liberar.

El único impedimento operativo excepcional modelado es la avería de una grúa. La avería solo se admite en ASSIGNED, DISPATCHED, ARRIVED o VEHICLE_LOADED. Desde IN_TRANSIT se asume que la grúa completa el traslado sin averías; no se admite reportarlas ni reasignar por ese motivo. La transferencia física entre equipos, la custodia, el rescate especial y sus procedimientos reales quedan fuera del modelo evaluativo.

Se excluyen pagos, tarifas, facturación, aseguradoras, reparaciones, emergencias, rescate de personas, sanciones, reputación, servicios futuros, múltiples empresas, paradas intermedias, optimización global, GPS continuo, mapas interactivos, geocodificación, fotografías, firmas y documentación adjunta. No se permite elegir grúa ni modificar origen o destino. El origen y el destino se conservan durante toda la solicitud; una sustitución retira el vehículo en el origen original.

No hay coordinación humana del servicio, peticiones de cancelación, correcciones de datos durante la ejecución ni resoluciones manuales de incidencias. Las notificaciones a usuarios continúan excluidas: el seguimiento se consulta por API. El mantenimiento administrativo y el diagnóstico técnico no constituyen aprobaciones del flujo de asistencia.

No se incorporan endpoints administrativos para reintentar, aprobar o descartar efectos técnicos inciertos. Su recuperación es automática; las correcciones de infraestructura/configuración son mantenimiento de desarrollo y operación.
