# DECISIONES ARQUITECTÓNICAS — AUXIRUTA

| Atributo | Valor |
|---|---|
| Sistema | AuxiRuta |
| Versión documental | 2.7 |
| Fecha | 9 de noviembre de 2026 |
| Línea base funcional | AR-MVP-5 |
| Estado | Fases 1 a 10 implementadas; entrega y verificación documentadas |
| Alcance | Backend de asistencia y remolque de vehículos livianos |

## Índice

- [1. Propósito y gobierno del documento](#1-propósito-y-gobierno-del-documento)
- [2. Contexto, alcance y actores](#2-contexto-alcance-y-actores)
- [3. Arquitectura y límites funcionales](#3-arquitectura-y-límites-funcionales)
- [4. Plataforma tecnológica y artefactos](#4-plataforma-tecnológica-y-artefactos)
- [5. Restricciones del dominio AR-MVP-5](#5-restricciones-del-dominio-ar-mvp-5)
- [6. Estados y transiciones](#6-estados-y-transiciones)
- [7. Procesos colaborativos](#7-procesos-colaborativos)
- [8. Integración externa](#8-integración-externa)
- [9. Consistencia, concurrencia y recuperación](#9-consistencia-concurrencia-y-recuperación)
- [10. Identidad, autenticación y autorización](#10-identidad-autenticación-y-autorización)
- [11. Persistencia y modelo de información](#11-persistencia-y-modelo-de-información)
- [12. APIs REST y contratos](#12-apis-rest-y-contratos)
- [13. Componente común reutilizable](#13-componente-común-reutilizable)
- [14. Logging y operación](#14-logging-y-operación)
- [15. Contenerización, configuración y despliegue](#15-contenerización-configuración-y-despliegue)
- [16. Correspondencia con los requisitos mínimos del TPI](#16-correspondencia-con-los-requisitos-mínimos-del-tpi)
- [17. Verificación funcional y criterios de aceptación](#17-verificación-funcional-y-criterios-de-aceptación)
- [18. Glosario](#18-glosario)
- [19. Registro de evolución](#19-registro-de-evolución)

## 1. Propósito y gobierno del documento

Este documento constituye la especificación consolidada del dominio y las decisiones arquitectónicas de AuxiRuta. Define responsabilidades, restricciones, procesos, estados, contratos de operación, persistencia, seguridad y despliegue del producto mínimo viable.

Los términos **debe**, **no debe** y **puede** expresan obligación, prohibición y alternativa admitida, respectivamente. Las condiciones descritas son reglas propias del sistema; no representan certificaciones legales o mecánicas ni garantías comerciales de una empresa operadora real.

Esta especificación es evolutiva. Un cambio funcional o arquitectónico debe registrar su motivo, alcance y consecuencias, actualizar los contratos y diagramas afectados y mantener coherencia con la configuración. Los cambios en restricciones fijas requieren una nueva versión de reglas. Las solicitudes existentes conservan los parámetros y datos con los que fueron evaluadas.

La información de este documento prevalece sobre los documentos parciales cuando existan contradicciones. Los contratos OpenAPI deben reflejar las operaciones aquí definidas y la implementación efectiva. Ninguna discrepancia debe resolverse mediante un cambio silencioso del comportamiento.

### 1.1 Estado de realización

El repositorio dispone de documentación funcional y técnica y diagramas C1/C2 en Mermaid. Las imágenes PNG anteriores se conservan únicamente como antecedentes históricos, no como diagramas vigentes. Los proyectos ejecutables, los contratos OpenAPI y la infraestructura de ejecución todavía deben construirse y verificarse. La descripción de una capacidad expresa una decisión de diseño, no evidencia de su implementación.

## 2. Contexto, alcance y actores

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

## 3. Arquitectura y límites funcionales

### 3.1 Contenedores y responsabilidades

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

### 3.2 Organización interna

Los servicios funcionales aplican la siguiente distribución:

| Capa / paquete | Responsabilidad |
|---|---|
| controllers | Recibir HTTP, validar DTOs, obtener identidad, delegar y construir ResponseEntity |
| services | Aplicar reglas, transiciones, autorización contextual y coordinación |
| repositories | Consultar y persistir mediante Spring Data JPA |
| models | Representar entidades persistentes y valores del dominio |
| dtos | Definir contratos de entrada, salida e integración |
| clients | Encapsular RestClient, serialización y errores remotos |
| jobs | Ejecutar evaluación pendiente, vencimientos y reconciliación |
| config / exceptions | Configuración tipada, seguridad y tratamiento de errores |

Se utiliza inyección por constructor. Los servicios no deben guardar información de la petición actual en atributos mutables compartidos. Las entidades no se administran como beans singleton.

## 4. Plataforma tecnológica y artefactos

| Elemento | Línea base |
|---|---|
| Java | Temurin 21 LTS; parche exacto a registrar al preparar el entorno |
| Maven | 3.9.11, con Maven Wrapper |
| Spring Boot | 3.5.16 |
| Spring Cloud BOM | 2025.0.3 en el Gateway |
| Gateway | spring-cloud-starter-gateway-server-webflux, versión resuelta por el BOM |
| Servicios funcionales | Spring MVC, Spring Data JPA/Hibernate, Spring Security y RestClient |
| OpenAPI / Swagger UI | springdoc-openapi 2.10.0, módulo webmvc-ui |
| Base de datos | PostgreSQL 17.11 |
| Identidad | Keycloak 26.7.5 |
| Docker / Compose | Versiones estables del entorno; pendientes de registrar y verificar |
| Biblioteca común | com.auxiruta:auxiruta-commons:1.0.0 |

La línea Boot 3 mantiene continuidad y compatibilidad con el diseño de dependencias adoptado. Su soporte open source ha finalizado; la elección corresponde al entorno de desarrollo y evaluación definido. Una actualización de generación debe revisar conjuntamente Spring Cloud, starters, seguridad, springdoc y serialización. No se mezclan configuraciones de Boot 3 y Boot 4.

Spring Boot administra las versiones de Spring Framework, Security, Data JPA, Hibernate, Jackson y el driver JDBC. No se agregan versiones independientes sin verificar compatibilidad.

### 4.1 Identificadores y estructura Maven

groupId: `com.auxiruta`. Paquetes base: `com.auxiruta.assistance`, `com.auxiruta.fleet`, `com.auxiruta.gateway` y `com.auxiruta.commons`. Versión inicial de los artefactos propios: `1.0.0`.

Cada aplicación y la biblioteca constituyen proyectos Maven independientes. Se prescinde inicialmente de un agregador multimódulo por simplicidad; esta elección no impide usar un parent o BOM para administrar dependencias.

Los ejecutables producen `target/app.jar` mediante `finalName=app`. La biblioteca produce `auxiruta-commons-1.0.0.jar`, sin main ni plugin de empaquetado Boot ejecutable.

### 4.2 Dependencias por proyecto

Los starters Boot usan groupId `org.springframework.boot`; el de Gateway, `org.springframework.cloud`.

| Proyecto | Dependencias |
|---|---|
| Ambos servicios funcionales | spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-validation, spring-boot-starter-oauth2-resource-server, org.postgresql:postgresql con scope runtime, org.springdoc:springdoc-openapi-starter-webmvc-ui:2.10.0 y auxiruta-commons:1.0.0 |
| Assistance adicionalmente | spring-boot-starter-oauth2-client para obtener credenciales técnicas hacia Fleet |
| Gateway | spring-cloud-starter-gateway-server-webflux y spring-boot-starter-oauth2-resource-server |
| Commons | spring-web, spring-security-core y spring-security-oauth2-jose, según los tipos efectivamente compartidos |

RestClient se obtiene a través de Spring Web. La integración de Keycloak usa estándares OAuth2/OIDC y JWT, sin adaptador específico del proveedor. El Gateway no incorpora MVC, JPA ni lógica bloqueante de asignación.

Las aplicaciones incorporan spring-boot-starter-test y spring-security-test con scope test; sus versiones son administradas por Boot. Lombok y DevTools son opcionales. Actuator, Resilience4j, Flyway/Liquibase, brokers, service discovery, configuración centralizada y stacks de observabilidad quedan fuera de la línea base.

## 5. Restricciones del dominio AR-MVP-5

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
| Antigüedad de ubicación al despachar | Sin límite de antigüedad tras aceptar; se asume permanencia en la posición informada |
| Antigüedad de evaluación de rutas | Hasta 300 s |
| Plazo para despacho desde aceptación | 300 s |
| Ajuste máximo de coordenada a la red vial | 100 m |

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

Las rutas se reutilizan únicamente con iguales coordenadas, perfil y revisión de datos/reglas, dentro de su vigencia. Al emitir una propuesta se exige una evaluación vigente. Esa evaluación se conserva durante toda la ventana de aceptación aunque supere los 300 segundos, bajo el supuesto de posición y condiciones constantes. Aceptar no recalcula rutas ni extiende expiresAt o searchDeadline; Fleet sigue validando recursos y ubicación de hasta 300 segundos. Antes de despachar se revalidan las rutas y se recalculan si vencieron.

### 5.6 Búsqueda, selección y propuestas

El intento inicial comienza en createdAt e incluye evaluación, consultas y propuestas. Rechazos, conflictos o fallos técnicos no extienden su plazo. La ausencia de recursos en una ronda mantiene la búsqueda hasta el vencimiento o agotamiento del cupo de propuestas; no produce rechazo inmediato.

En cada ronda se preseleccionan los candidatos elegibles más cercanos por Haversine, hasta el máximo definido. Entre los que tengan evaluación de ruta válida se ordena por menor presupuesto de llegada, mayor tiempo libre y UUID de grúa lexicográfico. No se garantiza un óptimo global entre toda la flota.

Solo hay una propuesta activa por solicitud. Un operador que rechaza queda excluido de nuevas propuestas para esa solicitud, incluidos los intentos posteriores. Si aceptó y venció su plazo de despacho, puede volver a competir tras liberar el recurso y cumplir nuevamente las condiciones de elegibilidad. Un recurso anteriormente ocupado sin propuesta fallida puede evaluarse al quedar libre.

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

Una vez en DISPATCHED no se reasigna ni se cancela el servicio: el operador registra llegada, carga, traslado y entrega. Se conservan el origen, el destino y los datos declarados.

### 5.9 Ejecución del servicio

El operador asignado declara salida, llegada, carga, inicio de traslado y entrega/descarga, en ese orden. La entrega requiere nota y una asignación vigente; el administrador no puede registrarla en nombre del operador.

Las fechas de salida, llegada, carga, inicio de traslado y entrega se conservan en el historial. No existe un temporizador que complete el servicio: la entrega requiere declaración del operador asignado.

### 5.10 Cancelación y cierres automáticos

El conductor solo puede cancelar antes de que exista una primera confirmación durable de aceptación en Fleet. La solicitud conserva firstAssignedAt de manera irreversible: una búsqueda de reemplazo nunca restituye el permiso de cancelar. Los estados admisibles son REQUESTED, PENDING_EVALUATION, AWAITING_ACCEPTANCE y SEARCHING_ALTERNATIVE, siempre con firstAssignedAt ausente y sin confirmación remota previa.

La carrera entre aceptación y cancelación se serializa en Assistance. Si ya se envió una confirmación a Fleet y su resultado es incierto, la cancelación devuelve 409 ACCEPTANCE_CONFIRMATION_PENDING y primero se reconcilia. Si Fleet confirmó, se conserva ASSIGNED aunque la respuesta original se haya perdido. Si no confirmó, puede procesarse la cancelación. Al admitirla se impiden confirmaciones posteriores y se libera cualquier retención pendiente.

No existen peticiones de cancelación, autorización administrativa de cancelación, corrección de datos durante el servicio, suspensión pendiente de decisión ni cierre excepcional con custodia. UNFULFILLED solo se admite antes del despacho. Después del despacho se conserva la asignación hasta la entrega y liberación.

### 5.11 Validación de entradas

Descripción de solicitud: 10 a 1.000 caracteres. Motivos de rechazo/cancelación y nota de entrega: 10 a 500 caracteres.

La creación exige Idempotency-Key de hasta 100 caracteres, única por conductor y conservada con la solicitud. No se admite estado, propietario o grúa elegida arbitrariamente en la entrada. Una solicitud terminal no se reabre; la creación de otra requiere ausencia de compromisos anteriores inciertos o pendientes sobre el vehículo.

### 5.12 Demostración temporal y pruebas

Se define un perfil `demo` para observar vencimientos en Postman o Swagger con plazos reducidos. Su implementación está disponible desde fase 9. El perfil normal conserva AR-MVP-5 y sus valores; el perfil demo identifica sus snapshots como `AR-MVP-5-DEMO` y se utiliza exclusivamente en demostraciones.

| Parámetro | Normal | Demo |
|---|---|---|
| Intento de búsqueda | 600 s | 60 s |
| Ventana máxima de propuesta | 60 s | 10 s |
| Ventana mínima para ofrecer | 15 s | 3 s |
| Plazo para despacho | 300 s | 20 s |
| Revisión de búsquedas | 30 s | 2 s |

Los cupos de cinco propuestas, cinco candidatos por ronda y dos reasignaciones se mantienen. El resto de los parámetros conserva los valores normales: el perfil reduce plazos de espera, no escala el tiempo global ni los tiempos de viaje, carga o descarga. El perfil debe activarse de manera coherente en ambos servicios; Fleet sigue recibiendo y verificando los deadlines de Assistance. Cada solicitud conserva los parámetros efectivos utilizados, sin alterar solicitudes ya existentes al cambiar el perfil.

Los overrides de referencia están en [Parámetros demo](documentacionPartes/AuxiRuta-parametros-demo.yml). RulesConfiguration aplica estos valores al activar `SPRING_PROFILES_ACTIVE=docker,demo` en Compose; volver a `docker` restablece los valores normales para nuevas solicitudes. Fleet recibe los tiempos del intento en el contrato de reserva. La guía PRUEBAS_JAVA.md documenta los comandos; el YAML de docs es una referencia y no se carga automáticamente.

El código temporal usa un `Clock` inyectado. En ejecución normal y demo se utiliza hora real UTC; en tests se controla el reloj para verificar límites sin esperas reales. La prueba avanza el reloj e invoca explícitamente el procesamiento de vencimientos: cambiar la hora simulada no dispara por sí solo el scheduler. Las pruebas integradas usan hora real y el perfil demo; para reproducir una interrupción o un vencimiento modifican exclusivamente timestamps e intenciones de sus propios fixtures en PostgreSQL. No se modifica la hora del sistema operativo ni se agregan endpoints públicos para manipularla.

## 6. Estados y transiciones

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

## 7. Procesos colaborativos

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

Si vence el plazo de despacho o se confirma una pérdida de elegibilidad antes de despachar, Assistance cierra la asignación con motivo y conserva firstAssignedAt. Persiste RELEASE, consulta Fleet ante respuestas perdidas y espera confirmación de liberación antes de intentar reservar otra pareja.

Si queda cupo se abre automáticamente la siguiente búsqueda con origen y destino originales; de lo contrario se termina UNFULFILLED. El conductor no recupera el permiso de cancelar. Tras DISPATCHED solo continúa la secuencia de ejecución.

## 8. Integración externa

OpenRouteService aporta distancia y duración de la aproximación y del traslado. La aproximación determina elegibilidad y ranking; el traslado determina admisión y fin planificado, utilizado para validar la habilitación. Sin esa información el sistema no dispone de una base confiable para asignar el servicio.

El cliente recibe coordenadas WGS84 y las adapta al orden [longitud, latitud]. Utiliza Directions driving-car y verifica el ajuste admitido a la red vial. La respuesta debe contener distancia y duración no negativas y los campos necesarios. Respuestas incompletas o inválidas constituyen falla de integración.

La ausencia confirmada de ruta origen-destino rechaza la solicitud en la admisión inicial; una reevaluación de sustitución inviable termina UNFULFILLED. Ausencia de ruta hacia un candidato lo descarta. Timeout, cuota agotada o indisponibilidad no equivalen a ausencia de ruta y no habilitan aprobación a ciegas.

No se presupone tránsito en tiempo real, acceso garantizado de vehículos pesados ni certificación de transitabilidad. El acceso al vehículo y la recepción en destino se asumen posibles dentro del alcance evaluativo. La sección 8.1 fija la URL de referencia y el tratamiento de cuotas; la cuenta y la llamada real se verifican al construir el adaptador. La clave permanece fuera del repositorio y la comunicación con el proveedor utiliza HTTPS.

### 8.1 Contrato y límites del proveedor

El adaptador utilizará `ORS_BASE_URL=https://api.heigit.org/openrouteservice/v2` y `POST /directions/driving-car/geojson`, con API key en la cabecera Authorization, coordenadas en orden [longitud, latitud] y respuesta métrica. La geometría y los puntos ajustados se utilizan para comprobar el ajuste máximo a la red vial; no se persiste el JSON remoto completo. La URL anterior api.openrouteservice.org está deprecada.

La referencia pública de Directions indica 2.000 consultas por ventana de 24 horas y 40 por ventana móvil de 60 segundos. Son cuotas del proveedor, no reglas de negocio; se deben contrastar con la cuenta utilizada antes de la primera integración real. La cuota diaria no se presupone reiniciada a medianoche.

Assistance centraliza el presupuesto de llamadas y reutiliza rutas únicamente bajo las condiciones de vigencia definidas. La ruta origen-destino se consulta una vez por evaluación vigente, no una vez por candidato. El perfil demo tampoco acelera las cuotas del proveedor. Ante agotamiento, se difiere la consulta sin extender el plazo de búsqueda; al vencer se aplica el cierre por dependencia, nunca una ruta ficticia.

El adaptador distingue cuota diaria (puede responder 403), límite por minuto (429), credencial inválida y ausencia de ruta mediante el código y contenido documentados del proveedor. Respeta Retry-After y, cuando estén disponibles, las cabeceras de cuota/reset. Un error desconocido permanece como falla técnica, sin inferir ausencia de ruta. Las pruebas automatizadas usan respuestas controladas; la demostración de integración incluye al menos una consulta real.

Referencias verificadas el 4 de noviembre de 2026: [cambio de URL de HeiGIT](https://ask.openrouteservice.org/t/deprecating-api-openrouteservice-org-in-favour-of-api-heigit-org/7912) y [cuotas y códigos de respuesta](https://giscience.github.io/openrouteservice/frequently-asked-questions).

## 9. Consistencia, concurrencia y recuperación

### 9.1 Transacciones locales y exclusividad

Cada base tiene transacciones propias. @Transactional no revierte una llamada HTTP que ya produjo un efecto remoto. Se guardan intención y cambio local, se realiza HTTP fuera de la transacción y se confirma el resultado en una transacción posterior.

Fleet bloquea los recursos en orden fijo, revalida y comprueba ausencia de compromisos antes de reservar. Todos los caminos de reserva, confirmación y liberación utilizan el mismo protocolo. Puede reforzarse con índices únicos parciales para HELD/CONFIRMED por grúa y operador.

Assistance protege la creación de solicitud mediante bloqueo del vehículo y verificación local de solicitudes y limpieza pendiente. Un índice de solicitudes activas no cubre por sí solo compromisos remotos inciertos después de un estado terminal. @Version detecta actualizaciones concurrentes de una fila, pero no dos INSERT de filas distintas.

### 9.2 Idempotencia y tareas pendientes

La clave de creación y la huella de entrada se conservan por conductor. Repetir igual clave/cuerpo devuelve el recurso existente; otro cuerpo con la misma clave produce conflicto. Las transiciones usan claves dentro de su ámbito y resultados durables en entidades/historial.

Las acciones hacia Fleet son RESERVE, CONFIRM y RELEASE. Se persisten con datos explícitos y estado PENDING, COMPLETED o RETRYING. No se implementa un motor genérico de eventos ni un broker.

Una respuesta perdida exige consultar por assignmentAttemptId antes de generar otro intento. Un GET que devuelve 404 no demuestra por sí solo que una reserva incierta nunca se creará. En recuperación se repite el PUT original con el mismo identificador y contenido hasta obtener un resultado durable y, si la solicitud ya cerró, se libera ese mismo intento. Fleet no reactiva un intento RELEASED/EXPIRED ante una repetición tardía del PUT. Esta compensación no publica otra propuesta ni permite confirmar una solicitud cerrada. Los procesos pendientes revalidan la solicitud antes de ejecutar; no confirman una solicitud cancelada. La limpieza de una reserva incierta continúa aunque haya vencido el plazo comercial. No se elimina la intención hasta resolver posibles efectos remotos.

Una solicitud cerrada con liberación pendiente expone resourceReleasePending. Los recursos siguen ocupados y el vehículo no admite nueva solicitud hasta confirmar su liberación. Una acción incierta permanece RETRYING y se reconcilia automáticamente cada 60 s, sin aprobación operativa humana ni descarte silencioso. Una falla permanente de infraestructura o credenciales exige mantenimiento técnico; ningún rol puede aprobar efectos remotos desconocidos.

### 9.3 Tratamiento de fallos

| Parámetro técnico | Valor |
|---|---|
| Timeout de conexión | 2 s |
| Timeout de respuesta | 5 s por llamada |
| Reintentos iniciales de recuperación | A los 5, 15 y 30 s |
| Reintentos posteriores | Cada 60 s mientras haya incertidumbre pendiente |
| Espera por 429 sin Retry-After | 60 s |

Se realiza una llamada por intento y no se repiten escrituras inmediatamente. Toda escritura reintentada debe contar con identificación idempotente. HTTP 429 respeta Retry-After. Un error confirmado de credenciales/permisos requiere corrección técnica y no genera reintentos indiscriminados. En OpenRouteService, 403 también puede significar cuota diaria agotada; se clasifica según la sección 8.1. Los conflictos de dominio se distinguen de fallas de red.

Reconciliación, vencimientos y evaluaciones se ejecutan mediante tareas programadas. Los endpoints aplican también las condiciones temporales; no dependen de que una tarea haya corrido exactamente en el instante de vencimiento. Se contempla una instancia ejecutable por servicio; la coordinación de múltiples réplicas queda fuera del MVP.

## 10. Identidad, autenticación y autorización

### 10.1 Realm, clientes y permisos

Keycloak administra el realm `auxiruta` y exclusivamente los roles humanos DRIVER, OPERATOR y ADMIN. El permiso técnico `FLEET_SERVICE` pertenece exclusivamente a la cuenta de servicio; no constituye un actor adicional ni se asigna a usuarios humanos.

| Cliente | Tipo / flujo | Uso |
|---|---|---|
| auxiruta-api | Público; Authorization Code con PKCE S256 | Acceso humano desde herramientas o interfaces autorizadas |
| assistance-service | Confidencial; client_credentials | Llamadas internas y tareas programadas hacia Fleet |

Se definen redirect URIs concretas. El login y recuperación de contraseña pertenecen a Keycloak. No se almacenan contraseñas en bases funcionales ni se implementa autenticación artesanal. La administración de Keycloak requiere permisos propios, independientes del rol ADMIN del negocio.

Los tokens humanos tienen audiencia `auxiruta-api`; los técnicos, `auxiruta-fleet`. Los mappers correspondientes se configuran en el realm. Gateway y Assistance validan la audiencia humana. Fleet admite la audiencia correspondiente al grupo de rutas y verifica además rol/cliente: los endpoints internos requieren permiso técnico y el cliente Assistance esperado.

### 10.2 Distribución de controles

Gateway y ambos servicios validan JWT: firma, emisor, vigencia y audiencia. Los roles de realm_access.roles se convierten a ROLE_*. La identidad se obtiene del subject validado, nunca de cabeceras de identidad arbitrarias o campos de entrada.

Gateway aplica el Security Filter según método, ruta y rol; bloquea interfaces internas y rutas no previstas. Cada servicio repite autorización y controla propiedad/asignación y estado en la capa funcional. La red interna de Docker y la validación propia delimitan la zona de confianza.

Las peticiones humanas conservan su bearer token desde Gateway hasta el servicio. Assistance obtiene un token técnico independiente mediante el registro OAuth2 `fleet-client` para invocar Fleet. Esto permite el mismo mecanismo en operaciones de usuario y tareas sin sesión humana.

Los servicios MVC usan SecurityFilterChain/HttpSecurity y el Gateway WebFlux usa SecurityWebFilterChain/ServerHttpSecurity. El código reutilizable de roles no debe imponer infraestructura servlet al Gateway reactivo.

### 10.3 Emisor y claves en Docker

Para el despliegue local se definen las siguientes ubicaciones:

| Uso | Dirección |
|---|---|
| URL pública de Keycloak | http://localhost:8080 |
| Emisor esperado | http://localhost:8080/realms/auxiruta |
| Claves desde contenedores | http://keycloak:8080/realms/auxiruta/protocol/openid-connect/certs |
| Tokens del cliente técnico | http://keycloak:8080/realms/auxiruta/protocol/openid-connect/token |

Keycloak configura hostname público y backchannel dinámico. Los resource servers declaran issuer-uri y jwk-set-uri para mantener validación de emisor y obtener claves por la red interna. La firma se valida con claves públicas cacheadas; no se requiere consultar Keycloak en cada petición.

Un JwtDecoder personalizado debe conservar explícitamente los validadores. Si la configuración de red falla, se corrige sin eliminar validación de issuer. La exposición fuera del equipo local requiere cambiar coherentemente URL pública, TLS y redirecciones. Esta configuración debe comprobarse al implementar.

## 11. Persistencia y modelo de información

### 11.1 Separación de datos

Un único contenedor PostgreSQL mantiene `assistance_db`, `fleet_db` y `keycloak_db`, con usuarios `assistance_user`, `fleet_user` y `keycloak_user`. Cada componente utiliza solo su base y privilegios necesarios; la inicialización emplea credenciales separadas. Deben revocarse accesos públicos innecesarios.

Las referencias a otro servicio o a Keycloak son identificadores simples, sin FK entre bases ni consultas cruzadas. El modelo de Keycloak es administrado por el proveedor.

### 11.2 Entidades funcionales

La siguiente tabla define los registros lógicos; los tipos, longitudes y restricciones finales deben materializarse en JPA y SQL de forma coherente.

| Servicio | Entidad / tabla | Información persistente |
|---|---|---|
| Assistance | Vehicle / vehicles | ID, propietario subject, patente normalizada, marca/modelo, tipo, peso, estado de registro, fechas y versión |
| Assistance | AssistanceRequest / assistance_requests | Vehículo, conductor, estado, origen y destino originales, datos declarados inmutables, firstAssignedAt, intento/plazos, versión y snapshot de reglas, idempotencia/huella y motivo de cierre |
| Assistance | Assignment / assignments | Intento único, solicitud, candidato evaluado, IDs externos de recursos/reserva, subject del operador, estado, reserva/propuesta/aceptación/despacho y cierre |
| Assistance | Evaluation / evaluations | Solicitud/intento/revisión, datos del vehículo, coordenadas usadas, parámetros efectivos, resultado/motivos y fecha de evaluación/obtención de ruta |
| Assistance | CandidateEvaluation / candidate_evaluations | Recursos externos, capacidad/compatibilidades, habilitación usada, ubicación/fecha, resultados de cobertura/elegibilidad, rutas crudas y ajustadas, ranking y fin estimado |
| Assistance | RequestStatusHistory / request_status_history | Solicitud, estado anterior/nuevo, actor, fecha, motivo, detalles, claves de operación y cambios de revisión/intento |
| Assistance | PendingFleetAction / pending_fleet_actions | Operación idempotente, solicitud/asignación, tipo, datos explícitos de recursos y snapshot necesario, estado, reintentos, próximos intentos y resultado/error |
| Fleet | Operator / operators | Subject único, nombre/contacto, estados de registro/disponibilidad, habilitación vigente, tiempo libre, fechas y versión |
| Fleet | TowTruck / tow_trucks | Patente única, operador actual único/opcional, capacidad, compatibilidad de movilidad, estado operativo, coordenadas y fecha/versión |
| Fleet | ResourceReservation / resource_reservations | Intento único, solicitud externa, recursos locales, estado, creación/vencimiento/confirmación/liberación, deadline recibido, fin estimado, huella y versión |
| Fleet | TruckStatusHistory / truck_status_history | Equipo, estado anterior/nuevo, motivo, actor, fecha y clave de operación administrativa |
| Fleet | tow_truck_supported_types | Colección de tipos admitidos por grúa, con unicidad por equipo/tipo |

Los datos vigentes de habilitación y vínculo se guardan en Operator y TowTruck; las reservas/evaluaciones conservan los datos históricos usados. Un historial administrativo completo de renovaciones o vínculos no forma parte del MVP. Los cambios administrativos relevantes se registran en logs.

Los snapshots no requieren una entidad adicional: los datos evaluados son columnas de solicitud/evaluación/candidato y los parámetros efectivos se serializan en una columna TEXT de la solicitud, según el modelo persistente. Las reglas no requieren tablas de configuración versionada. Tampoco se crean tablas para cada enum, DTO o rol humano.

### 11.3 Mapeo y restricciones

Se utilizan Jakarta Persistence, constructor sin argumentos, @Entity, @Id, @Column y enums STRING. UUID identifica registros; BigDecimal representa pesos/coordenadas e Instant/TIMESTAMPTZ los instantes. Las relaciones locales parten preferentemente de N hacia 1 con LAZY; los listados e historiales se consultan y paginan, sin colecciones bidireccionales innecesarias.

Los repositorios extienden JpaRepository. Las consultas usan métodos derivados o JPQL parametrizado; SQL específico se reserva para restricciones o necesidades justificadas. No se serializan entidades directamente ni se propaga borrado hacia auditoría mediante cascade REMOVE.

Se aplican NOT NULL, FK locales, unicidad de patentes/subjects/intentos/claves y CHECK de pesos, coordenadas y fechas. Los índices cubren consultas por propietario/estado/fecha, operador/estado, historial por solicitud, reservas por vencimiento y acciones por próximo intento. Las reglas de exclusividad y transiciones válidas por asignación se protegen mediante control transaccional sobre la solicitud o recursos.

### 11.4 Esquema y evolución

Se entregan scripts SQL controlados y `spring.jpa.hibernate.ddl-auto=validate`. create-drop se limita a entornos descartables. Los scripts de inicialización de PostgreSQL actúan sobre volumen vacío; la actualización de bases pobladas requiere un procedimiento SQL explícito. No se incorpora Flyway como dependencia obligatoria.

Cada servicio utiliza su DataSource/pool administrado por Spring. No comparte Connection como singleton ni administra manualmente un EntityManager por petición. H2 puede utilizarse en pruebas aisladas; la ejecución integral y comprobación de concurrencia se realizan sobre PostgreSQL.

## 12. APIs REST y contratos

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
| 404 | Recurso inexistente o fuera del ámbito autorizado: mismo código y mensaje genérico |
| 409 | Estado, concurrencia, exclusividad, duplicado o idempotencia incompatibles |
| 500 | Fallo inesperado, con mensaje público controlado |
| 503 | Dependencia indisponible cuando la acción no pudo aceptarse ni registrarse para recuperación |

Los errores usan ProblemDetail y código funcional estable. Un resultado REJECTED/UNFULFILLED de una solicitud creada se consulta con éxito HTTP; el resultado del negocio no es un fallo del protocolo. No se devuelve un error que sugiera ausencia de efectos si ya existe una acción durable pendiente.

Los contratos OpenAPI de ambos servicios deben incluir esquemas, parámetros, roles, ejemplos, respuestas y errores; springdoc publica especificación y Swagger UI. El Gateway enruta la documentación por servicio. La especificación se obtiene con token y puede cargarse en Swagger UI local; no se presupone que una navegación del navegador adjunte automáticamente un bearer token. Los controladores internos se excluyen de la especificación pública. La publicación de una interfaz Swagger con login integrado se verificará al construirla, sin abrir las APIs de negocio. Las URLs documentadas deben corresponder al acceso externo real.

## 13. Componente común reutilizable

`auxiruta-commons` concentra el formato de error y utilidades transversales efectivamente reutilizadas: extracción de roles de Keycloak, representación común de errores y acceso a identidad cuando sea compatible con el entorno consumidor. Los adaptadores servlet/reactivos y las autorizaciones específicas permanecen en cada aplicación.

La biblioteca se construye como artefacto Maven independiente y se instala en `.m2`; ambos servicios funcionales declaran su dependencia. Copiar archivos fuente entre proyectos no constituye consumo del componente.

No contiene entidades JPA, repositorios, DTOs de negocio ni reglas de asignación o capacidad. Esta frontera permite que cada servicio conserve su modelo y contrato y evita que un cambio funcional obligue a evolucionar todos los proyectos simultáneamente.

## 14. Logging y operación

Se incluyen logs básicos con SLF4J/Logback, versiones administradas por Boot y salida a consola recogida por Docker. No se crea una base, tabla ni API de logs. El formato contiene instante, nivel, servicio, clase y mensaje; se añaden requestId, assignmentId y reservationId cuando existan.

| Aplicación | Eventos |
|---|---|
| Gateway | Método, ruta normalizada, código, duración y fallos de acceso/enrutamiento |
| Assistance | Creación, decisiones de evaluación, propuestas, transiciones, cancelaciones, reasignación y recuperación |
| Fleet | Reservas, confirmaciones, liberaciones, vencimientos, conflictos, cambios de equipo y administración relevante |
| Cliente de rutas | Operación, resultado o tipo de error y duración, sin URL/cuerpo sensible |

INFO registra eventos relevantes; WARN, fallos recuperables y situaciones operativas excepcionales; ERROR, fallos inesperados o acciones que requieren mantenimiento técnico; DEBUG, diagnóstico. No se registra una excepción repetidamente en cada capa ni una consulta periódica sin cambios como evento importante.

Los mensajes de éxito se emiten después del resultado confirmado. Una respuesta perdida se registra como incierta hasta recuperarla. No se registran tokens, secretos, contraseñas, datos personales completos, coordenadas exactas, textos libres ni DTOs/cuerpos enteros automáticamente. Se preserva la causa técnica en el tratamiento autorizado de errores y se limita el mensaje externo.

Docker utiliza retención básica `json-file`, `max-size=10m`, `max-file=3`. La consulta se realiza mediante `docker compose logs`. La auditoría funcional persiste en PostgreSQL y no depende de conservar los logs.

Actuator, métricas, alertas, paneles, plataformas externas y correlación distribuida automática permanecen opcionales. Los IDs de negocio permiten diagnóstico básico sin comprometer tracing adicional.

## 15. Contenerización, configuración y despliegue

### 15.1 Topología

| Contenedor | Puerto interno | Exposición al host local |
|---|---|---|
| api-gateway | 8000 | 8000 |
| keycloak | 8080 | 8080 para autenticación y administración |
| fleet-service | 8081 | Sin publicación |
| assistance-service | 8082 | Sin publicación |
| postgres | 5432 | Sin publicación |

Compose define red compartida, volúmenes, variables, dependencias y construcción de imágenes. Los servicios se resuelven por DNS de Compose. `localhost` dentro de un contenedor no identifica otro servicio. Assistance requiere salida a Internet para rutas.

Cada ejecutable dispone de Dockerfile. La construcción debe resolver el JAR común antes de compilar servicios: una etapa Maven puede instalar commons en su propio `.m2` y luego empaquetar la aplicación. No se presupone que `.m2` del host exista dentro de un build aislado. La imagen final contiene el JAR ejecutable y un entorno Java compatible.

PostgreSQL mantiene un volumen persistente en la ubicación correspondiente a la versión elegida. Keycloak usa su base separada y configuración inicial de realm/clientes/roles. Las imágenes se fijan por versión; `.dockerignore` evita incluir archivos innecesarios o secretos.

Las condiciones de arranque y healthchecks deben verificar disponibilidad real sin imponer Actuator. `depends_on` por sí solo no significa servicio listo. Las aplicaciones manejan fallos también después del arranque. Se documentan inicio, parada, conservación de datos y actualización de esquema sin borrar volúmenes inadvertidamente.

### 15.2 Configuración externa

| Variables | Consumidor y finalidad |
|---|---|
| SERVER_PORT | Puerto de cada ejecutable |
| DB_URL, DB_USER, DB_PASSWORD | Base funcional correspondiente |
| KEYCLOAK_ISSUER_URI, KEYCLOAK_JWK_SET_URI | Gateway y servicios: validación del emisor y claves |
| KEYCLOAK_TOKEN_URI, KEYCLOAK_CLIENT_ID, KEYCLOAK_CLIENT_SECRET | Assistance: cliente técnico |
| FLEET_URL | Assistance: dirección interna de Fleet |
| FLEET_URI, ASSISTANCE_URI | Gateway: destinos de enrutamiento |
| ORS_BASE_URL, ORS_API_KEY | Assistance: proveedor de rutas y credencial |
| POSTGRES_ADMIN_PASSWORD | Inicialización del motor, separada de las aplicaciones |
| ASSISTANCE_DB_PASSWORD, FLEET_DB_PASSWORD, KEYCLOAK_DB_PASSWORD | Contraseñas independientes por base |
| KC_DB, KC_DB_URL, KC_DB_USERNAME, KC_DB_PASSWORD | Configuración de persistencia de Keycloak |
| SPRING_PROFILES_ACTIVE, AUXIRUTA_AUTOMATION_ENABLED | Perfil normal/demo y habilitación técnica del scheduler; automatización activa por defecto |
| KC_HOSTNAME, KC_HOSTNAME_BACKCHANNEL_DYNAMIC | Dirección pública y acceso interno de Keycloak |

En Compose, las URLs funcionales de base son `jdbc:postgresql://postgres:5432/assistance_db` y `jdbc:postgresql://postgres:5432/fleet_db`. La de Keycloak corresponde a `keycloak_db`; Fleet se accede mediante `http://fleet-service:8081` y Assistance mediante `http://assistance-service:8082`.

`.env.example` contiene nombres y valores ficticios; `.env` no se versiona. Los secretos requeridos no tienen contraseñas reales de respaldo en código. Las credenciales iniciales de administración de Keycloak se inyectan conforme a la versión elegida.

Los parámetros de negocio se cargan mediante RulesConfiguration desde el recurso rules/mvp.yml y los overrides del perfil demo; cada solicitud conserva una copia inmutable de sus valores efectivos. El archivo de referencia [AuxiRuta-parametros-MVP.yml](documentacionPartes/AuxiRuta-parametros-MVP.yml) debe coincidir con la sección 5 y distribuirse/importarse en la configuración efectiva; almacenarlo en docs no lo carga automáticamente.

La entrega ejecutable y la evidencia de verificación se documentan en [ENTREGA.md](ENTREGA.md) y [cambios.md](cambios.md). Los contratos públicos exportados y la colección se encuentran en docs/api; el contrato técnico privado se documenta en INTEGRACION_INTERNA.md.

### 15.3 Organización documental y técnica

```text
BACK-TPI-G1/
├── README.md
├── docs/
│   ├── DECISIONES_ARQUITECTONICAS.md
│   ├── documentacionPartes/
│   ├── diagramas/
│   │   ├── AuxiRuta_C1_C2.md          [vigente; Mermaid]
│   │   ├── AuxiRuta_DER.md            [vigente; Mermaid]
│   │   ├── bd1.png / bd2.png          [antecedentes]
│   │   ├── AuxiRuta_C1.png            [histórico]
│   │   └── AuxiRuta_C2.png            [histórico]
│   └── api/                         [contratos y colección implementados]
├── auxiruta-commons/                [implementado]
├── fleet-service/                  [implementado]
├── assistance-service/             [implementado]
├── api-gateway/                    [implementado]
├── infra/                          [implementado]
├── docker-compose.yml              [implementado]
└── .env.example                    [implementado]
```

El C1 representa actores, sistema e integraciones externas; el C2, contenedores, tecnologías y comunicaciones. La fuente vigente es [C1 y C2](diagramas/AuxiRuta_C1_C2.md); los PNG anteriores son históricos y no describen AR-MVP-5. Los diagramas deben verificarse contra el despliegue final. La biblioteca se representa como dependencia, no contenedor. Los scripts shell se guardan con LF y los archivos de compilación/secretos se excluyen de Git.

## 16. Correspondencia con los requisitos mínimos del TPI

Esta matriz relaciona cada requisito con la solución diseñada y la evidencia necesaria. Mientras no exista la evidencia, el requisito se considera **contemplado en el diseño y pendiente de verificación**, no cumplido por la sola presencia de este documento.

| Requisito | Ubicación y mecanismo de cumplimiento | Evidencia de implementación requerida |
|---|---|---|
| Dos o más microservicios funcionales Java/Spring Boot con responsabilidades coherentes | Secciones 3 y 4: Assistance administra solicitudes/viabilidad y Fleet capacidad/disponibilidad; proyectos y datos separados | Ambos proyectos compilables, ejecutables y con reglas propias verificables |
| Colaboración real entre microservicios | Secciones 7 y 9: evaluar, reservar, confirmar y liberar requiere Assistance y Fleet; ningún servicio puede completar por sí solo una asignación válida | Recorrido integral y casos de conflicto, respuesta perdida e indisponibilidad |
| API Gateway y Security Filter | Secciones 10, 12 y 15: Gateway WebFlux como entrada, rutas por servicio, validación JWT y control por método/rol; interfaces internas bloqueadas | Peticiones por Gateway, accesos denegados y ausencia de acceso público directo a microservicios |
| API pública externa necesaria para el negocio | Secciones 5.5 y 8: OpenRouteService determina aproximación, admisión, ranking y vigencia de habilitación | Consulta real con credencial externa; resultado que cambie una decisión y manejo de fallo sin datos inventados |
| Identidad, autenticación y autorización | Sección 10: Keycloak/OIDC, access tokens JWT, Spring Security, roles, pertenencia, asignación y cliente técnico | Realm/clientes configurados; token válido, vencido, emisor/audiencia incorrectos y acceso ajeno probados |
| Componente Java reutilizable independiente | Sección 13: auxiruta-commons instalado en `.m2` y consumido por ambos servicios; sin copiar fuentes | JAR construido, instalación Maven y dependencias declaradas en ambos pom.xml |
| Persistencia | Sección 11: PostgreSQL, entidades JPA, repositorios, restricciones y bases separadas | DDL válido, datos conservados tras reinicio, integridad y exclusividad concurrente comprobadas |
| APIs REST y documentación | Sección 12: métodos/códigos HTTP coherentes, JSON, validación, errores y OpenAPI/Swagger | Contratos completos de ambos servicios, Swagger accesible y coherencia con respuestas reales |
| Contenerización y despliegue integral | Sección 15: imágenes de ejecutables, Compose, PostgreSQL, Keycloak, red, volúmenes y configuración externa | Inicio reproducible del sistema, importación inicial, comunicación interna, salida al proveedor y recuperación tras reinicio |
| Diagramas C4 | Sección 15.3: C1 y C2 alineados con responsabilidades, integraciones y contenedores efectivos | Diagramas revisados contra la implementación entregada |

El logging básico es una decisión adicional del sistema. No sustituye ninguno de los requisitos ni convierte plataformas de monitoreo en requisitos mínimos.

## 17. Verificación funcional y criterios de aceptación

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

Se implementarán pruebas automatizadas significativas para límites, transiciones, autorización, idempotencia y exclusividad, además de la colección de demostración integral. Las pruebas temporales utilizarán Clock inyectado. No se exige un porcentaje arbitrario de cobertura ni se presume una suite existente. Los criterios deben mantenerse sincronizados con cada cambio de reglas.

### 17.1 Evidencia observable del MVP

Las rutas de negocio de esta tabla llevan prefijo `/api/v1`. Los casos de límites y fallos usan datos controlados; el recorrido integral incluye consumo real del proveedor. Cada evidencia se registra al ejecutarse, sin marcarla aprobada anticipadamente.

| ID | Condición inicial | Acción | Resultado esperado y referencia |
|---|---|---|---|
| CV-01 | Entorno configurado y volúmenes vacíos | Construir y arrancar Compose; crear datos y reiniciar conservando volúmenes | Aplicaciones accesibles por Gateway, tres bases aisladas y datos conservados; sección 15 |
| CV-02 | Vehículo propio y pareja grúa/operador elegible | Crear solicitud; aceptar propuesta; registrar dispatch, arrival, loading, transport-start y completion | COMPLETED, historial ordenado y reserva RELEASED con recursos libres; secciones 5.5, 5.7 y 5.9, endpoints de solicitud y asignación |
| CV-03 | Dos conductores y un operador con tokens válidos | Consultar vehículo propio, ajeno e inexistente; repetir sin token y usar una operación ADMIN con rol DRIVER | 200 para propio, mismo 404 para ajeno/inexistente, 401 sin token y 403 por rol; GET /vehicles/{id}, administración Fleet y sección 10 |
| CV-04 | Solicitud válida salvo un límite controlado | Evaluar puntos en el radio y fuera de él; rutas válidas y fuera de distancia; capacidad suficiente e insuficiente | Igualdades inclusivas aceptadas, incumplimiento de admisión REJECTED y candidato incompatible descartado; secciones 5.1–5.5 |
| CV-05 | Solicitud sin aceptación y otra con firstAssignedAt registrado | Cancelar la primera; intentar cancelar la segunda, incluso durante reemplazo | Primera CANCELLED con liberación recuperable; segunda 409 y sin modificación; POST /assistance-requests/{id}/cancellations y sección 5.10 |
| CV-06 | Clock controlado o perfil demo, propuesta y asignación pendientes | Alcanzar vencimiento de oferta, búsqueda y despacho; agotar dos reasignaciones | No se acepta exactamente en el límite; alternativa solo previa al despacho y UNFULFILLED al agotar límites; secciones 5.6–5.8 y 5.12 |
| CV-07 | Dos solicitudes compiten por la misma pareja libre | Reservar concurrentemente; repetir creación con igual clave/cuerpo y luego con cuerpo distinto | Como máximo una reserva activa; reintento idéntico sin duplicación y conflicto para contenido distinto; PUT interno de reserva, POST de solicitud y sección 9.1 |
| CV-08 | Operación remota con respuesta perdida o proveedor indisponible | Perder respuesta de confirmación/liberación, reiniciar y reconciliar; simular timeout y cuota del proveedor | Se conserva aceptación durable, no se duplica reserva, se completa liberación y no se inventa ruta; historial/logs permiten comprobarlo sin secretos; secciones 8, 9 y 14 |

## 18. Glosario

| Concepto | Definición en AuxiRuta |
|---|---|
| Actor | Tipo de participante con responsabilidades dentro del proceso |
| API | Contrato mediante el cual una aplicación utiliza capacidades de otra |
| API Gateway | Punto de entrada que enruta peticiones y aplica controles transversales |
| API pública externa | Servicio accesible por Internet que aporta información ajena al sistema |
| Aproximación | Recorrido desde la ubicación de la grúa hasta el vehículo |
| Asignación | Propuesta y posterior vinculación de recursos con una solicitud, registrada por Assistance |
| Autenticación | Comprobación de identidad del solicitante de una operación |
| Autorización | Comprobación de permiso por rol, propiedad, asignación y contexto |
| Audience / aud | Receptor o conjunto de receptores para los que se emitió un token |
| Auditoría funcional | Historial persistente de estados, actores y decisiones del negocio |
| Backend | Conjunto de servicios que ejecutan las reglas y exponen las APIs |
| Bean | Objeto cuya creación y dependencias administra el contenedor de Spring |
| BOM | Conjunto administrado de versiones Maven compatibles |
| Client credentials | Flujo OAuth2 para que Assistance opere con identidad técnica propia |
| Concurrencia | Ejecución simultánea de operaciones que pueden competir por datos o recursos |
| Consistencia eventual | Sincronización progresiva entre servicios mediante confirmación y recuperación |
| Contenedor | Instancia aislada de ejecución construida desde una imagen |
| C4, C1 y C2 | Modelo de vistas arquitectónicas; contexto del sistema y contenedores, respectivamente |
| Deadline | Instante límite, evaluado por el servidor, para una operación |
| DBMS | Motor que administra bases de datos, transacciones e integridad |
| Docker Compose | Definición declarativa del despliegue multicontenedor, red y volúmenes |
| DTO | Representación de entrada/salida o integración, separada de las entidades persistentes |
| Elegibilidad | Resultado de comprobar que recurso y operador cumplen condiciones para una operación |
| Endpoint | Combinación de método HTTP y ruta que expone una operación |
| Entidad JPA | Objeto del dominio mapeado a información persistente mediante Jakarta Persistence |
| Estado operativo | Aptitud de la grúa para trabajar, independiente de su ocupación |
| FK | Restricción que referencia un registro dentro de la misma base de datos |
| Habilitación interna | Registro operativo de aptitud y vigencia verificado manualmente por administración |
| Haversine | Cálculo geográfico utilizado para pertenencia al círculo y preselección |
| Hibernate | Implementación ORM que materializa operaciones JPA sobre la base |
| Idempotencia | Repetición controlada de una operación sin duplicar sus efectos |
| Identity Provider / IdP | Proveedor de identidad; Keycloak en esta arquitectura |
| Inyección de dependencias | Provisión externa de colaboradores de una clase; se aplica mediante constructores |
| Issuer / iss | Emisor esperado de un token, verificado exactamente al validarlo |
| JAR común | Biblioteca Java empaquetada y consumida como dependencia Maven |
| JPA | Especificación de persistencia objeto-relacional utilizada en los servicios |
| JPQL | Lenguaje de consulta sobre entidades y relaciones JPA |
| JWT | Token firmado que contiene identidad y claims verificables |
| Logging | Registro técnico de eventos y errores para diagnóstico |
| Microservicio funcional | Aplicación autónoma con responsabilidad de negocio y datos propios |
| Monitoreo | Observación de salud y métricas de ejecución; opcional en el MVP |
| MVP | Alcance mínimo completo que permite ejecutar y demostrar el proceso central |
| MVC | Modelo de procesamiento web utilizado por los servicios funcionales mediante Spring MVC |
| OAuth2 | Marco de autorización basado en permisos delegados y tokens de acceso |
| Ocupación | Situación de recurso libre, retenido temporalmente o asignado |
| OIDC | Capa de identidad sobre OAuth2 utilizada en la autenticación |
| OpenAPI / Swagger UI | Especificación del contrato REST e interfaz para explorarlo/probarlo |
| ORM | Mapeo entre objetos de aplicación y estructuras relacionales |
| PKCE | Protección del intercambio de código de autorización para clientes públicos |
| ProblemDetail | Representación estructurada y controlada de un error HTTP |
| Realm | Ámbito de usuarios, roles y clientes administrado por Keycloak |
| Reconciliación | Consulta y corrección de resultados locales/remotos pendientes o divergentes |
| Reserva | Retención exclusiva de grúa y operador administrada por Fleet |
| Repositorio | Interfaz de acceso a datos; en Maven, también designa el almacenamiento de artefactos como `.m2` |
| RestClient | Cliente HTTP utilizado por Assistance para integrar Fleet y el proveedor externo |
| Resource server | Aplicación que protege recursos y valida tokens de acceso |
| REST | Estilo de API basado en recursos, representaciones y semántica HTTP |
| Rol | Conjunto de permisos asignado a una identidad |
| Snapshot | Copia de datos y parámetros usados para una decisión, conservada para auditoría |
| Solicitud | Necesidad de traslado administrada durante todo su ciclo por Assistance |
| Spring Boot | Plataforma de configuración y ejecución de las aplicaciones Java del sistema |
| Subject / sub | Identificador de la identidad representada por un token |
| Primera asignación | Primera aceptación durable en Fleet; impide definitivamente cancelar por el conductor |
| Retiro vigente | Origen original, conservado durante toda la solicitud |
| Timeout | Límite de espera de una operación técnica remota |
| Transacción local | Conjunto atómico de modificaciones dentro de una base de datos |
| Traslado | Recorrido del vehículo desde su origen hasta el destino acordado |
| UUID | Identificador utilizado para registros e intentos de operación |
| Volumen | Almacenamiento persistente independiente del ciclo de vida del contenedor |
| WebFlux | Infraestructura web reactiva utilizada por el Gateway, separada de los servicios MVC |
| Zona de confianza | Ámbito de comunicaciones internas con controles de identidad y permisos explícitos |

## 19. Registro de evolución

| Versión | Fecha | Alcance |
|---|---|---|
| 1.0 | 2026-11-03 | Línea base histórica AR-MVP-1: consolidación inicial |
| 2.0 | 2026-11-04 | AR-MVP-2: tres roles humanos, cancelación solo previa a primera aceptación, supuestos evaluativos, sustitución automática por avería incluso con carga y cierres sin evaluación humana; actualización de secciones 2–3, 5–12 y 15–18, parámetros, entidades, contratos y diagramas |
| 2.1 | 2026-11-04 | AR-MVP-3: se excluyen averías durante IN_TRANSIT; sustituciones solo antes del traslado, origen inmutable, eliminación del retiro alternativo y actualización de estados, contratos, persistencia, parámetros y casos de aceptación |
| 2.2 | 2026-11-04 | AR-MVP-4: simplificación del flujo operativo, ejecución continua desde DISPATCHED, búsquedas alternativas solo anteriores al despacho, alta administrada de operadores y sincronización integral del alcance |
| 2.3 | 2026-11-04 | AR-MVP-5: eliminación de la detección automática de demoras, sus registros, endpoints, entidad y parámetros; conservación del historial de etapas con sus fechas |
| 2.4 | 2026-11-04 | Perfil demo con plazos reducidos y pruebas con Clock; conserva las reglas y cupos de AR-MVP-5 para ejecución normal |
| 2.5 | 2026-11-04 | Revisión de preparación: modelo persistente consolidado y DER editable, precisión de permisos y fallos de integración, cuotas/URL de rutas, pruebas y secuencia de construcción; sin cambio de reglas AR-MVP-5 |
| 2.6 | 2026-11-08 | Estado de construcción actualizado hasta fase 9; activación real del perfil demo, recuperación persistente y configuración. Se conserva AR-MVP-5 sin cambiar límites normales ni alcance |
| 2.7 | 2026-11-09 | Entrega de fase 10: colección, contratos públicos y privados, preparación de datos y verificación Java. Estado de construcción actualizado sin cambiar AR-MVP-5; guía operativa en ENTREGA.md |

Toda revisión posterior debe agregar una entrada y precisar las secciones afectadas. Antes de declarar una capacidad implementada se deben registrar su evidencia y verificación. Las versiones exactas del entorno Java/Docker, la configuración efectiva de Keycloak, los contratos OpenAPI, las restricciones SQL y la verificación de las cuotas de la cuenta se completarán durante la construcción sin modificar silenciosamente esta línea base.

## Ajuste de ubicación acordado el 9 de noviembre de 2026

La ubicación informada se considera fija hasta la salida. Su vigencia de 300 segundos, inclusiva, se comprueba al seleccionar, reservar y confirmar una propuesta. Superado ese plazo, el operador debe volver a enviar coordenadas para recibir o aceptar propuestas. Una asignación ya aceptada conserva esa posición sin caducidad por antigüedad: no se exige actualizar para despachar ni se libera por ese motivo. Permanecen vigentes la comprobación de rutas, la habilitación y el plazo independiente de 300 segundos para declarar el despacho. Este ajuste sustituye la restricción previa de ubicación de 120 segundos; no elimina los controles de ubicación para una futura solicitud.
## Ubicación bloqueada durante propuesta o servicio — 9 de noviembre de 2026

El operador puede informar coordenadas mientras su grúa esté libre. Desde que Fleet crea una retención HELD, y durante el compromiso CONFIRMED, la actualización devuelve 409 sin modificar coordenadas ni locationUpdatedAt, incluso si se envían las mismas coordenadas. Se asume permanencia en la posición informada hasta el despacho. El bloqueo comparte el bloqueo transaccional de grúa con la reserva para evitar una carrera entre ofrecer y actualizar.

Si el operador rechaza una propuesta, o esta vence o el conductor cancela antes de aceptación, se vuelve a permitir informar ubicación cuando Fleet confirma la liberación o materializa la expiración. Mientras la liberación sea incierta no se permite actualizar. El operador rechaza propuestas: no se incorpora una cancelación operativa posterior a aceptar. Una grúa libre puede actualizar su ubicación aunque todavía tenga vigencia; superar 300 segundos exige actualizarla para nuevas propuestas y confirmaciones.

Este ajuste elimina el cambio de coordenadas entre propuesta y aceptación identificado en H3. No demuestra por sí mismo la vigencia temporal de las rutas cacheadas: ese borde de la auditoría sigue pendiente de verificación específica. H1 continúa pendiente.