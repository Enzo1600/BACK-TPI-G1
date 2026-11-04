# AuxiRuta — Asistencia y remolque vehicular

> Línea base vigente: **AR-MVP-3**, 4 de noviembre de 2026. Referencia consolidada: [Decisiones arquitectónicas](../DECISIONES_ARQUITECTONICAS.md). Diseño pendiente de implementación.

## Alcance y actores

AuxiRuta administra solicitudes inmediatas de traslado de vehículos livianos para una única empresa, todos los días durante las 24 horas. El sistema valida solicitudes, selecciona candidatos, reserva recursos, solicita aceptación al operador y gestiona automáticamente búsquedas, vencimientos, sustituciones por avería y cierres.

| Actor / rol | Responsabilidad y alcance de acceso |
|---|---|
| Conductor — DRIVER | Registrar vehículos propios, crear y consultar solicitudes propias y cancelar únicamente antes de la primera asignación aceptada |
| Operador — OPERATOR | Declarar disponibilidad y ubicación, aceptar/rechazar propuestas, registrar las etapas de sus servicios y comunicar averías |
| Administrador — ADMIN | Administrar operadores, habilitaciones, grúas y vínculos; consultar globalmente solicitudes, historial, incidencias y parámetros |

ADMIN no es un permiso universal: no puede aceptar propuestas, registrar etapas por otro operador, cancelar solicitudes ajenas, elegir una grúa, resolver incidencias ni forzar estados. Si una persona reúne varios roles, cada operación sigue exigiendo la propiedad o asignación del rol correspondiente. La identidad procede del subject del JWT.

### 2.1 Supuestos y exclusiones

Se asumen correctos los datos declarados del vehículo, accesible el punto de retiro y disponible el destino. Los operadores pueden cumplir su tarea sin imprevistos personales. La habilitación y disponibilidad se controlan para admitir cada asignación; el vencimiento durante una ejecución ya despachada no abre una incidencia ni requiere una decisión humana. Se conserva el snapshot utilizado y la exclusividad del recurso hasta liberar.

El único impedimento operativo excepcional modelado es la avería de una grúa. La avería solo se admite en ASSIGNED, DISPATCHED, ARRIVED o VEHICLE_LOADED. Desde IN_TRANSIT se asume que la grúa completa el traslado sin averías; no se admite reportarlas ni reasignar por ese motivo. La transferencia física entre equipos, la custodia, el rescate especial y sus procedimientos reales quedan fuera del modelo evaluativo.

Se excluyen pagos, tarifas, facturación, aseguradoras, reparaciones, emergencias, rescate de personas, sanciones, reputación, servicios futuros, múltiples empresas, paradas intermedias, optimización global, GPS continuo, mapas interactivos, geocodificación, fotografías, firmas y documentación adjunta. No se permite elegir grúa ni modificar origen o destino. El origen y el destino se conservan durante toda la solicitud; una sustitución retira el vehículo en el origen original.

No hay coordinación humana del servicio, peticiones de cancelación, correcciones de datos durante la ejecución ni resoluciones manuales de incidencias. Las notificaciones a usuarios continúan excluidas: el seguimiento se consulta por API. El mantenimiento administrativo y el diagnóstico técnico no constituyen aprobaciones del flujo de asistencia.

## Microservicios y colaboración

| Componente | Responsabilidad | Datos propios |
|---|---|---|
| assistance-service | Administrar solicitudes, evaluar viabilidad y recorridos, seleccionar alternativas y coordinar el ciclo del servicio | Vehículos, solicitudes, evaluaciones, propuestas/asignaciones, historial, incidencias automáticas y acciones pendientes hacia Fleet |
| fleet-service | Administrar la capacidad operativa y garantizar exclusividad de recursos | Operadores, habilitación vigente, grúas, vínculo actual grúa/operador, reservas e historial operativo de grúas |
| api-gateway | Enrutar y controlar el acceso externo a las APIs | Configuración de rutas y seguridad; sin base de negocio |
| Keycloak | Administrar identidad, autenticación y emisión de tokens | Usuarios, credenciales, roles, clientes y configuración del realm |
| PostgreSQL | Persistir datos con aislamiento lógico por componente | Tres bases y credenciales independientes |
| OpenRouteService | Calcular recorridos por calles y estimaciones de distancia/duración | Fuente externa; no administra reglas ni asignaciones de AuxiRuta |

La separación responde a dos decisiones distintas: Fleet determina si un recurso puede operar y reservarse; Assistance determina si una solicitud puede atenderse y cómo evoluciona. Fleet no decide el resultado completo de la solicitud y Assistance no modifica directamente sus tablas.

Cada servicio funcional tiene repositorios y modelo propios. La colaboración usa HTTP/JSON. Assistance consume Fleet y OpenRouteService; Fleet no invoca Assistance. Los cambios de Fleet se detectan mediante consulta y reconciliación desde Assistance, con consistencia eventual y sin garantía de detección instantánea.

El Gateway constituye infraestructura y no cuenta como uno de los dos microservicios funcionales. El componente común es una dependencia Maven y no un contenedor ejecutable.

## Flujo principal

1. Assistance valida identidad, propiedad, entrada y exclusividad local; persiste REQUESTED con la intención de evaluar.
2. Valida alcance y consulta la ruta origen-destino. Un incumplimiento confirmado termina REJECTED; una falla temporal conserva evaluación pendiente.
3. Consulta candidatos elegibles en Fleet y aplica preselección y evaluación de aproximación.
4. Guarda evaluación, snapshots y el identificador de intento antes de solicitar la reserva.
5. Fleet revalida y retiene operador y grúa en una transacción local.
6. Assistance confirma el resultado de reserva y publica la propuesta consultable en AWAITING_ACCEPTANCE.
7. El operador acepta o rechaza. Assistance solicita confirmación/liberación a Fleet; ASSIGNED solo se registra después de confirmar la aceptación durable.
8. El operador ejecuta la secuencia autorizada. Al terminar, Assistance registra el cierre y solicita liberar los recursos.

Fleet conserva la autoridad sobre disponibilidad y Assistance sobre el resultado de la solicitud. La participación de ambos es necesaria para producir una asignación válida; no se trata de dos catálogos independientes.

## Avería y sustitución

El operador comunica una avería de su asignación aceptada. Assistance bloquea/versiona la solicitud, registra la incidencia y la cancelación de esa asignación, conserva la primera aceptación y mantiene el origen original. La operación solo se admite antes de IN_TRANSIT; la carrera contra transport-start se resuelve con bloqueo/versionado de la solicitud.

Se persisten acciones idempotentes para marcar OUT_OF_SERVICE en Fleet y liberar el compromiso anterior. Hasta confirmar ambos efectos no se reserva otro equipo. Durante esa recuperación SEARCHING_ALTERNATIVE expone recoveryPending; su nuevo plazo de búsqueda comienza al confirmar la liberación y disponibilidad para buscar, sin consumirlo por una limpieza remota pendiente.

Luego se inicia automáticamente el siguiente intento, o se termina UNFULFILLED si no queda cupo o la recuperación es inviable. Se conserva el historial y el destino. La grúa averiada nunca vuelve a ser candidata mientras siga OUT_OF_SERVICE. El operador recupera libertad tras la liberación, pero una nueva candidatura exige otro equipo operativo y vínculo administrado sin reservas activas.

## Arquitectura técnica

Gateway Spring Cloud Gateway WebFlux; microservicios Spring Boot MVC/JPA; identidad Keycloak; PostgreSQL con assistance_db, fleet_db y keycloak_db; cliente RestClient hacia Fleet y OpenRouteService. El JAR auxiruta-commons comparte utilidades de seguridad y errores, sin entidades ni DTOs de negocio. Todo se desplegará con Docker Compose.

La cobertura comercial es circular, de 15 km alrededor de (-31.4167, -64.1833), con cálculo local Haversine. OpenRouteService aporta recorridos por calles y tiempos estimados. La selección automática aplica compatibilidad, habilitación, distancias y plazos; el proveedor no asigna grúas.

Las reglas completas, tecnologías, persistencia y requisitos del TPI están definidos en el documento consolidado. Los [diagramas C1 y C2 vigentes](../diagramas/AuxiRuta_C1_C2.md) representan únicamente DRIVER, OPERATOR y ADMIN.
