# AuxiRuta — Estados y transiciones del MVP

> Restricciones fijas: `AuxiRuta-reglas-de-negocio-MVP.md` (AR-MVP-1), con parámetros en `AuxiRuta-parametros-MVP.yml`. Cobertura circular de 15 km. Ante diferencias anteriores, prevalece el documento de reglas para límites, plazos y casos de borde.

## 1. Propósito y decisiones del modelo

Este documento precisa la propuesta inicial. Cuando exista una diferencia, estas definiciones son la referencia para el MVP junto con el documento de operaciones y alcance.

Revisado según los apuntes. El modelo persistente vigente se detalla en `AuxiRuta-modelo-persistente-MVP.md`. Los estados del negocio no son los estados JPA New/Managed/Detached/Removed: estos últimos describen cómo el ORM administra los objetos.

Se distinguen cuatro conceptos: la solicitud expresa el progreso del servicio; el vehículo conserva su registro y su condición declarada; la grúa tiene un estado operativo y otro de ocupación; las incidencias tienen su propio ciclo. No se debe guardar el mismo estado de negocio en varios servicios.

Assistance es dueño de solicitudes, vehículos, asignaciones e incidencias. Fleet es dueño de grúas, operadores y reservas. Cada transición registra fecha, identidad responsable, estado anterior, estado nuevo y motivo cuando corresponda. Las fechas se almacenan como instantes UTC y se muestran en la zona horaria configurada.

## 2. Estados de la solicitud

| Estado | Significado | Cómo ingresa | Cómo puede salir |
|---|---|---|---|
| REQUESTED | Solicitud registrada, todavía sin evaluación concluida | El conductor solicita asistencia | PENDING_EVALUATION, AWAITING_ACCEPTANCE, REJECTED, UNFULFILLED, CANCELLED |
| PENDING_EVALUATION | Falta información confiable por una falla temporal de Fleet o de rutas | El evaluador detecta timeout, error temporal o respuesta inválida | AWAITING_ACCEPTANCE, REJECTED, UNFULFILLED, CANCELLED |
| AWAITING_ACCEPTANCE | Hay una reserva temporal y se espera respuesta del operador | Fleet confirma la reserva temporal | ASSIGNED, SEARCHING_ALTERNATIVE, CANCELLED, UNFULFILLED |
| ASSIGNED | El operador aceptó y Fleet confirmó la asignación | Confirmación exitosa de la reserva | DISPATCHED, SEARCHING_ALTERNATIVE, CANCELLED, UNFULFILLED |
| DISPATCHED | La grúa salió hacia el origen | El operador registra la salida | ARRIVED, SUSPENDED |
| ARRIVED | La grúa llegó al vehículo, que todavía no fue cargado | El operador confirma llegada | VEHICLE_LOADED, SUSPENDED |
| VEHICLE_LOADED | El vehículo fue cargado | El operador confirma carga | IN_TRANSIT, SUSPENDED |
| IN_TRANSIT | La grúa transporta el vehículo hacia el destino | El operador inicia el traslado | COMPLETED, SUSPENDED |
| SUSPENDED | Una incidencia bloqueante impide continuar | El operador informa una incidencia bloqueante durante la ejecución | Estado previo a la suspensión, SEARCHING_ALTERNATIVE, CANCELLED, CLOSED_UNRESOLVED |
| SEARCHING_ALTERNATIVE | Se perdió o rechazó una alternativa y se busca otra | Rechazo, vencimiento o reasignación autorizada | PENDING_EVALUATION, AWAITING_ACCEPTANCE, UNFULFILLED, CANCELLED |
| COMPLETED | El operador confirmó entrega y descarga en el destino acordado | Finalización desde IN_TRANSIT | Ninguna |
| REJECTED | La solicitud incumple una regla objetiva | Evaluación: fuera de cobertura o vehículo no admitido por el alcance | Ninguna |
| UNFULFILLED | La solicitud es válida, pero no se consiguió un recurso dentro del plazo | Sin candidatos viables o vencimiento del plazo total de búsqueda | Ninguna |
| CANCELLED | El servicio se canceló antes de cargar el vehículo | Conductor antes del despacho; coordinador después, bajo condiciones | Ninguna |
| CLOSED_UNRESOLVED | El servicio comenzó, pero no pudo completarse; quedó documentada su terminación excepcional | El coordinador cierra una suspensión con resolución y constancia de custodia | Ninguna |

REQUESTED se persiste antes de iniciar llamadas externas. La creación devuelve 201 aunque la evaluación continúe después. REJECTED expresa una decisión del negocio; una petición con datos inválidos recibe un error HTTP y no crea una solicitud.

### 2.1 Flujo normal

```text
REQUESTED → AWAITING_ACCEPTANCE → ASSIGNED → DISPATCHED
                                              ↓
COMPLETED ← IN_TRANSIT ← VEHICLE_LOADED ← ARRIVED
```

### 2.2 Búsqueda, reintentos y vencimientos

- Una falla temporal deja la evaluación pendiente; no constituye un rechazo. Se usa RestClient con timeouts y reintentos acotados. Resilience4j queda como mejora opcional.
- Búsqueda: 10 min desde creación, hasta 5 propuestas reservadas por intento. No se reinicia por cada candidato/reintento. Reserva/aceptación: hasta 60 s, limitada por el plazo restante; no se propone si quedan menos de 15 s.
- Si no hay candidatos en una ronda, se consulta cada 30 s hasta el plazo. Termina UNFULFILLED al vencer o agotar 5 propuestas. Una confirmación incierta se resuelve antes de concluir que no hubo aceptación. La incertidumbre técnica mantiene PENDING_EVALUATION.
- Una propuesta rechazada o vencida pasa a SEARCHING_ALTERNATIVE; se descarta ese candidato durante el intento de búsqueda actual.
- El vencimiento de una reserva no es un estado terminal de la solicitud: dispara otra búsqueda, si queda tiempo.
- Una reasignación válida abre otro intento de 10 min con historial. Máximo 2 reasignaciones después de aceptación y 3 intentos totales. Un impedimento antes del despacho o falta de partida en 5 min también consume cupo; los rechazos antes de aceptación no lo consumen.
- Una solicitud terminal no se reabre. Se permite otra cuando esté confirmada la liberación y no exista reserva incierta pendiente para el vehículo.

### 2.3 Aceptación y despacho

La aceptación es válida antes de expiresAt. Fleet decide atómicamente; al coincidir con el límite ya venció. Assistance pasa a ASSIGNED después de confirmar. Si Fleet confirmó a tiempo y se perdió la respuesta, recuperar el resultado después del plazo no invalida la aceptación. Despacho: antes de acceptedAt + 5 min; el endpoint valida el plazo aunque la tarea no haya corrido.

Antes del despacho se verifican asignación activa, recurso operativo y habilitación vigente. Una dependencia no disponible impide despachar y permite reintentar. Una incompatibilidad confirmada exige otra alternativa. No se despacha con una validación antigua si ya dejó de ser válida.

Si Fleet registra un impedimento mientras la solicitud está AWAITING_ACCEPTANCE o ASSIGNED, Assistance lo detecta por consulta, invalida esa alternativa y gestiona la liberación antes de continuar en SEARCHING_ALTERNATIVE. Si ya está en ejecución, crea una incidencia bloqueante y pasa a SUSPENDED. El sondeo no ofrece detección instantánea.

### 2.4 Cancelación

- El conductor puede cancelar REQUESTED, PENDING_EVALUATION, SEARCHING_ALTERNATIVE, AWAITING_ACCEPTANCE y ASSIGNED.
- En DISPATCHED o ARRIVED, el coordinador debe registrar primero una suspensión y luego cancelar con motivo. El vehículo no debe estar cargado y debe constar que el operador fue informado.
- En SUSPENDED se permite CANCELLED únicamente si la suspensión ocurrió antes de VEHICLE_LOADED.
- Después de la carga no existe cancelación ordinaria. Se continúa, se suspende o se cierra excepcionalmente con constancia de resolución y custodia.
- Cancelar no elimina historial ni libera recursos a ciegas: la liberación se confirma con Fleet.

## 3. Incidencias y suspensión

| Estado de incidencia | Significado | Responsable |
|---|---|---|
| OPEN | Informada y pendiente de resolución | Operador asignado o coordinador la registra |
| RESOLVED | Existe una resolución documentada | Coordinador |

Una incidencia tiene tipo del catálogo AR-MVP-1, descripción, clase bloqueante/informativa derivada del tipo, solicitud, asignación, reportante y fechas. El cliente no elige libremente esa clase. La resolución contiene acción adoptada, explicación, responsable y fecha.

Una incidencia informativa no cambia el estado de la solicitud. Demora mayor a 15 min sobre llegada/carga/traslado estimados registra una incidencia informativa deduplicada; no completa ni libera recursos. Una suspensión de más de 15 min se prioriza para coordinación, sin cierre automático. Una bloqueante lleva DISPATCHED, ARRIVED, VEHICLE_LOADED o IN_TRANSIT a SUSPENDED y conserva `suspendedFromStatus`. El MVP admite una sola incidencia bloqueante abierta por solicitud; otras observaciones pueden ser informativas.

Resoluciones permitidas:

| Resolución | Condiciones | Efecto |
|---|---|---|
| CONTINUE | El impedimento se solucionó y el recurso sigue habilitado; si se cargó, la custodia sigue a cargo del mismo operador | Restaura el estado previo a la suspensión |
| REASSIGN | Suspensión anterior a la carga; se confirmó el cierre de la asignación previa | Pasa a SEARCHING_ALTERNATIVE |
| CANCEL | Suspensión anterior a la carga; operador informado | Pasa a CANCELLED |
| CLOSE_UNRESOLVED | No se completará el traslado; se documentan resultado, ubicación final y responsable de la custodia o recepción | Pasa a CLOSED_UNRESOLVED |

Cerrar una incidencia con CONTINUE no completa el traslado. Una avería bloqueante marca la grúa fuera de servicio en Fleet; si esa comunicación falla, se mantiene la suspensión y se reintenta. Para continuar después de repararla se exige reactivación explícita en Fleet.

Antes del despacho, un impedimento se resuelve mediante reasignación o cancelación y queda igualmente auditado. No hace falta suspender un traslado que todavía no comenzó.

## 4. Estados del vehículo del conductor

### 4.1 Estado del registro, persistido en Assistance

| Estado | Significado | Transiciones |
|---|---|---|
| ACTIVE | Puede utilizarse para nuevas solicitudes | INACTIVE si no tiene solicitudes activas |
| INACTIVE | Conserva historial, pero no admite nuevas solicitudes | ACTIVE por su propietario |

No se elimina físicamente un vehículo con historial. No se cambian peso, tipo ni características de compatibilidad durante una solicitud activa. Cada solicitud guarda una copia de los datos utilizados para evaluarla.

### 4.2 Condición declarada para una solicitud

La condición no representa una certificación mecánica ni cambia automáticamente al terminar un remolque.

| Condición | Significado | Efecto |
|---|---|---|
| ROLLABLE | Puede desplazarse sobre sus ruedas para las maniobras de carga, aunque necesite remolque | Admite equipos compatibles con esa condición |
| NON_ROLLABLE | No puede desplazarse sobre sus ruedas | Exige equipo capaz de cargarlo en esa condición |

La condición se declara al solicitar. Si en el lugar se descubre una diferencia, el operador informa incidencia. El coordinador puede corregir los datos declarados de la solicitud suspendida antes de la carga; el cambio exige una nueva evaluación, sin alterar el snapshot histórico anterior.

### 4.3 Situación logística del vehículo, derivada

Se calcula desde la solicitud activa y no se mantiene como otro estado editable:

| Situación mostrada | Fuente |
|---|---|
| SIN_SERVICIO_ACTIVO | No tiene solicitud activa |
| ESPERANDO_ASISTENCIA | Búsqueda, propuesta, asignación, despacho o llegada antes de la carga |
| CARGADO | Solicitud en VEHICLE_LOADED |
| EN_TRASLADO | Solicitud en IN_TRANSIT |
| SERVICIO_SUSPENDIDO | Solicitud en SUSPENDED; se muestra además si ya fue cargado |

Para solicitudes terminales, el detalle histórico muestra ENTREGADO, CANCELADO, NO_ATENDIDO o CERRADO_SIN_COMPLETAR según el resultado. Un vehículo entregado no se considera reparado ni apto para circular.

## 5. Estados de la grúa

### 5.1 Estado operativo, persistido en Fleet

| Estado | Significado | Quién lo cambia |
|---|---|---|
| ACTIVE | Equipo habilitado para operar | ADMIN, luego de verificar requisitos |
| MAINTENANCE | Equipo en mantenimiento | ADMIN; solo puede ingresarlo sin asignación activa |
| OUT_OF_SERVICE | Avería o condición que impide operar | Operador asignado al reportar avería, coordinador o ADMIN |
| INACTIVE | Equipo retirado de la operación habitual | ADMIN; requiere ausencia de reserva o asignación activa |

ACTIVE puede pasar a MAINTENANCE, OUT_OF_SERVICE o INACTIVE. La reactivación desde cualquiera de ellos corresponde a ADMIN y exige motivo. MAINTENANCE e INACTIVE solo se aplican a equipos libres; una avería puede producir OUT_OF_SERVICE durante un servicio y debe iniciar su tratamiento como incidencia.

### 5.2 Estado de ocupación, controlado por Fleet

| Estado | Significado | Cómo cambia |
|---|---|---|
| FREE | Sin reserva o asignación vigente | Se reserva → HELD |
| HELD | Reserva temporal esperando aceptación | Se confirma → ASSIGNED; se rechaza, libera o vence → FREE |
| ASSIGNED | Recurso comprometido con un servicio aceptado | Cierre confirmado de asignación → FREE |

Una grúa es candidata solo si está ACTIVE y FREE, tiene operador compatible y disponible, ubicación vigente y capacidad suficiente. El operador también se reserva exclusivamente; no puede utilizar otra grúa al mismo tiempo.

Una grúa OUT_OF_SERVICE puede seguir ASSIGNED mientras se resuelve una incidencia. Al cerrar esa asignación pasa a FREE, pero continúa OUT_OF_SERVICE y no puede ser seleccionada.

DISPATCHED, ARRIVED e IN_TRANSIT pertenecen a la solicitud. Fleet puede consultar la referencia al servicio, pero no necesita duplicar su ciclo completo.

## 6. Ciclo de la reserva de recursos

| Estado | Significado |
|---|---|
| HELD | Retención temporal de grúa y operador |
| CONFIRMED | El operador aceptó y los recursos quedaron asignados |
| RELEASED | La propuesta se rechazó/canceló o terminó la asignación |
| EXPIRED | Terminó el plazo de aceptación sin confirmación |

Transiciones: HELD → CONFIRMED / RELEASED / EXPIRED; CONFIRMED → RELEASED. Los estados terminales no se reabren. Cada propuesta utiliza un `assignmentAttemptId` distinto; los reintentos técnicos conservan el mismo identificador.

## 7. Consistencia y fallos

- Fleet reserva grúa y operador en una transacción local. Solo una solicitud gana si hay competencia.
- Assistance conserva acciones pendientes hacia Fleet y las reintenta. No se requieren transacciones distribuidas ni un broker para el MVP.
- Cada cambio pendiente utiliza un identificador idempotente. Repetirlo no duplica reservas, incidencias ni liberaciones. La recuperación usa acciones tipadas hacia Fleet, sin motor genérico de eventos ni payloads JSON arbitrarios.
- Se usa control de versión para impedir que cancelación, aceptación y vencimiento produzcan resultados incompatibles.
- Si la solicitud termina y Fleet todavía no confirmó la liberación, la respuesta muestra `resourceReleasePending: true`. Los recursos siguen ocupados hasta confirmar la liberación.
- Assistance consulta a Fleet para recuperar operaciones interrumpidas y detectar cambios de reservas o estado operativo. Fleet administra vencimientos sin llamar a Assistance. También se revalida al aceptar, despachar y continuar; puede existir un intervalo entre el cambio en Fleet y su detección por sondeo.
- No se permite un endpoint genérico que cambie libremente el estado. Cada transición corresponde a una acción del negocio y valida estado, rol y pertenencia.

## 8. Ejemplos integrados

### Servicio normal

Solicitud: REQUESTED → AWAITING_ACCEPTANCE → ASSIGNED → DISPATCHED → ARRIVED → VEHICLE_LOADED → IN_TRANSIT → COMPLETED.

Grúa: ACTIVE/FREE → ACTIVE/HELD → ACTIVE/ASSIGNED → ACTIVE/FREE al confirmar la liberación.

Vehículo: registro ACTIVE, condición declarada inalterada; situación logística de espera → cargado → en traslado → sin servicio activo, con entrega en su historial.

### Avería antes de cargar

Solicitud: DISPATCHED → SUSPENDED → SEARCHING_ALTERNATIVE → AWAITING_ACCEPTANCE. El coordinador resolvió la incidencia con REASSIGN.

Primera grúa: ACTIVE/ASSIGNED → OUT_OF_SERVICE/ASSIGNED → OUT_OF_SERVICE/FREE. La nueva grúa recorre su propio ciclo de reserva.

### Avería después de cargar

Solicitud: IN_TRANSIT → SUSPENDED. Se permite continuar con el mismo recurso tras solucionar y validar el impedimento, o cerrar con CLOSED_UNRESOLVED y constancia de custodia. El transbordo y la coordinación automática de una segunda grúa quedan fuera del MVP.

## 9. Límites geográficos y temporales de AR-MVP-1

Cobertura: radio de 15.000 m desde (-31.4167, -64.1833), con Haversine. Aproximación por calles <=15 km; traslado entre 500 m y 30 km. Llegada planificada <=45 min y viaje ajustado <=90 min, con factor 1,25. Vehículos CAR/SUV/PICKUP de 500 a 3.000 kg. Solo plataforma. Los detalles de fórmulas, habilitaciones, plazos, máximo de reasignaciones y recuperación están en el documento de reglas. No existe un máximo real de tres horas que cierre servicios automáticamente.
