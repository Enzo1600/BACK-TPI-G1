# AuxiRuta — Asistencia y remolque vehicular

> Línea base vigente: **AR-MVP-5**, 4 de noviembre de 2026. Referencia consolidada: [Decisiones arquitectónicas](../DECISIONES_ARQUITECTONICAS.md). Diseño pendiente de implementación.

## Alcance y actores

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

## Microservicios

| Componente | Responsabilidad | Datos propios |
|---|---|---|
| assistance-service | Administrar solicitudes, evaluar viabilidad y recorridos, seleccionar alternativas y coordinar el ciclo del servicio | Vehículos, solicitudes, evaluaciones, propuestas/asignaciones, historial y acciones pendientes hacia Fleet |
| fleet-service | Administrar la capacidad operativa y garantizar exclusividad de recursos | Operadores, habilitación vigente, grúas, vínculo actual grúa/operador, reservas e historial operativo de grúas |
| api-gateway | Enrutar y controlar el acceso externo a las APIs | Configuración de rutas y seguridad; sin base de negocio |
| Keycloak | Administrar identidad, autenticación y emisión de tokens | Usuarios, credenciales, roles, clientes y configuración del realm |
| PostgreSQL | Persistir datos con aislamiento lógico por componente | Tres bases y credenciales independientes |
| OpenRouteService | Calcular recorridos por calles y estimaciones de distancia/duración | Fuente externa; no administra reglas ni asignaciones de AuxiRuta |

La separación responde a dos decisiones distintas: Fleet determina si un recurso puede operar y reservarse; Assistance determina si una solicitud puede atenderse y cómo evoluciona. Fleet no decide el resultado completo de la solicitud y Assistance no modifica directamente sus tablas.

Cada servicio funcional tiene repositorios y modelo propios. La colaboración usa HTTP/JSON. Assistance consume Fleet y OpenRouteService; Fleet no invoca Assistance. Los cambios de Fleet se detectan mediante consulta y reconciliación desde Assistance, con consistencia eventual y sin garantía de detección instantánea.

El Gateway constituye infraestructura y no cuenta como uno de los dos microservicios funcionales. El componente común es una dependencia Maven y no un contenedor ejecutable.

## Colaboración

### 7.1 Solicitud, evaluación y asignación

1. Assistance valida identidad, propiedad, entrada y exclusividad local; persiste REQUESTED con la intención de evaluar.
2. Valida alcance y consulta la ruta origen-destino. Un incumplimiento confirmado termina REJECTED; una falla temporal conserva evaluación pendiente.
3. Consulta candidatos elegibles en Fleet y aplica preselección y evaluación de aproximación.
4. Guarda evaluación, snapshots y el identificador de intento antes de solicitar la reserva.
5. Fleet revalida y retiene operador y grúa en una transacción local.
6. Assistance confirma el resultado de reserva y publica la propuesta consultable en AWAITING_ACCEPTANCE.
7. El operador acepta o rechaza. Assistance solicita confirmación/liberación a Fleet; ASSIGNED solo se registra después de confirmar la aceptación durable.
8. El operador ejecuta la secuencia autorizada. Al terminar, Assistance registra el cierre y solicita liberar los recursos.

Fleet conserva la autoridad sobre disponibilidad y Assistance sobre el resultado de la solicitud. La participación de ambos es necesaria para producir una asignación válida; no se trata de dos catálogos independientes.

### 7.2 Búsqueda alternativa anterior al despacho

Si vence el plazo de despacho o no se confirma elegibilidad para despachar, Assistance cierra la asignación con motivo y conserva firstAssignedAt. Persiste RELEASE, consulta Fleet ante respuestas perdidas y espera confirmación de liberación antes de intentar reservar otra pareja.

Si queda cupo se abre automáticamente la siguiente búsqueda con origen y destino originales; de lo contrario se termina UNFULFILLED. El conductor no recupera el permiso de cancelar. Tras DISPATCHED solo continúa la secuencia de ejecución.

## Integración externa

OpenRouteService aporta distancia y duración de la aproximación y del traslado. La aproximación determina elegibilidad y ranking; el traslado determina admisión y fin planificado, utilizado para validar la habilitación. Sin esa información el sistema no dispone de una base confiable para asignar el servicio.

El cliente recibe coordenadas WGS84 y las adapta al orden [longitud, latitud]. Utiliza Directions driving-car y verifica el ajuste admitido a la red vial. La respuesta debe contener distancia y duración no negativas y los campos necesarios. Respuestas incompletas o inválidas constituyen falla de integración.

La ausencia confirmada de ruta origen-destino rechaza la solicitud en la admisión inicial; una reevaluación de sustitución inviable termina UNFULFILLED. Ausencia de ruta hacia un candidato lo descarta. Timeout, cuota agotada o indisponibilidad no equivalen a ausencia de ruta y no habilitan aprobación a ciegas.

No se presupone tránsito en tiempo real, acceso garantizado de vehículos pesados ni certificación de transitabilidad. El acceso al vehículo y la recepción en destino se asumen posibles dentro del alcance evaluativo. La URL vigente y las cuotas de la cuenta deben verificarse al construir el adaptador. La clave permanece fuera del repositorio y la comunicación con el proveedor utiliza HTTPS.

## Arquitectura

Gateway WebFlux; servicios Spring Boot MVC/JPA; Keycloak; PostgreSQL con tres bases; RestClient y JAR auxiruta-commons. Despliegue con Docker Compose. Los [diagramas vigentes](../diagramas/AuxiRuta_C1_C2.md) representan DRIVER, OPERATOR y ADMIN.
