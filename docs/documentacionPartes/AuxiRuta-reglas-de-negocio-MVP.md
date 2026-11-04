# AuxiRuta — Reglas de negocio fijas del MVP

Versión de reglas: AR-MVP-1. Fecha de definición: 3 de noviembre de 2026.

## 1. Carácter de este documento

Este documento fija las restricciones de dominio del MVP y prevalece sobre menciones anteriores genéricas o contradictorias. Los valores son decisiones de diseño de AuxiRuta para el trabajo práctico, no normas legales, exigencias de la cátedra ni garantías comerciales de una empresa real.

Se mantiene una empresa de remolque, dos microservicios funcionales, cuatro roles y tres bases dentro de un PostgreSQL. No se agrega monitoreo al compromiso del MVP. Las reglas se cargan por configuración, quedan fijas en la entrega y no se modifican mediante endpoints.

El radio de cobertura de 15 km y su punto de referencia fueron acordados en la conversación. Los restantes valores concretan el proceso ya definido, con un alcance que el grupo puede implementar y demostrar.

## 2. Resumen de valores

| Parámetro | Valor fijo AR-MVP-1 |
|---|---|
| Punto de referencia | Latitud -31.4167, longitud -64.1833; próximo a Plaza San Martín, Córdoba, Argentina |
| Radio comercial | 15.000 m, límites incluidos |
| Puntos sujetos a cobertura | Origen, destino y ubicación inicial de la grúa candidata |
| Horario de solicitudes | Todos los días, 24 horas; atención condicionada a recursos disponibles |
| Zona horaria de presentación | America/Argentina/Cordoba |
| Tipos admitidos | CAR, SUV, PICKUP sin remolque |
| Peso declarado | Entre 500 y 3.000 kg, inclusive |
| Grúas admitidas | Plataforma para un único vehículo |
| Traslado mínimo por ruta | 500 m |
| Traslado máximo por ruta | 30.000 m |
| Aproximación máxima por ruta | 15.000 m desde la grúa hasta el origen |
| Llegada estimada máxima | 45 min desde la confirmación de asignación; incluye partida y margen |
| Viaje estimado máximo con vehículo | 90 min ajustados |
| Factor sobre duración de API | 1,25 |
| Margen de llegada | 5 min |
| Plazo de búsqueda por intento | 10 min |
| Plazo de aceptación y reserva HELD | 60 s; limitado por el tiempo restante de búsqueda |
| Propuestas máximas por intento | 5 propuestas efectivamente reservadas |
| Grúas preseleccionadas por ronda | Hasta 5, por cercanía geográfica al origen |
| Revisión de búsqueda pendiente | Cada 30 s |
| Reconciliación de reservas activas | Cada 15 s |
| Antigüedad máxima de ubicación | 5 min al reservar y aceptar; 2 min al despachar |
| Antigüedad máxima de evaluación de ruta | 5 min al aceptar/despachar; se recalcula si está vencida |
| Plazo para despachar tras aceptación | Menor a 5 min; al alcanzar el plazo deja de permitirse el despacho |
| Carga estimada ROLLABLE | 15 min |
| Carga estimada NON_ROLLABLE | 30 min |
| Descarga estimada | 10 min |
| Margen adicional de planificación | 10 min para comprobar vigencia de habilitación |
| Reasignaciones máximas | 2 después de asignaciones aceptadas; máximo 3 intentos de búsqueda en la misma solicitud |
| Peticiones de cancelación abiertas | Máximo 1 por solicitud |
| Incidencias bloqueantes abiertas | Máximo 1 por solicitud |

Los máximos de distancia, peso y antigüedad admiten igualdad. Los vencimientos de búsqueda, aceptación y despacho son exclusivos: al llegar al instante límite ya no se acepta la acción.

## 3. Cobertura geográfica

### GEO-01 — Zona comercial circular

El centro del círculo es una constante operativa: (-31.4167, -64.1833). El radio es 15.000 metros. No se afirma que el círculo coincida con el ejido municipal ni que cubra todos los barrios de Córdoba Capital. Puede incluir sectores externos a ese municipio. El nombre de localidad escrito por el cliente no cambia la decisión.

Se calcula la distancia Haversine con radio terrestre fijo de 6.371.000 m. Un punto cumple si el resultado sin redondear es menor o igual a 15.000 m. El redondeo se usa solo para mostrar resultados. Se aceptan coordenadas WGS84, latitud entre -90 y 90 y longitud entre -180 y 180, con hasta 6 decimales en la entrada.

### GEO-02 — Validaciones

- Assistance valida origen y destino antes de buscar grúas. Si alguno está fuera, la solicitud válida estructuralmente se registra y termina REJECTED con motivo específico.
- Fleet filtra candidatos por cobertura y antigüedad de ubicación. Assistance verifica nuevamente la cobertura del candidato utilizado al evaluar.
- La ubicación del recurso debe seguir dentro del círculo al aceptar y despachar, antes de iniciar el servicio.
- Una grúa ya en ejecución puede salir del círculo siguiendo la ruta. Eso no libera su asignación ni suspende automáticamente el servicio.
- No se exige que toda la geometría de la ruta quede dentro del círculo; se controlan sus extremos y límites de recorrido.
- Coordenadas incompletas o fuera del rango mundial son error 400 y no crean solicitud.
- Origen y destino no pueden ser exactamente iguales. Ese caso es error de validación 400. Puntos diferentes pero demasiado próximos se evalúan con la distancia por ruta.

No se consulta una API geográfica adicional para decidir pertenencia municipal. La integración externa obligatoria del negocio sigue siendo la de rutas.

## 4. Personas, roles y propiedad

### USR-01 — Identidad

Toda operación de negocio requiere token válido y rol autorizado. El propietario se obtiene de sub en el token; no se acepta un ownerId arbitrario en el cuerpo. DRIVER no requiere entidad propia. OPERATOR sí tiene perfil operativo en Fleet vinculado a un único subject.

### USR-02 — Alcance de cada rol

- DRIVER administra sus vehículos y solicitudes; no elige grúa ni modifica estados libremente.
- OPERATOR opera solo las propuestas y servicios asignados a él, y actualiza únicamente su grúa.
- COORDINATOR consulta el conjunto de operaciones, atiende cancelaciones e incidencias y solicita búsquedas alternativas. No omite compatibilidad, cobertura, plazos ni habilitación.
- ADMIN administra flota y operadores y tiene permisos de coordinación. No suplanta a un operador para registrar llegada, carga o entrega.
- No se añade sanciones, suscripciones, reputación ni limitación de solicitudes por día.

## 5. Vehículos

### VEH-01 — Registro

Campos: patente, marca, modelo, tipo CAR/SUV/PICKUP y peso declarado en kg. Patente, tipo y peso son obligatorios; marca/modelo permiten identificar el vehículo pero no determinan compatibilidad. No se valida contra un registro estatal.

La patente se normaliza a mayúsculas, sin espacios ni guiones, admite entre 6 y 10 caracteres alfanuméricos y debe ser única en el sistema. Este es un identificador operativo, no un validador completo de patentes argentinas. No hay cambio de propietario ni transferencia de vehículo en el MVP.

Peso declarado entre 500 y 3.000 kg. Es el peso total a transportar declarado para esta operación, incluyendo lo que permanezca en el vehículo. No se suman pesos estimados por tipo o modelo.

### VEH-02 — Compatibilidad y condiciones excluidas

Solo se transporta un vehículo liviano por servicio, sin ocupantes durante el traslado ni remolque enganchado. Se excluyen motos, colectivos, camiones, maquinaria, rescate fuera de calzada y vehículos que requieran equipamiento especial fuera de la plataforma disponible.

El conductor declara movilidad ROLLABLE o NON_ROLLABLE. NON_ROLLABLE exige una plataforma equipada para esa condición. Un vehículo sin capacidad de rodar puede admitirse; uno que requiere rescate especializado queda fuera del alcance.

El conductor confirma que el vehículo está accesible desde vía transitable y que el destino permite descargar. Sin esa confirmación, no se admite la solicitud. Si en el lugar se descubre una discrepancia, se informa incidencia antes de cargar. La confirmación no constituye una certificación técnica de acceso.

### VEH-03 — Exclusividad y edición

- Registro ACTIVE para solicitar; INACTIVE conserva historial y no admite nuevas solicitudes.
- Una sola solicitud no terminal por vehículo, aunque el conductor tenga varios vehículos.
- No se desactiva ni modifica patente, tipo o peso de un vehículo con solicitud activa.
- Una nueva solicitud solo se permite cuando la anterior sea terminal y no haya reserva, asignación ni operación de liberación incierta o pendiente vinculada al vehículo.
- La solicitud conserva copia de tipo, peso y condición. Modificar después el registro no modifica una evaluación histórica.
- Terminar el remolque no significa reparar el vehículo ni certificar que puede circular.

## 6. Grúas y operadores

### FLE-01 — Equipo

Cada grúa posee patente única normalizada, capacidad máxima positiva, tipos admitidos, indicador supportsNonRollable y estado operativo. Para este MVP la capacidad registrada debe estar entre 500 y 5.000 kg; el límite de admisión de vehículos sigue siendo 3.000 kg.

Todos los equipos son de plataforma para un solo vehículo. La grúa debe admitir explícitamente el tipo solicitado y tener capacidad mayor o igual al peso declarado. El administrador no puede bajar capacidad ni cambiar compatibilidades mientras exista reserva/asignación activa.

### FLE-02 — Operador y habilitación

Operador ACTIVE, disponibilidad AVAILABLE, vinculado a esa grúa y sin reserva/asignación activa. Un operador no se vincula a dos grúas simultáneamente ni una grúa a dos operadores. Cambiar el vínculo requiere ambos recursos libres.

Se utiliza una habilitación operativa interna LIGHT_TOWING, verificada manualmente por ADMIN, con inicio y vencimiento. No representa una categoría legal de licencia inventada por el sistema. Debe estar vigente ahora y vencer después del fin estimado del servicio. Al coincidir exactamente con el fin estimado no alcanza.

Una renovación conserva las evaluaciones anteriores. No hay validación automática con organismos públicos ni gestión de jornadas o turnos laborales.

### FLE-03 — Estado y ocupación

Se requiere estado operativo ACTIVE. MAINTENANCE, OUT_OF_SERVICE e INACTIVE excluyen al equipo. La ocupación es FREE, HELD o ASSIGNED según sus reservas; se calcula separadamente del estado operativo.

Una avería puede dejar OUT_OF_SERVICE/ASSIGNED hasta resolver el servicio. Liberar esa asignación no reactiva el equipo. Solo ADMIN lo reactiva con motivo. Pasar a mantenimiento o inactivo requiere recursos libres; marcar una avería se permite durante un servicio.

### FLE-04 — Ubicación

Se actualiza manualmente. Fleet asigna locationUpdatedAt con su reloj al recibirla; el cliente no puede rejuvenecerla enviando una fecha arbitraria. No se detecta fraude GPS ni se calcula velocidad.

Antigüedad máxima: 5 min al preseleccionar, reservar y aceptar; 2 min al despachar. El operador debe actualizar si venció. La ubicación desactualizada excluye nuevas asignaciones, pero no libera ni completa las existentes. No se exige actualización periódica durante el viaje, porque no hay seguimiento GPS continuo.

## 7. Distancias, rutas y estimaciones

### RUT-01 — Tres medidas distintas

1. Centro → punto, Haversine: decide cobertura.
2. Grúa → vehículo, recorrido por calles: decide aproximación y orden de propuestas.
3. Vehículo → destino, recorrido por calles: decide admisión del traslado y duración estimada total.

El radio de 15 km no limita el recorrido a 15 km: origen y destino pueden estar en extremos opuestos del círculo. El traslado admite entre 500 y 30.000 m por ruta. La aproximación admite desde 0 hasta 15.000 m por ruta; si la grúa está exactamente en origen puede ser cero.

### RUT-02 — Proveedor y perfil

OpenRouteService Directions con perfil driving-car, sin rutas alternativas ni paradas intermedias. Se utiliza la ruta recomendada por el proveedor, sin probar otras para hacer cumplir un límite. El adaptador transforma las coordenadas de entrada (latitud/longitud) al orden [longitud, latitud] esperado en las coordenadas del proveedor.

Se usan distancia en metros y duración en segundos. No se atribuyen al proveedor tránsito en tiempo real ni certificación de paso para una grúa. El operador verifica la accesibilidad real y puede informar incidencia.

Para el cálculo se admite ajuste del punto a una calle de hasta 100 m; el adaptador configura o verifica esa tolerancia. Si el proveedor no puede ubicar un extremo dentro de ella, se informa ubicación no enrutable; no se desplaza silenciosamente cientos de metros.

### RUT-03 — Fórmulas fijas

```text
approachSeconds = ceil(apiApproachSeconds × 1,25)
transportSeconds = ceil(apiTransportSeconds × 1,25)

arrivalBudgetSeconds = 300 + approachSeconds + 300
# 5 min para partir + aproximación ajustada + 5 min de margen

planningEndAt = instanteDeValidación
              + aceptaciónRestante
              + 300
              + approachSeconds
              + cargaSeconds
              + transportSeconds
              + 600
              + 600
# descarga 10 min + contingencia general 10 min
```

arrivalBudgetSeconds debe ser <= 2.700 s (45 min). transportSeconds debe ser <= 5.400 s (90 min). El factor 1,25 es margen operativo del proyecto, no una predicción real de tránsito.

Al reservar, aceptaciónRestante es el plazo de reserva que se ofrecerá. Al confirmar/despachar es cero. Al despachar se usan ubicación fresca y tiempo restante de partida igual a cero; para comprobar habilitación se conserva el margen de 5 min si se desea una previsión conservadora, y se guarda la fórmula efectivamente aplicada. Para eliminar ambigüedad, AR-MVP-1 conserva los 300 s también en planningEndAt al despachar; no concede cinco minutos más para permitir la acción.

Carga: 900 s si ROLLABLE y 1.800 s si NON_ROLLABLE. Descarga: 600 s. Estos valores planifican la habilitación; no obligan a esperar para registrar una acción realizada ni completan automáticamente el servicio al vencer.

No hay una regla de duración real máxima de tres horas. Se calcula el fin estimado sumando componentes. Si el servicio real demora más, se gestiona la incidencia; no se abandona un vehículo ni se libera una grúa automáticamente por ese motivo.

### RUT-04 — Datos utilizables y vencimientos

Datos de ruta utilizables hasta 5 min desde su obtención. Reutilización solo con mismas coordenadas exactas, perfil, revisión de datos declarados y reglas. Antes de aceptar/despachar, una evaluación vencida se recalcula y se revalidan los límites. Si no se puede verificar, no se habilita la acción.

Distancias se comparan sin redondear contra metros máximos/mínimos; duraciones ajustadas se redondean hacia arriba al segundo. Respuestas nulas, distancias negativas, duraciones negativas o campos faltantes son fallas de integración, no aprobación ni ausencia de ruta.

## 8. Solicitud y evaluación inicial

### SOL-01 — Datos y creación

Entrada: vehículo propio, origen, destino, condición, descripción de 10 a 1.000 caracteres y confirmación de acceso/carga no especial. No se recibe fecha futura, grúa elegida, estado ni propietario arbitrario.

Primero se valida JSON, formato, identidad y exclusividad. Si falla estructura: 400; rol: 403; vehículo ajeno/inexistente: 404; solicitud activa o liberación pendiente: 409. No se crea una solicitud inválida en esos casos.

Si la entrada es estructuralmente válida, se persiste REQUESTED y se devuelve 201 con Location. Luego se evalúan cobertura, alcance y rutas. Puede resultar REJECTED sin cambiar el éxito de creación del recurso.

### SOL-02 — Motivos permanentes de rechazo

Origen o destino fuera de cobertura, alcance especial no admitido, traslado menor a 500 m o mayor a 30 km, viaje ajustado mayor a 90 min o ruta origen-destino inexistente confirmada por el proveedor. Peso/tipo ya se validan contra el catálogo al registrar/corregir datos; si una versión histórica no cumple el alcance vigente de la solicitud, se rechaza con motivo.

La aproximación excesiva o incompatible de una grúa solo descarta esa alternativa, no rechaza toda la solicitud. Si faltan recursos viables se busca hasta el plazo y luego queda UNFULFILLED.

## 9. Búsqueda y ranking

### BUS-01 — Intento inicial

El plazo de 10 min comienza con createdAt, incluyendo consultas externas, reservas y propuestas. No se reinicia por error técnico ni por rechazo de operador. Hasta 5 propuestas confirmadas en Fleet por intento; conflictos de reserva no consumen propuesta, pero tampoco extienden el reloj.

Si no hay recursos en una ronda se conserva REQUESTED o SEARCHING_ALTERNATIVE y se revisa cada 30 s. PENDING_EVALUATION se utiliza cuando falta información por falla temporal. No se termina inmediatamente solo porque en ese instante no haya grúa.

Al vencer el plazo sin asignación aceptada: UNFULFILLED. También termina UNFULFILLED si se rechazaron/vencieron las 5 propuestas del intento. El motivo distingue NO_AVAILABLE_RESOURCE, NO_VIABLE_CANDIDATE, OFFER_LIMIT_REACHED y DEPENDENCY_TIMEOUT. No se muestra “sin grúas” si en realidad no se pudo consultar Fleet.

### BUS-02 — Selección acotada

Fleet devuelve candidatos que cumplen requisitos operativos. Por ronda, Assistance toma hasta 5 más cercanos al origen por Haversine; descarta candidatos rechazados/vencidos en este intento. Entre esos cinco evalúa distancia y duración por API. No se afirma que se encuentre el óptimo global entre toda la flota.

Para ordenar candidatos con evaluación válida:

1. Menor arrivalBudgetSeconds.
2. Mayor tiempo libre desde su última liberación/disponibilidad.
3. UUID de grúa en orden lexicográfico para desempate estable.

Solo se propone una alternativa a la vez por solicitud. Si un candidato de la ronda no se pudo evaluar por falla temporal, puede proponerse uno cuya viabilidad sí esté confirmada; si ninguno está confirmado, se espera. No se declara inviabilidad permanente de los candidatos inciertos.

Solicitudes pendientes se procesan por createdAt y luego UUID. Es una prioridad de procesamiento local, no una garantía de equidad global entre todas las peticiones concurrentes.

### BUS-03 — Nueva búsqueda después de una asignación aceptada

Hasta 2 reasignaciones: como máximo intentos de búsqueda 1, 2 y 3. Cada reasignación válida abre un plazo nuevo de 10 min y reinicia el contador de hasta 5 propuestas. No se cuentan como reasignación los rechazos antes de que alguien haya aceptado.

Si un recurso aceptado queda inválido antes del despacho, se consume una reasignación igual que ante intervención del coordinador. Si el límite ya se agotó, finaliza UNFULFILLED cuando no comenzó el servicio, o CLOSED_UNRESOLVED desde suspensión después del despacho y antes de cargar, con resolución documentada. Este último cierre requiere coordinador: no se cierra automáticamente un servicio comenzado.

No se vuelve a proponer una pareja grúa/operador que haya fallado en esa solicitud; sí se puede considerar un recurso que antes solo estaba temporalmente ocupado, sin propuesta rechazada.

## 10. Reserva y aceptación

### RES-01 — Exclusividad

Fleet reserva grúa y operador en una transacción local. Solo se reserva ACTIVE/FREE y operador elegible. Una misma grúa u operador no tienen dos reservas HELD/CONFIRMED simultáneas. La ocupación se mantiene aunque se pierda una respuesta HTTP.

Cada propuesta usa assignmentAttemptId único, persistido antes de contactar Fleet. Repetir igual intento con mismo contenido devuelve su resultado; con contenido diferente devuelve 409. No se crea otro intento para “solucionar” un timeout sin consultar el anterior.

### RES-02 — Vencimiento

```text
expiresAt = min(instanteDeReserva + 60 s, searchDeadline)
```

No se emite propuesta si quedan menos de 15 s de búsqueda. La solicitud espera el cierre del intento sin crear una reserva de duración inútil. Si quedan entre 15 y 60 s, se ofrece ese tiempo restante.

Aceptar requiere instante de confirmación en Fleet < expiresAt y < searchDeadline. Cuenta el reloj del servidor, no la hora del cliente. Fleet revalida ubicación, cobertura, habilitación y operatividad. Si confirmación y vencimiento compiten, una única transacción decide.

Si se perdió la respuesta de una confirmación realizada a tiempo, Assistance recupera ASSIGNED aunque la consulte después del deadline. Nunca pasa a UNFULFILLED por el reloj local sin resolver una confirmación de resultado incierto. Si no existe confirmación y ya venció, libera/cierra el intento. El resultado durable prevalece sobre la llegada tardía de la respuesta.

Una aceptación HTTP recibida a tiempo en Assistance pero confirmada tarde en Fleet no cuenta como aceptada.

### RES-03 — Rechazo y liberación

Rechazo del operador exige motivo de 10 a 500 caracteres. Propuesta vencida registra motivo automático. Assistance no propone otra hasta conocer el cierre/liberación de la anterior. Ningún usuario libera un recurso cambiando a mano el estado de la solicitud.

## 11. Ejecución y despacho

### EJE-01 — Secuencia permitida

```text
ASSIGNED → DISPATCHED → ARRIVED → VEHICLE_LOADED → IN_TRANSIT → COMPLETED
```

Solo el operador asignado registra cada acción. No se saltan etapas. Llegada/carga/entrega son declaraciones operativas auditadas; sin GPS o fotos no se pretende comprobar físicamente la presencia.

### EJE-02 — Partida

El operador debe despachar antes de acceptedAt + 5 min. Si no lo hace, Assistance invalida esa asignación, registra DISPATCH_TIMEOUT y busca alternativa según el límite de reasignaciones, después de confirmar liberación. Hasta entonces no se habilita otra propuesta.

Antes de despachar: ubicación <= 2 min, evaluación <= 5 min o recalculada, recurso activo, reserva confirmada y habilitación suficiente. Si falla temporalmente una consulta, no se despacha; se puede reintentar dentro del plazo. El reloj no se extiende automáticamente por esa falla.

### EJE-03 — Demoras en ejecución

La llegada prevista se guarda al despachar: dispatchedAt + approachSeconds + 300 s. Superarla por más de 15 min genera una sola incidencia informativa DELAYED_ARRIVAL para coordinación, sin detener ni reasignar automáticamente.

Si carga supera su tiempo estimado por más de 15 min o traslado supera transportSeconds por más de 15 min, se registra una incidencia informativa de demora por etapa/asignación. No hay completado, liberación o cancelación automática por exceder la estimación. Se comprueba cada 30 s y se deduplica por etapa y asignación.

Los plazos de demora son seguimiento del dominio, no métricas ni una plataforma de monitoreo. La API puede consultarlos sin notificaciones externas.

### EJE-04 — Finalización

Completar exige IN_TRANSIT, operador asignado y ausencia de suspensión bloqueante. Se registra recepción y descarga en el destino acordado, con nota de 10 a 500 caracteres. Se cierra la solicitud y se solicita liberar reserva. Hasta confirmar Fleet, se muestra resourceReleasePending y no se permite reutilizar el recurso.

No se obliga al conductor a confirmar una segunda vez ni se cobra el servicio.

## 12. Cancelaciones

### CAN-01 — Antes del despacho

DRIVER puede cancelar estados de búsqueda, espera de aceptación y ASSIGNED con motivo de 10 a 500 caracteres. No hay multa, costo ni contador de sanciones. COORDINATOR puede cancelar con motivo en esas etapas.

Una cancelación compite de forma controlada con aceptación/despacho. Si ya se confirmó DISPATCHED, el comando del conductor recibe 409 y debe utilizar la petición de cancelación.

### CAN-02 — Después del despacho y antes de cargar

El conductor crea una petición OPEN; no detiene la grúa por sí mismo. Una petición abierta por solicitud. COORDINATOR puede rechazarla con motivo o aprobarla si el vehículo sigue sin cargar y registra que avisó al operador. Se suspende y cancela mediante el comando de negocio correspondiente.

No hay vencimiento automático de la petición: si el servicio avanza a carga se cierra REJECTED con motivo CANCELLATION_TOO_LATE. También se cierra si la solicitud termina por otra causa. No se agrega plazo de respuesta garantizado para el coordinador.

### CAN-03 — Después de cargar

No se permite cancelar, cambiar destino ni reasignar automáticamente. El problema se trata como incidencia. El coordinador puede autorizar continuación o cierre excepcional con ubicación y custodia documentadas.

## 13. Incidencias, suspensión y resolución

### INC-01 — Tipos cerrados

| Tipo | Clase | Efecto |
|---|---|---|
| TOW_TRUCK_BREAKDOWN | Bloqueante | Suspende; grúa OUT_OF_SERVICE |
| VEHICLE_DATA_MISMATCH | Bloqueante antes de cargar | Suspende y permite corrección/reevaluación |
| VEHICLE_NOT_ACCESSIBLE | Bloqueante | Suspende; requiere resolución del coordinador |
| DESTINATION_UNAVAILABLE | Bloqueante | Suspende; no habilita cambiar destino |
| OPERATOR_UNAVAILABLE | Bloqueante | Suspende si ya se despachó; antes invalida alternativa |
| QUALIFICATION_INVALID | Bloqueante | Impide acciones nuevas y requiere coordinación |
| DELAYED_ARRIVAL | Informativa | Seguimiento sin cambiar estado |
| LOADING_DELAY | Informativa | Seguimiento sin cambiar estado |
| TRANSPORT_DELAY | Informativa | Seguimiento sin cambiar estado |
| OPERATIONAL_NOTE | Informativa | Observación sin cambio de estado |

La clase se deriva del tipo, no de un boolean arbitrario enviado por el cliente. Si se conserva blocking en DTO, se valida contra el tipo y se rechaza un valor contradictorio. No se permite marcar una avería como informativa para evitar suspensión.

Una incidencia necesita descripción de 10 a 1.000 caracteres. Una bloqueante OPEN por solicitud. Repetir una incidencia con la misma clave idempotente devuelve la existente. Otras observaciones pueden añadirse como notas informativas.

### INC-02 — Resoluciones

- CONTINUE: impedimento resuelto y recurso nuevamente elegible; restaura suspendedFromStatus. No completa el servicio. Si cambió la condición declarada, reevalúa antes de continuar.
- REASSIGN: solo antes de cargar, con cupo de reasignación y liberación confirmada. Abre nuevo intento de búsqueda.
- CANCEL: solo antes de cargar y con constancia de aviso al operador.
- CLOSE_UNRESOLVED: servicio que comenzó y no se completará; exige motivo, ubicación final válida, nombre o identificación operativa de quien queda a cargo y constancia de entrega/custodia.

Solo COORDINATOR o ADMIN resuelven. Una avería requiere reactivación por ADMIN antes de CONTINUE. La grúa y el operador permanecen ocupados durante una suspensión. Una incidencia informativa puede resolverse con CONTINUE como “observación atendida”, sin restaurar ni alterar ningún estado.

Si una resolución cancela, reasigna o cierra excepcionalmente, la incidencia bloqueante pasa a RESOLVED en la misma transacción local que registra esa decisión. Las observaciones informativas OPEN pueden cerrarse con nota al terminar la solicitud, conservando su historial. No se deja una incidencia bloqueante abierta sobre una solicitud terminal por descuido.

No existe un timeout que cierre una suspensión por sí mismo. A los 15 min de suspensión se marca el caso como pendiente prioritario para coordinación en la consulta; no se suma otro estado ni se libera el recurso.

### INC-03 — Corrección de datos

Solo COORDINATOR, en suspensión anterior a carga por VEHICLE_DATA_MISMATCH, corrige tipo, peso o condición dentro del alcance admitido. Se incrementa revisión y conserva el antes/después. No se cambia propietario, vehículo, origen ni destino. No se actualiza a escondidas el registro maestro del conductor.

Si los datos reales exceden 3.000 kg o el catálogo, se documentan como motivo de incompatibilidad y se cancela/cierra según etapa; no se guarda una corrección inválida para forzar aceptación.

## 14. Vigencia de habilitación durante el servicio

Se comprueba al reservar, confirmar, despachar, cargar, iniciar traslado y continuar tras suspensión. Antes de la carga, una habilitación inválida impide avanzar y requiere otro recurso según las reglas.

Si se detecta después de cargar, se registra incidencia bloqueante y se exige coordinación; el sistema no ordena abandonar el vehículo, no libera recursos ni modela un reemplazo de operador en ruta. El coordinador no puede omitir el chequeo para marcar CONTINUE; debe regularizarse el recurso o documentarse cierre excepcional. Un reloj de planificación no es una autorización legal.

## 15. Fallos de integración y recuperación

### TEC-01 — Parámetros operativos de integración

Separados de las reglas comerciales, pero fijos en la configuración de la entrega: timeout de conexión 2 s y respuesta 5 s por llamada; una llamada por intento, sin repetir escrituras de inmediato. Evaluaciones pendientes cada 30 s. Acciones de recursos reintentan a los 5, 15 y 30 s y después cada 60 s mientras haya incertidumbre pendiente.

Un 429 respeta Retry-After; si no viene, espera 60 s. No se aprueba sin ruta ni se inventan tiempos. Si el plazo comercial de búsqueda vence se resuelve el resultado de negocio, pero la limpieza de reservas inciertas continúa.

401/403 técnicos requieren corrección de credenciales/permisos; el caso queda pendiente con motivo técnico visible y no se bombardea el proveedor con reintentos. Los rechazos 4xx de una operación inválida se registran como fallo de dominio/contrato, no como caída genérica.

### TEC-02 — Consistencia

Se persisten intenciones y resultados de reserva/confirmación/liberación. Si existe duda sobre una escritura, primero se consulta por assignmentAttemptId. No se crea un segundo recurso para la misma acción.

Fleet expira HELD automáticamente con su reloj. Los consumidores deben tratarla como vencida por expiresAt aunque la tarea todavía no haya materializado EXPIRED. Reservas CONFIRMED nunca vencen por cronómetro; se liberan al cerrar la asignación.

Toda reconciliación consulta desde Assistance hacia Fleet; no hay callback inverso. Las tareas y los endpoints ejecutan el mismo método de negocio para evitar reglas diferentes.

### TEC-03 — Idempotencia

Crear solicitud exige Idempotency-Key de hasta 100 caracteres, única por conductor. Se conserva mientras exista la solicitud. Misma clave y mismos datos devuelve la misma solicitud; misma clave con datos distintos: 409. Las acciones de transición usan clave por solicitud/acción y guardan su resultado en historial o entidad de negocio, con unicidad local.

Idempotencia no autoriza a repetir la operación sobre otro recurso ni evita verificar identidad. Acciones tardías de una asignación reemplazada reciben 409 sin alterar el servicio actual.

## 16. Auditoría, reloj y comparaciones

- UTC en persistencia y reloj del servidor; presentación en America/Argentina/Cordoba.
- Fecha de inicio/vencimiento de habilitación como instantes explícitos. Si se carga una fecha civil, la interfaz debe pedir la hora o convertirla con una regla documentada; no se adivina el último segundo del día.
- Estados, actor, motivo y fecha por transición. Snapshots de reglas, ubicaciones y rutas utilizadas. No se borran decisiones previas.
- Cambios de configuración futuros requieren otra versión AR-MVP-* y revisión del compromiso; no se cambian silenciosamente las reglas de solicitudes activas.
- Los límites de búsqueda/reserva quedan materializados como timestamps. Los de admisión/rutas se guardan en snapshot para reevaluar una solicitud bajo la misma versión de reglas.
- Versionado/bloqueos y restricciones locales protegen exclusividad; una consulta previa sin control concurrente no basta.

## 17. Estados y resultado de reglas

Se conservan los estados definidos en el documento específico. AWAITING_ACCEPTANCE puede salir también a UNFULFILLED cuando vence el intento o se agotan propuestas, después de resolver cualquier confirmación incierta. ASSIGNED puede salir a UNFULFILLED si falla antes de despachar y no queda cupo de reasignación; una ejecución iniciada se resuelve desde SUSPENDED por coordinación.

La falta temporal de recursos no equivale a REJECTED. Las demoras no equivalen a COMPLETED. Una suspensión no equivale a cancelación. CLOSED_UNRESOLVED es un resultado excepcional documentado, no una entrega exitosa.

## 18. Casos de aceptación con límites

| Caso | Resultado esperado |
|---|---|
| Punto a exactamente 15.000 m del centro | Dentro de cobertura |
| Punto a 15.000,01 m | Fuera de cobertura |
| Vehículo 3.000 kg y capacidad grúa 3.000 kg | Compatible por peso |
| Vehículo 3.000,01 kg | Fuera del alcance |
| Traslado por ruta 30.000 m | Admitido por distancia |
| Traslado por ruta 30.000,01 m | REJECTED |
| Ruta de aproximación 15.000,01 m | Descarta esa grúa |
| Llegada planificada 2.700 s | Cumple |
| Llegada planificada 2.701 s | Descarta esa grúa |
| Dos solicitudes reservan el mismo recurso | Solo una obtiene HELD |
| Confirmación exactamente en expiresAt | No se acepta |
| Fleet confirmó antes del plazo y la respuesta se perdió | Se recupera ASSIGNED, sin duplicar |
| No hay grúas en la primera ronda | Sigue buscando hasta deadline/cupo |
| Tiempo de búsqueda agotado y sin confirmación | UNFULFILLED y limpieza pendiente si corresponde |
| Se venció propuesta número 5 | UNFULFILLED en ese intento |
| Tercera reasignación solicitada | 409; coordinador debe resolver dentro de las alternativas permitidas |
| Se supera estimación durante traslado | Incidencia informativa; conserva recurso ocupado |
| Avería con vehículo cargado | SUSPENDED; sin reasignación automática |
| Solicitud terminal con liberación incierta | No admite nuevo servicio sobre ese vehículo hasta resolver |

## 19. Configuración entregable y límites del alcance

El archivo `AuxiRuta-parametros-MVP.yml` concentra los valores fijos como referencia para distribuirlos en la configuración de cada servicio. No es un Compose ni una aplicación ejecutable. Los DTOs y tablas deben reflejar las aclaraciones listadas al final del modelo persistente.

No se agregan mapas interactivos, geocodificación, pagos, aseguradoras, sanciones, GPS continuo, turnos futuros, multas, elección manual de grúa, rescate especial, rutas con múltiples retiros, transbordo tras cargar ni herramientas de monitoreo obligatorias.

## 20. Fuente de datos externos

La distancia y duración de rutas son información externa. Los límites de 15/30 km, los márgenes y tiempos anteriores son decisiones propias del proyecto. Documentación del proveedor: [Directions](https://giscience.github.io/openrouteservice/v9.10.0/api-reference/endpoints/directions/) y [respuestas: distancia en metros y duración en segundos](https://giscience.github.io/openrouteservice/v9.10.0/api-reference/endpoints/directions/requests-and-return-types).
