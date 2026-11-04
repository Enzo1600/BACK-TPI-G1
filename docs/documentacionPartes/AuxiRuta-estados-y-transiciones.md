# AuxiRuta — Estados y transiciones del MVP

> Línea base vigente: **AR-MVP-3**, 4 de noviembre de 2026. Referencia consolidada: [Decisiones arquitectónicas](../DECISIONES_ARQUITECTONICAS.md). Diseño pendiente de implementación.

### 6.1 Solicitud

| Estado | Significado | Destinos permitidos |
|---|---|---|
| REQUESTED | Solicitud registrada | PENDING_EVALUATION, AWAITING_ACCEPTANCE, REJECTED, UNFULFILLED, CANCELLED |
| PENDING_EVALUATION | Información externa pendiente | AWAITING_ACCEPTANCE, REJECTED, UNFULFILLED, CANCELLED |
| AWAITING_ACCEPTANCE | Propuesta vigente, esperando al operador | ASSIGNED, SEARCHING_ALTERNATIVE, UNFULFILLED, CANCELLED |
| ASSIGNED | Aceptación confirmada en Fleet | DISPATCHED, SEARCHING_ALTERNATIVE, UNFULFILLED |
| SEARCHING_ALTERNATIVE | Búsqueda automática por rechazo, vencimiento, falta de despacho o avería | PENDING_EVALUATION, AWAITING_ACCEPTANCE, UNFULFILLED, CANCELLED |
| DISPATCHED | Grúa en camino al punto de retiro vigente | ARRIVED, SEARCHING_ALTERNATIVE, UNFULFILLED |
| ARRIVED | Grúa en el punto de retiro | VEHICLE_LOADED, SEARCHING_ALTERNATIVE, UNFULFILLED |
| VEHICLE_LOADED | Vehículo cargado en la grúa actual | IN_TRANSIT, SEARCHING_ALTERNATIVE, UNFULFILLED |
| IN_TRANSIT | Traslado al destino; sin averías en el MVP | COMPLETED |
| COMPLETED | Entrega y descarga confirmadas por el operador | Terminal |
| REJECTED | Solicitud inicial fuera de las reglas de admisión | Terminal |
| UNFULFILLED | Búsqueda o sustitución sin resultado dentro de límites | Terminal |
| CANCELLED | Conductor canceló antes de la primera aceptación | Terminal |

CANCELLED solo es alcanzable si nunca existió una asignación aceptada, incluso en una búsqueda alternativa. REJECTED solo corresponde a la admisión inicial; una recuperación inviable produce UNFULFILLED. Una falla temporal no equivale a inexistencia de ruta.

Cada transición registra estado anterior/nuevo, instante, actor y motivo. Las automáticas usan actor SYSTEM. Un estado terminal no libera por sí mismo reservas remotas pendientes. No hay endpoint genérico de modificación de estado.

### 6.2 Vehículo, grúa, operador y reserva

| Concepto | Estados / valores | Semántica |
|---|---|---|
| Registro de vehículo | ACTIVE, INACTIVE | Admisión de nuevas solicitudes |
| Movilidad declarada | ROLLABLE, NON_ROLLABLE | Compatibilidad de carga |
| Situación logística | Esperando, cargado, en traslado o esperando reemplazo | Derivada de la solicitud y última asignación; no editable |
| Registro de operador | ACTIVE, INACTIVE | Administración del perfil |
| Disponibilidad | AVAILABLE, UNAVAILABLE | Declaración modificable solo sin compromiso activo |
| Estado operativo de grúa | ACTIVE, MAINTENANCE, OUT_OF_SERVICE, INACTIVE | Independiente de ocupación |
| Ocupación | FREE, HELD, ASSIGNED | Derivada de reservas activas |
| Reserva Fleet | HELD, CONFIRMED, RELEASED, EXPIRED | Retención y compromiso exclusivo |
| Propuesta/asignación | RESERVATION_PENDING, RESERVATION_FAILED, PROPOSED, ACCEPTED, REJECTED, EXPIRED, CANCELLED, CLOSED | Historial de cada intento |
| Incidencia | PROCESSING, RESOLVED | Tratamiento automático; sin responsable humano de resolución |

Fleet aplica HELD → CONFIRMED/RELEASED/EXPIRED y CONFIRMED → RELEASED. Las confirmadas no vencen automáticamente. Una avería deja la asignación CANCELLED con motivo BREAKDOWN; el término no significa que la solicitud haya sido cancelada por el conductor. ACCEPTED pasa a CLOSED en una finalización normal o sustitución por falta de despacho. El equipo averiado queda OUT_OF_SERVICE aunque su ocupación vuelva a FREE.

### 6.3 Incidencias automáticas

| Tipo | Entrada / detección | Tratamiento |
|---|---|---|
| TOW_TRUCK_BREAKDOWN | Operador de asignación aceptada; solo ASSIGNED, DISPATCHED, ARRIVED o VEHICLE_LOADED; descripción y ubicación | Cancelar asignación, marcar equipo inoperativo, liberar y buscar reemplazo o cerrar UNFULFILLED |
| DELAYED_ARRIVAL | Job al superar tolerancia de llegada | Registro informativo; cierre al alcanzar llegada o terminar asignación |
| LOADING_DELAY | Job al superar tolerancia de carga | Registro informativo; cierre al cargar o terminar asignación |
| TRANSPORT_DELAY | Job al superar tolerancia de traslado | Registro informativo; cierre al entregar o terminar asignación |

La avería permanece PROCESSING mientras se coordinan sus efectos remotos. Pasa a RESOLVED al confirmar el cambio operativo, la liberación y el inicio de búsqueda o cierre automático; no espera a que termine todo el servicio. Se guardan resultado, fecha y actor SYSTEM. No hay endpoint de resolución. La deduplicación por asignación/tipo y operación evita descontar dos reasignaciones por el mismo evento.

## Ejemplo: avería antes de iniciar el traslado

1. La solicitud está ARRIVED con la asignación A1 ACCEPTED; el traslado aún no comenzó.
2. El operador comunica avería y coordenadas actuales. A1 pasa a CANCELLED por BREAKDOWN; la solicitud pasa a SEARCHING_ALTERNATIVE.
3. Se confirma OUT_OF_SERVICE y liberación en Fleet. El sistema inicia el siguiente intento desde el origen original.
4. Otra propuesta A2 es aceptada. La solicitud vuelve a ASSIGNED y el nuevo operador registra sus etapas desde el origen hasta el destino original.
5. Si A2 completa la entrega, la solicitud termina COMPLETED. Si no hay reemplazo dentro de los límites, termina UNFULFILLED con motivo y última ubicación, sin resolución manual.

Durante toda la recuperación el conductor conserva bloqueada la cancelación porque firstAssignedAt ya existe. Los reintentos idempotentes no duplican averías ni consumen otro cupo.

## Exclusión de averías durante el traslado

Desde IN_TRANSIT solo se admite COMPLETED en el flujo funcional. Un reporte de avería devuelve 409 BREAKDOWN_NOT_ALLOWED_IN_TRANSIT sin efectos. Si el reporte compite con transport-start, ambas operaciones bloquean/versionan la misma solicitud: gana una sola transición válida; no puede empezar un traslado y abrirse simultáneamente su sustitución. La entrega sigue siendo declarada por el operador; no se completa por un temporizador.
