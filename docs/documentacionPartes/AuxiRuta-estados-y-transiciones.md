# AuxiRuta — Estados y transiciones del MVP

> Línea base vigente: **AR-MVP-5**, 4 de noviembre de 2026. Referencia consolidada: [Decisiones arquitectónicas](../DECISIONES_ARQUITECTONICAS.md). Diseño pendiente de implementación.

### 6.1 Solicitud

| Estado | Significado | Destinos permitidos |
|---|---|---|
| REQUESTED | Solicitud registrada | PENDING_EVALUATION, AWAITING_ACCEPTANCE, REJECTED, UNFULFILLED, CANCELLED |
| PENDING_EVALUATION | Información externa pendiente | AWAITING_ACCEPTANCE, REJECTED, UNFULFILLED, CANCELLED |
| AWAITING_ACCEPTANCE | Propuesta vigente, esperando al operador | ASSIGNED, SEARCHING_ALTERNATIVE, UNFULFILLED, CANCELLED |
| ASSIGNED | Aceptación confirmada en Fleet | DISPATCHED, SEARCHING_ALTERNATIVE, UNFULFILLED |
| SEARCHING_ALTERNATIVE | Búsqueda automática por rechazo, vencimiento o falta de despacho | PENDING_EVALUATION, AWAITING_ACCEPTANCE, UNFULFILLED, CANCELLED |
| DISPATCHED | Grúa en camino al origen | ARRIVED |
| ARRIVED | Grúa en el origen | VEHICLE_LOADED |
| VEHICLE_LOADED | Vehículo cargado | IN_TRANSIT |
| IN_TRANSIT | Traslado al destino | COMPLETED |
| COMPLETED | Entrega y descarga confirmadas por el operador | Terminal |
| REJECTED | Solicitud inicial fuera de las reglas de admisión | Terminal |
| UNFULFILLED | Búsqueda o sustitución sin resultado dentro de límites | Terminal |
| CANCELLED | Conductor canceló antes de la primera aceptación | Terminal |

CANCELLED solo es alcanzable si nunca existió una asignación aceptada, incluso en una búsqueda alternativa. REJECTED solo corresponde a la admisión inicial; una búsqueda alternativa agotada antes de despacho produce UNFULFILLED. Una falla temporal no equivale a inexistencia de ruta.

Cada transición registra estado anterior/nuevo, instante, actor y motivo. Las automáticas usan actor SYSTEM. Un estado terminal no libera por sí mismo reservas remotas pendientes. No hay endpoint genérico de modificación de estado.

### 6.2 Vehículo, grúa, operador y reserva

| Concepto | Estados / valores | Semántica |
|---|---|---|
| Registro de vehículo | ACTIVE, INACTIVE | Admisión de nuevas solicitudes |
| Movilidad declarada | ROLLABLE, NON_ROLLABLE | Compatibilidad de carga |
| Situación logística | Esperando, cargado, en traslado o esperando reemplazo | Derivada de la solicitud y última asignación; no editable |
| Registro de operador | ACTIVE, INACTIVE | Administración del perfil |
| Disponibilidad | AVAILABLE, UNAVAILABLE | Declaración modificable solo sin compromiso activo |
| Estado operativo de grúa | ACTIVE, MAINTENANCE, INACTIVE | Independiente de ocupación |
| Ocupación | FREE, HELD, ASSIGNED | Derivada de reservas activas |
| Reserva Fleet | HELD, CONFIRMED, RELEASED, EXPIRED | Retención y compromiso exclusivo |
| Propuesta/asignación | RESERVATION_PENDING, RESERVATION_FAILED, PROPOSED, ACCEPTED, REJECTED, EXPIRED, CANCELLED, CLOSED | Historial de cada intento |

Fleet aplica HELD → CONFIRMED/RELEASED/EXPIRED y CONFIRMED → RELEASED. Las confirmadas no vencen automáticamente. PROPOSED puede terminar REJECTED, EXPIRED o CANCELLED. Una asignación ACCEPTED pasa a CLOSED al finalizar normalmente o al ser reemplazada antes del despacho. Liberar una reserva no cambia el estado operativo del equipo.

## Ejecución

Desde DISPATCHED el flujo es DISPATCHED → ARRIVED → VEHICLE_LOADED → IN_TRANSIT → COMPLETED. Cada etapa es declarada por el operador asignado.
