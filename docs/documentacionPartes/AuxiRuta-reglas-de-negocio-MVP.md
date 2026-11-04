# AuxiRuta — Reglas de negocio fijas del MVP

> Línea base vigente: **AR-MVP-4**, 4 de noviembre de 2026. Referencia consolidada: [Decisiones arquitectónicas](../DECISIONES_ARQUITECTONICAS.md). Diseño pendiente de implementación.

## Alcance y actores

AuxiRuta administra solicitudes inmediatas de traslado de vehículos livianos para una única empresa, todos los días durante las 24 horas. El sistema valida solicitudes, selecciona candidatos, reserva recursos, solicita aceptación al operador y gestiona automáticamente búsquedas, vencimientos y cierres.

| Actor / rol | Responsabilidad y alcance de acceso |
|---|---|
| Conductor — DRIVER | Registrar vehículos propios, crear y consultar solicitudes propias y cancelar únicamente antes de la primera asignación aceptada |
| Operador — OPERATOR | Declarar disponibilidad y ubicación, aceptar/rechazar propuestas, registrar las etapas de sus servicios |
| Administrador — ADMIN | Administrar operadores, habilitaciones, grúas y vínculos; consultar globalmente solicitudes, historial, demoras y parámetros |

ADMIN no es un permiso universal: no puede aceptar propuestas, registrar etapas por otro operador, cancelar solicitudes ajenas, elegir una grúa, resolver incidencias ni forzar estados. Si una persona reúne varios roles, cada operación sigue exigiendo la propiedad o asignación del rol correspondiente. La identidad procede del subject del JWT.

### 2.1 Supuestos y exclusiones

Se asumen correctos los datos declarados del vehículo, accesible el punto de retiro y disponible el destino. Los operadores pueden cumplir su tarea sin imprevistos personales. El alta de operadores es administrada por la empresa: se crea su identidad en Keycloak, se asigna OPERATOR y se registra en Fleet su perfil, habilitación y vínculo con una grúa. No existe registro libre de prestadores independientes. La habilitación y disponibilidad se controlan para admitir cada asignación; el vencimiento durante una ejecución ya despachada no abre una incidencia ni requiere una decisión humana. Se conserva el snapshot utilizado y la exclusividad del recurso hasta liberar.

Se asume continuidad operativa desde el despacho hasta la entrega. Las etapas de ejecución son declaradas por el operador y no requieren evaluación administrativa.

Se excluyen pagos, tarifas, facturación, aseguradoras, reparaciones, emergencias, rescate de personas, sanciones, reputación, servicios futuros, múltiples empresas, paradas intermedias, optimización global, GPS continuo, mapas interactivos, geocodificación, fotografías, firmas y documentación adjunta. No se permite elegir grúa ni modificar origen o destino. El origen y el destino se conservan durante toda la solicitud; una sustitución retira el vehículo en el origen original.

No hay coordinación humana del servicio, peticiones de cancelación, correcciones de datos durante la ejecución ni resoluciones manuales de incidencias. Las notificaciones a usuarios continúan excluidas: el seguimiento se consulta por API. El mantenimiento administrativo y el diagnóstico técnico no constituyen aprobaciones del flujo de asistencia.

## Restricciones AR-MVP-4

### 5.1 Parámetros y semántica de límites

Los valores siguientes constituyen la única tabla normativa de parámetros de este documento. Se expresan en unidades explícitas; las aplicaciones deben trabajar con metros, segundos, kilogramos e instantes UTC.

| Parámetro | Valor |
|---|---|
| Centro de cobertura | Latitud -31.4167, longitud -64.1833; referencia próxima a Plaza San Martín, Córdoba, Argentina |
| Radio comercial | 15.000 m |
| Radio terrestre para Haversine | 6.371.000 m |
| Precisión admitida de coordenadas | Hasta 6 decimales, WGS84 |
| Zona horaria de presentación | America/Argentina/Cordoba |
| Tipos de vehículo | CAR, SUV y PICKUP, sin remolque |
| Peso declarado del vehículo | 500 a 3.000 kg |
| Capacidad registrable de grúa | 500 a 5.000 kg |
| Tipo de equipo | Plataforma para un único vehículo |
| Habilitación interna requerida | LIGHT_TOWING |
| Distancia inicial de traslado por calles | 500 a 30.000 m |
| Aproximación por calles | 0 a 15.000 m |
| Llegada planificada máxima | 2.700 s |
| Viaje ajustado máximo con vehículo | 5.400 s |
| Factor de ajuste de duración | 1,25 |
| Margen de llegada | 300 s |
| Planificación de partida | 300 s |
| Carga estimada ROLLABLE / NON_ROLLABLE | 900 s / 1.800 s |
| Descarga estimada | 600 s |
| Contingencia de planificación | 600 s |
| Duración de intento de búsqueda | 600 s |
| Máximo de propuestas reservadas por intento | 5 |
| Máximo de candidatos preseleccionados por ronda | 5 |
| Ventana máxima / mínima de propuesta | 60 s / 15 s |
| Reasignaciones posteriores a una aceptación | 2; hasta 3 intentos totales de búsqueda |
| Revisión de búsqueda / reconciliación | Cada 30 s / 15 s |
| Antigüedad de ubicación al reservar/aceptar | Hasta 300 s |
| Antigüedad de ubicación al despachar | Hasta 120 s |
| Antigüedad de evaluación de rutas | Hasta 300 s |
| Plazo para despacho desde aceptación | 300 s |
| Ajuste máximo de coordenada a la red vial | 100 m |
| Tolerancia adicional de demora por etapa | 900 s |
| Comprobación de demoras | Cada 30 s |

Los extremos de peso, distancia, capacidad y antigüedad son inclusivos. Los vencimientos de búsqueda, aceptación y despacho son exclusivos: al alcanzar el instante límite no se admite la acción. La presentación redondeada de una distancia no modifica la comparación del valor original.

### 5.2 Cobertura

La cobertura es un círculo comercial y no el límite administrativo del municipio. Puede incluir sectores externos al ejido y excluir sectores de Córdoba Capital. La pertenencia se calcula mediante Haversine entre el centro y el punto; la distancia debe ser menor o igual al radio definido.

Assistance verifica origen y destino antes de buscar recursos. Fleet filtra candidatos por ubicación, cobertura y vigencia; Assistance verifica la ubicación utilizada para decidir. El candidato debe cumplir cobertura también al aceptar y despachar.

La ruta puede salir transitoriamente del círculo; se controlan extremos, distancia y duración, no toda su geometría. Una grúa en ejecución no pierde su asignación por salir del círculo.

Latitud y longitud deben estar dentro de los rangos mundiales válidos. Coordenadas incompletas, exceso de precisión u origen y destino exactamente iguales son errores de entrada. Los nombres de barrio o localidad no sustituyen las coordenadas ni determinan cobertura. No se incorpora una segunda API para verificar pertenencia municipal.

### 5.3 Vehículos

El registro contiene patente, marca, modelo, tipo y peso declarado. Patente, tipo y peso son obligatorios. Marca y modelo identifican el equipo y no sustituyen sus características declaradas.

La patente se normaliza a mayúsculas sin espacios ni guiones; admite entre 6 y 10 caracteres alfanuméricos y es única. Esta validación es operativa y no verifica inscripción ni propiedad legal. No se implementa transferencia de titularidad.

El peso declarado corresponde al total a transportar, incluyendo la carga que permanezca. El vehículo debe estar ACTIVE y pertenecer al conductor. No puede tener otra solicitud no terminal ni una operación anterior con reserva o liberación incierta pendiente. Las modificaciones de patente, tipo, peso o desactivación se impiden mientras exista solicitud activa.

La condición declarada es ROLLABLE si puede rodar para maniobras de carga, o NON_ROLLABLE si requiere equipo compatible con esa condición. No se transportan ocupantes ni remolques enganchados. Motocicletas, vehículos pesados, maquinaria y rescates especiales quedan excluidos.

El acceso, la carga y la descarga se asumen posibles y los datos declarados se consideran correctos. No existe una validación presencial ni corrección posterior de discrepancias dentro del MVP. La entrega no implica reparación ni aptitud para circular.

### 5.4 Grúas y operadores

La grúa debe estar ACTIVE, sin reserva activa, con capacidad suficiente y compatibilidad explícita de tipo y movilidad. Se requiere un operador ACTIVE, AVAILABLE, vinculado exclusivamente a ese equipo y sin compromiso con otro servicio.

Capacidad, tipos admitidos, estado operativo y vínculo grúa/operador solo se modifican con recursos libres. ADMIN administra ACTIVE, MAINTENANCE e INACTIVE con motivo; ningún cambio administrativo interrumpe un compromiso activo.

La habilitación LIGHT_TOWING es un control operativo interno verificado manualmente, no una categoría legal de licencia. Debe tener inicio y vencimiento, estar vigente y vencer estrictamente después del final estimado del servicio. Se comprueba al reservar, confirmar y despachar cada asignación. Una vez despachada, se conserva la admisión utilizada para esa ejecución; el mero vencimiento posterior no interrumpe el servicio.

Fleet asigna la fecha de ubicación al recibir coordenadas. El cliente no puede rejuvenecerla mediante una fecha enviada. La ubicación vencida excluye nuevas acciones que requieran vigencia, sin liberar servicios existentes. No se exige actualización GPS continua ni se detecta fraude de posición.

Una habilitación inválida antes del despacho impide iniciarlo y activa la búsqueda automática de alternativa dentro del límite. No se cambian habilitaciones, vínculo, disponibilidad ni registro del operador con una reserva o asignación activa. Las nuevas asignaciones siempre requieren una habilitación vigente para su propio fin estimado.

### 5.5 Reglas de recorridos y planificación

La aproximación y el traslado se evalúan por calles mediante OpenRouteService. La ruta debe ser la recomendada para driving-car, sin paradas ni alternativas seleccionadas para eludir un límite. Se compara la distancia sin redondeo y se redondea hacia arriba la duración ajustada al segundo.

```text
aproximaciónAjustada = ceil(duraciónAPI_grúa_origen × factor)
trasladoAjustado = ceil(duraciónAPI_origen_destino × factor)
presupuestoLlegada = partida + aproximaciónAjustada + margenLlegada

finPlanificado = instanteValidación + aceptaciónRestante + partida
                + aproximaciónAjustada + cargaSegúnMovilidad
                + trasladoAjustado + descarga + contingencia
```

El presupuesto de llegada y el traslado ajustado deben respetar los máximos de la tabla. La aceptación restante corresponde a la ventana ofrecida al reservar y es cero después de aceptar. Al despachar, la fórmula de vigencia conserva la partida como margen conservador; esto no extiende el plazo autorizado para salir.

Los tiempos de carga y descarga planifican la habilitación. No obligan a esperar para registrar acciones efectivamente realizadas ni producen transiciones automáticas de ejecución. No existe una duración real máxima de tres horas que cierre el servicio.

Las búsquedas alternativas anteriores al despacho conservan origen y destino originales y aplican los mismos límites de distancia del traslado inicial.

Las rutas se reutilizan únicamente con iguales coordenadas, perfil y revisión de datos/reglas, dentro de su vigencia. Una evaluación vencida se recalcula antes de aceptar/despachar. Si la información no puede validarse, la acción no se habilita.

### 5.6 Búsqueda, selección y propuestas

El intento inicial comienza en createdAt e incluye evaluación, consultas y propuestas. Rechazos, conflictos o fallos técnicos no extienden su plazo. La ausencia de recursos en una ronda mantiene la búsqueda hasta el vencimiento o agotamiento del cupo de propuestas; no produce rechazo inmediato.

En cada ronda se preseleccionan los candidatos elegibles más cercanos por Haversine, hasta el máximo definido. Entre los que tengan evaluación de ruta válida se ordena por menor presupuesto de llegada, mayor tiempo libre y UUID de grúa lexicográfico. No se garantiza un óptimo global entre toda la flota.

Solo hay una propuesta activa por solicitud. No se vuelve a proponer una pareja grúa/operador rechazada, vencida o fallida en esa solicitud. Un recurso anteriormente ocupado sin propuesta fallida puede evaluarse al quedar libre.

Una reserva creada consume propuesta; un conflicto que impidió crearla no la consume. Al finalizar el plazo o agotar propuestas sin aceptación se utiliza UNFULFILLED, con motivo diferenciado: NO_AVAILABLE_RESOURCE, NO_VIABLE_CANDIDATE, OFFER_LIMIT_REACHED o DEPENDENCY_TIMEOUT.

Las solicitudes pendientes se procesan por antigüedad y UUID. Este orden local no constituye garantía de equidad global en ejecución concurrente.

### 5.7 Reserva y aceptación

Fleet verifica y reserva grúa y operador atómicamente. La propuesta identifica el intento antes de invocar el servicio remoto.

```text
expiresAt = min(instanteReserva + ventanaMáxima, searchDeadline)
```

No se emite una propuesta si el plazo restante es inferior a la ventana mínima. La confirmación debe persistirse en Fleet antes de expiresAt y searchDeadline; no basta que el cliente haya enviado su aceptación a tiempo.

Si Fleet confirmó a tiempo y se perdió la respuesta, la recuperación posterior conserva ASSIGNED. Assistance no concluye UNFULFILLED mientras exista una confirmación de resultado incierto. Si no hubo confirmación válida, se cierra/libera el intento.

El rechazo exige motivo. La liberación previa debe estar confirmada antes de proponer otro recurso. Un reintento con igual identificador y contenido recupera el resultado existente; el mismo identificador con otros datos produce conflicto.

### 5.8 Reasignaciones y despacho

Cada reasignación posterior a una aceptación abre automáticamente un nuevo intento de búsqueda de 600 s y hasta cinco propuestas. Se admiten dos reasignaciones, es decir, tres intentos totales contando el inicial. Los rechazos anteriores a aceptar no consumen reasignación. No hay endpoint humano para iniciar una búsqueda alternativa ni seleccionar su candidato.

Antes de despachar se revalidan reserva confirmada, estado operativo, ubicación, rutas y habilitación. El vencimiento de los 300 s para despachar o la pérdida confirmada de elegibilidad antes del despacho genera una búsqueda alternativa, previa liberación. Una falla temporal no extiende el plazo ni habilita el despacho a ciegas.

Si se agotan las reasignaciones, vence la búsqueda o se agotan sus propuestas sin aceptación, la solicitud termina UNFULFILLED con motivo. Una confirmación remota incierta se reconcilia antes de concluir que no existe aceptación válida. La nueva búsqueda comienza al confirmar la liberación anterior; su plazo no se consume durante esa limpieza técnica.

Una vez en DISPATCHED no se reasigna ni se cancela el servicio: el operador registra llegada, carga, traslado y entrega. Se conservan el origen, el destino y los datos declarados. Los retrasos solo generan registros informativos.

### 5.9 Ejecución y demoras

El operador asignado declara salida, llegada, carga, inicio de traslado y entrega/descarga, en ese orden. La entrega requiere nota y una asignación vigente; el administrador no puede registrarla en nombre del operador.

La llegada prevista se calcula al despachar como dispatchedAt más aproximación ajustada y margen. Superar las estimaciones de llegada, carga o traslado más 900 s genera un registro informativo único por etapa y asignación. No se suspende, cancela ni libera un servicio por superar una estimación. El registro se cierra automáticamente al completar la etapa o cerrar la asignación. No existe plazo real de tres horas ni una bandeja de evaluación humana.

### 5.10 Cancelación y cierres automáticos

El conductor solo puede cancelar antes de que exista una primera confirmación durable de aceptación en Fleet. La solicitud conserva firstAssignedAt de manera irreversible: una búsqueda de reemplazo nunca restituye el permiso de cancelar. Los estados admisibles son REQUESTED, PENDING_EVALUATION, AWAITING_ACCEPTANCE y SEARCHING_ALTERNATIVE, siempre con firstAssignedAt ausente y sin confirmación remota previa.

La carrera entre aceptación y cancelación se serializa en Assistance. Si ya se envió una confirmación a Fleet y su resultado es incierto, la cancelación devuelve 409 ACCEPTANCE_CONFIRMATION_PENDING y primero se reconcilia. Si Fleet confirmó, se conserva ASSIGNED aunque la respuesta original se haya perdido. Si no confirmó, puede procesarse la cancelación. Al admitirla se impiden confirmaciones posteriores y se libera cualquier retención pendiente.

No existen peticiones de cancelación, autorización administrativa de cancelación, corrección de datos durante el servicio, suspensión pendiente de decisión ni cierre excepcional con custodia. UNFULFILLED solo se admite antes del despacho. Después del despacho se conserva la asignación hasta la entrega y liberación; ninguna demora produce un cierre automático.

### 5.11 Validación de entradas

Descripción de solicitud: 10 a 1.000 caracteres. Motivos de rechazo/cancelación y nota de entrega: 10 a 500 caracteres. Los registros de demora son generados por el sistema; no se reciben reportes operativos ni resoluciones humanas.

La creación exige Idempotency-Key de hasta 100 caracteres, única por conductor y conservada con la solicitud. No se admite estado, propietario o grúa elegida arbitrariamente en la entrada. Una solicitud terminal no se reabre; la creación de otra requiere ausencia de compromisos anteriores inciertos o pendientes sobre el vehículo.

## Criterios de aceptación

La validación debe cubrir una historia completa: registro de vehículo, solicitud, evaluación externa, reserva, aceptación, ejecución, entrega y liberación. Se proporciona una colección de peticiones con datos reproducibles y usuarios de los distintos roles.

| Grupo | Comprobaciones |
|---|---|
| Límites de dominio | Igualdad y exceso de cobertura, peso, capacidad, distancia, duración y vigencia; criterios temporales de la sección 5 |
| Búsqueda | Ausencia temporal de recursos, rechazo/vencimiento de propuestas, cupo agotado y reasignación válida/agotada |
| Concurrencia | Dos solicitudes sobre un recurso; aceptación contra vencimiento; cancelación contra primera aceptación y confirmación con respuesta perdida |
| Integración | Ruta inexistente frente a timeout, cuota, error de credenciales y respuesta inválida |
| Recuperación | Reserva/confirmación con respuesta perdida, reinicio con acción pendiente, cierre con liberación incierta |
| Flujo automático | Vencimiento de despacho, búsqueda alternativa agotada, prohibición de cancelar tras primera aceptación y ausencia de reasignación desde DISPATCHED |
| Seguridad | Rol insuficiente, propietario ajeno, operador no asignado y acceso externo a interfaces internas |
| Logging | Eventos relevantes localizables por ID, niveles coherentes y ausencia de secretos/datos sensibles |
| Despliegue | Arranque con volumen vacío, reinicio conservando datos y procedimientos de actualización documentados |

Los tests automatizados pueden aportar evidencia cuando sean útiles; no se presume una cobertura o suite inexistente. Las comprobaciones temporales pueden usar Clock inyectado. Los criterios deben mantenerse sincronizados con cada cambio de reglas.
