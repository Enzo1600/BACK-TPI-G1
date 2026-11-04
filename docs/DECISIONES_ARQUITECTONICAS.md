# DECISIONES ARQUITECTÓNICAS — AUXIRUTA

| Atributo | Valor |
|---|---|
| Sistema | AuxiRuta |
| Versión documental | 1.0 |
| Fecha | 3 de noviembre de 2026 |
| Línea base funcional | AR-MVP-1 |
| Estado | Diseño definido; implementación pendiente |
| Alcance | Backend de asistencia y remolque de vehículos livianos |

## Índice

- [1. Propósito y gobierno del documento](#1-propósito-y-gobierno-del-documento)
- [2. Contexto, alcance y actores](#2-contexto-alcance-y-actores)
- [3. Arquitectura y límites funcionales](#3-arquitectura-y-límites-funcionales)
- [4. Plataforma tecnológica y artefactos](#4-plataforma-tecnológica-y-artefactos)
- [5. Restricciones del dominio AR-MVP-1](#5-restricciones-del-dominio-ar-mvp-1)
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

El repositorio dispone de documentación funcional y técnica e imágenes C1 y C2. Los proyectos ejecutables, los contratos OpenAPI y la infraestructura de ejecución todavía deben construirse y verificarse. La descripción de una capacidad expresa una decisión de diseño, no evidencia de su implementación.

## 2. Contexto, alcance y actores

AuxiRuta administra solicitudes de traslado de vehículos inmovilizados. Cada solicitud vincula un vehículo, un origen, un destino y una necesidad de remolque. El sistema identifica recursos compatibles, evalúa recorridos, reserva una grúa y un operador, solicita aceptación y gestiona la ejecución hasta la entrega o una resolución excepcional.

La operación corresponde a una única empresa, con disponibilidad de solicitudes todos los días durante las 24 horas. La prestación depende de la existencia de recursos habilitados y disponibles. Cada servicio traslada un vehículo con una grúa y un operador; no se programan servicios para fechas futuras.

| Actor / rol | Responsabilidad y alcance de acceso |
|---|---|
| Conductor — DRIVER | Registrar vehículos propios, solicitar y consultar sus servicios, cancelar antes del despacho y pedir cancelación después del despacho antes de la carga |
| Operador — OPERATOR | Declarar disponibilidad y ubicación de su grúa, responder propuestas, registrar ejecución y comunicar incidencias de sus asignaciones |
| Coordinador — COORDINATOR | Supervisar servicios, atender cancelaciones e incidencias y gestionar reasignaciones dentro de las restricciones |
| Administrador — ADMIN | Administrar operadores y grúas, consultar parámetros y ejecutar operaciones de coordinación |

La propiedad se identifica mediante el subject del usuario autenticado. Los controles de acceso deben considerar rol, propiedad, asignación y estado. ADMIN no autoriza a suplantar un operador para declarar una carga o una entrega, ni a omitir restricciones de compatibilidad o vigencia.

### 2.1 Exclusiones

Se excluyen del MVP pagos, tarifas, facturación, aseguradoras, pólizas, reparaciones, emergencias médicas, rescate de personas, rescate especial fuera de calzada, sanciones, reputación, suscripciones, turnos futuros, múltiples empresas, paradas intermedias, optimización global de flota, GPS continuo, mapas interactivos y geocodificación.

También se excluyen la elección arbitraria de grúa, la modificación de origen o destino una vez creada la solicitud, el transbordo o reasignación automática después de cargar, la sustitución de operador durante el recorrido, la doble confirmación de entrega por el conductor, fotografías, firmas y documentación adjunta.

Las notificaciones a usuarios permanecen fuera del alcance: no se incorpora bandeja interna, correo, SMS ni push. Los usuarios consultan estados y propuestas mediante las APIs. La gestión funcional de demoras se conserva como parte del negocio.

## 3. Arquitectura y límites funcionales

### 3.1 Contenedores y responsabilidades

| Componente | Responsabilidad | Datos propios |
|---|---|---|
| assistance-service | Administrar solicitudes, evaluar viabilidad y recorridos, seleccionar alternativas y coordinar el ciclo del servicio | Vehículos, solicitudes, evaluaciones, propuestas/asignaciones, historial, incidencias, peticiones de cancelación y acciones pendientes hacia Fleet |
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
| jobs | Ejecutar evaluación pendiente, vencimientos, demoras y reconciliación |
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

Lombok, DevTools y herramientas de tests se incorporan cuando resulten útiles. Actuator, Resilience4j, Flyway/Liquibase, brokers, service discovery, configuración centralizada y stacks de observabilidad quedan fuera de la línea base.

## 5. Restricciones del dominio AR-MVP-1

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
| Distancia de traslado por calles | 500 a 30.000 m |
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
| Priorización de suspensión para coordinación | Al superar 900 s |
| Comprobación de demoras | Cada 30 s |
| Peticiones de cancelación OPEN por solicitud | 1 |
| Incidencias bloqueantes OPEN por solicitud | 1 |

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

El conductor debe confirmar acceso por vía transitable y posibilidad de carga/descarga sin rescate especial. Una discrepancia detectada en el lugar se registra antes de cargar. La declaración no constituye certificación técnica. La entrega no implica reparación ni aptitud para circular.

### 5.4 Grúas y operadores

La grúa debe estar ACTIVE, sin reserva activa, con capacidad suficiente y compatibilidad explícita de tipo y movilidad. Se requiere un operador ACTIVE, AVAILABLE, vinculado exclusivamente a ese equipo y sin compromiso con otro servicio.

Capacidad, tipos admitidos y vínculo grúa/operador no se cambian con reservas o asignaciones activas. MAINTENANCE e INACTIVE requieren recursos libres. OUT_OF_SERVICE puede declararse durante un servicio y no libera la asignación. Solo ADMIN reactiva el equipo, con motivo.

La habilitación LIGHT_TOWING es un control operativo interno verificado manualmente, no una categoría legal de licencia. Debe tener inicio y vencimiento, estar vigente y vencer estrictamente después del final estimado del servicio. Se comprueba al reservar, confirmar, despachar, cargar, iniciar traslado y continuar tras suspensión.

Fleet asigna la fecha de ubicación al recibir coordenadas. El cliente no puede rejuvenecerla mediante una fecha enviada. La ubicación vencida excluye nuevas acciones que requieran vigencia, sin liberar servicios existentes. No se exige actualización GPS continua ni se detecta fraude de posición.

Una habilitación inválida antes de cargar impide avanzar y requiere otro recurso. Después de cargar se registra incidencia bloqueante y se requiere coordinación; no se abandona el vehículo ni se habilita una continuación que omita la restricción.

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

Cada reasignación posterior a una aceptación abre un nuevo intento de búsqueda y reinicia su cupo de propuestas, conservando la solicitud y el historial. El límite se aplica también a impedimentos confirmados antes del despacho y falta de partida en plazo. Los rechazos anteriores a aceptar no son reasignaciones.

Antes de despachar se revalidan reserva confirmada, estado operativo, ubicación, rutas y habilitación. Una falla temporal permite reintentar dentro del plazo sin extenderlo. Si el despacho vence, se invalida la asignación y se busca alternativa después de confirmar liberación, según el cupo disponible.

Si se agotaron las reasignaciones antes de comenzar el servicio, el resultado es UNFULFILLED. Si la ejecución comenzó, se mantiene suspensión y el coordinador debe resolverla. No se reasigna después de cargar.

### 5.9 Ejecución y demoras

El operador asignado declara salida, llegada, carga, inicio de traslado y entrega/descarga, en ese orden. La entrega requiere nota y ausencia de suspensión bloqueante. Estas declaraciones se auditan sin pretender comprobar presencia física mediante GPS o fotografías.

La llegada prevista se calcula al despachar como dispatchedAt más aproximación ajustada y margen. Superar las estimaciones de llegada, carga o traslado más la tolerancia genera una incidencia informativa por etapa/asignación. No se completa, cancela ni libera un servicio por superar la estimación. Las incidencias automáticas deben deduplicarse.

Una suspensión que supera el umbral de prioridad se presenta como pendiente prioritario para coordinación, sin un nuevo estado ni cierre automático.

### 5.10 Cancelación, corrección y cierre excepcional

El conductor cancela directamente antes del despacho. Después del despacho y antes de cargar presenta una petición OPEN, que no detiene el servicio. El coordinador puede aprobarla con constancia de aviso al operador, registrando suspensión y cancelación mediante el comando de negocio, o rechazarla con motivo.

La petición se cierra como no aplicable si se carga el vehículo o termina la solicitud. No tiene vencimiento automático ni plazo garantizado de respuesta. Después de cargar no se admite cancelación ordinaria ni cambio de destino; se utiliza el proceso de incidencias.

Una discrepancia de tipo, peso o movilidad permite corrección solo al coordinador, en suspensión anterior a carga. Se conserva revisión anterior, se incrementa revisión y se reevalúa. No se modifica el vehículo maestro, propietario, origen ni destino. Datos reales fuera del alcance se documentan como incompatibilidad; no se guardan valores falsos para forzar admisión.

El cierre excepcional exige motivo, ubicación final válida, responsable de custodia y constancia del resultado. No representa entrega exitosa.

### 5.11 Validación de entradas

Descripción de solicitud/incidencia: 10 a 1.000 caracteres. Motivos de rechazo/cancelación y nota de entrega: 10 a 500 caracteres. Los campos de resolución deben explicar la acción y cumplir los datos obligatorios del cierre cuando corresponda.

La creación exige Idempotency-Key de hasta 100 caracteres, única por conductor y conservada con la solicitud. No se admite estado, propietario o grúa elegida arbitrariamente en la entrada. Una solicitud terminal no se reabre; la creación de otra requiere ausencia de compromisos anteriores inciertos o pendientes sobre el vehículo.

## 6. Estados y transiciones

### 6.1 Solicitud

| Estado | Significado | Destinos permitidos |
|---|---|---|
| REQUESTED | Registrada, pendiente de resultado de evaluación | PENDING_EVALUATION, AWAITING_ACCEPTANCE, REJECTED, UNFULFILLED, CANCELLED |
| PENDING_EVALUATION | Falta información confiable por una falla temporal | AWAITING_ACCEPTANCE, REJECTED, UNFULFILLED, CANCELLED |
| AWAITING_ACCEPTANCE | Recursos retenidos temporalmente, esperando al operador | ASSIGNED, SEARCHING_ALTERNATIVE, CANCELLED, UNFULFILLED |
| ASSIGNED | Aceptación y reserva confirmadas | DISPATCHED, SEARCHING_ALTERNATIVE, CANCELLED, UNFULFILLED |
| SEARCHING_ALTERNATIVE | Búsqueda posterior a pérdida/rechazo de alternativa | PENDING_EVALUATION, AWAITING_ACCEPTANCE, UNFULFILLED, CANCELLED |
| DISPATCHED | Grúa en camino al origen | ARRIVED, SUSPENDED |
| ARRIVED | En origen, vehículo aún sin cargar | VEHICLE_LOADED, SUSPENDED |
| VEHICLE_LOADED | Vehículo cargado | IN_TRANSIT, SUSPENDED |
| IN_TRANSIT | Vehículo transportado al destino | COMPLETED, SUSPENDED |
| SUSPENDED | Incidencia bloqueante impide continuar | Etapa previa validada, SEARCHING_ALTERNATIVE, CANCELLED, CLOSED_UNRESOLVED |
| COMPLETED | Entrega y descarga declaradas en destino | Terminal |
| REJECTED | Incumplimiento objetivo del alcance o viabilidad del traslado | Terminal |
| UNFULFILLED | Solicitud válida sin recurso aceptado dentro de límites | Terminal |
| CANCELLED | Cancelación anterior a carga | Terminal |
| CLOSED_UNRESOLVED | Ejecución iniciada y finalizada excepcionalmente sin completar | Terminal |

Las salidas están sujetas a las precondiciones de la sección 5 y la resolución de incidencias. No existe un endpoint genérico que permita establecer cualquier estado. Los reintentos sin cambio efectivo no generan transiciones ficticias.

REJECTED comprende origen/destino fuera de cobertura, condiciones especiales excluidas, traslado fuera de distancia/duración y ausencia confirmada de ruta entre origen y destino. Una grúa con aproximación inviable se descarta sin rechazar toda la solicitud. La falta temporal de información o recursos no se confunde con rechazo permanente.

Cada transición registra origen, destino, fecha, actor y motivo. La suspensión conserva suspendedFromStatus. Una transición terminal no libera por sí misma un recurso remoto cuya confirmación continúa pendiente.

### 6.2 Vehículo, grúa, operador y reserva

| Concepto | Estados / valores | Semántica |
|---|---|---|
| Registro de vehículo | ACTIVE, INACTIVE | Admisión de nuevas solicitudes; sin borrado de historial |
| Condición declarada | ROLLABLE, NON_ROLLABLE | Capacidad de rodar para carga, independiente del registro |
| Situación logística del vehículo | Sin servicio activo, esperando asistencia, cargado, en traslado, suspendido | Derivada de la solicitud; no constituye otro estado editable |
| Registro de operador | ACTIVE, INACTIVE | Habilitación administrativa del perfil |
| Disponibilidad de operador | AVAILABLE, UNAVAILABLE | Declaración de disposición a trabajar; distinta de su ocupación |
| Estado operativo de grúa | ACTIVE, MAINTENANCE, OUT_OF_SERVICE, INACTIVE | Aptitud operativa del equipo |
| Ocupación de recurso | FREE, HELD, ASSIGNED | Derivada de reservas activas |
| Reserva Fleet | HELD, CONFIRMED, RELEASED, EXPIRED | Retención temporal, compromiso aceptado y cierres |
| Propuesta/asignación Assistance | RESERVATION_PENDING, RESERVATION_FAILED, PROPOSED, ACCEPTED, REJECTED, EXPIRED, CANCELLED, CLOSED | Historial del intento, incluida incertidumbre previa a conocer la reserva |
| Petición de cancelación | OPEN, APPROVED, REJECTED | Revisión de cancelación después de despacho |
| Incidencia | OPEN, RESOLVED | Gestión del problema, separada del estado del servicio |

La reserva transita HELD → CONFIRMED/RELEASED/EXPIRED y CONFIRMED → RELEASED. No se reabre. Una CONFIRMED no vence por cronómetro. Una HELD se considera vencida por su expiresAt aunque la tarea todavía no haya materializado EXPIRED.

RESERVATION_PENDING pasa a PROPOSED al recuperar una reserva válida o a RESERVATION_FAILED si no se obtuvo. PROPOSED pasa a ACCEPTED, REJECTED, EXPIRED o CANCELLED; ACCEPTED termina CLOSED al cerrar/reemplazar su asignación. El registro persiste antes de la llamada remota para recuperar respuestas perdidas.

### 6.3 Incidencias

| Tipo | Clase | Tratamiento |
|---|---|---|
| TOW_TRUCK_BREAKDOWN | Bloqueante | Suspender si comenzó la ejecución y marcar grúa OUT_OF_SERVICE |
| VEHICLE_DATA_MISMATCH | Bloqueante anterior a carga | Suspender, corregir datos admisibles y reevaluar |
| VEHICLE_NOT_ACCESSIBLE | Bloqueante | Requerir resolución de coordinación |
| DESTINATION_UNAVAILABLE | Bloqueante | Requerir resolución; no habilitar cambio de destino |
| OPERATOR_UNAVAILABLE | Bloqueante | Invalidar alternativa antes de despacho o suspender durante ejecución |
| QUALIFICATION_INVALID | Bloqueante | Impedir avance y requerir coordinación |
| DELAYED_ARRIVAL | Informativa | Registrar demora sin cambio de estado |
| LOADING_DELAY | Informativa | Registrar demora sin cambio de estado |
| TRANSPORT_DELAY | Informativa | Registrar demora sin cambio de estado |
| OPERATIONAL_NOTE | Informativa | Registrar observación |

La clase se deriva del tipo. No puede enviarse una avería como informativa. Una incidencia bloqueante durante ejecución lleva la solicitud a SUSPENDED; un impedimento anterior al despacho invalida la alternativa sin simular un traslado iniciado.

| Resolución | Condiciones y efecto |
|---|---|
| CONTINUE | Impedimento resuelto y recurso elegible; restaura la etapa suspendida. Una informativa se cierra sin cambiar la solicitud |
| REASSIGN | Antes de carga, con cupo y liberación confirmada; abre nueva búsqueda |
| CANCEL | Antes de carga y con constancia de aviso al operador; termina CANCELLED |
| CLOSE_UNRESOLVED | Ejecución iniciada sin posibilidad de completar; exige constancias de custodia y termina CLOSED_UNRESOLVED |

El coordinador o administrador resuelve. La resolución y el cambio local de solicitud deben ser atómicos. La grúa permanece ocupada durante suspensión. Una grúa averiada requiere reactivación administrativa antes de continuar. Las informativas abiertas pueden cerrarse con nota al terminar; una solicitud terminal no debe conservar una bloqueante abierta por omisión.

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

### 7.2 Incidencia y reasignación

Un impedimento comunicado o detectado por consulta queda asociado a la asignación. Si comenzó la ejecución, Assistance registra suspensión; si ocurrió antes del despacho, invalida la alternativa según sus reglas. Las averías originan una acción idempotente de cambio operativo en Fleet.

La reasignación cierra y libera el compromiso anterior antes de buscar otro. La primera grúa puede quedar FREE y OUT_OF_SERVICE; liberar no implica reactivar. Después de la carga se conserva la responsabilidad sobre el vehículo y se resuelve continuidad o cierre excepcional.

## 8. Integración externa

OpenRouteService aporta distancia y duración de la aproximación y del traslado. La aproximación determina elegibilidad y ranking; el traslado determina admisión y fin planificado, utilizado para validar la habilitación. Sin esa información el sistema no dispone de una base confiable para asignar el servicio.

El cliente recibe coordenadas WGS84 y las adapta al orden [longitud, latitud]. Utiliza Directions driving-car y verifica el ajuste admitido a la red vial. La respuesta debe contener distancia y duración no negativas y los campos necesarios. Respuestas incompletas o inválidas constituyen falla de integración.

Ausencia confirmada de ruta origen-destino rechaza la solicitud. Ausencia de ruta hacia un candidato lo descarta. Timeout, cuota agotada o indisponibilidad no equivalen a ausencia de ruta y no habilitan aprobación a ciegas.

No se presupone tránsito en tiempo real, acceso garantizado de vehículos pesados ni certificación de transitabilidad. La validación física de acceso pertenece al proceso operativo. La URL vigente y las cuotas de la cuenta deben verificarse al construir el adaptador. La clave permanece fuera del repositorio y la comunicación con el proveedor utiliza HTTPS.

## 9. Consistencia, concurrencia y recuperación

### 9.1 Transacciones locales y exclusividad

Cada base tiene transacciones propias. @Transactional no revierte una llamada HTTP que ya produjo un efecto remoto. Se guardan intención y cambio local, se realiza HTTP fuera de la transacción y se confirma el resultado en una transacción posterior.

Fleet bloquea los recursos en orden fijo, revalida y comprueba ausencia de compromisos antes de reservar. Todos los caminos de reserva, confirmación y liberación utilizan el mismo protocolo. Puede reforzarse con índices únicos parciales para HELD/CONFIRMED por grúa y operador.

Assistance protege la creación de solicitud mediante bloqueo del vehículo y verificación local de solicitudes y limpieza pendiente. Un índice de solicitudes activas no cubre por sí solo compromisos remotos inciertos después de un estado terminal. @Version detecta actualizaciones concurrentes de una fila, pero no dos INSERT de filas distintas.

### 9.2 Idempotencia y tareas pendientes

La clave de creación y la huella de entrada se conservan por conductor. Repetir igual clave/cuerpo devuelve el recurso existente; otro cuerpo con la misma clave produce conflicto. Las transiciones usan claves dentro de su ámbito y resultados durables en entidades/historial.

Las acciones hacia Fleet son RESERVE, CONFIRM, RELEASE y MARK_OUT_OF_SERVICE. Se persisten con datos explícitos y estado PENDING, COMPLETED o NEEDS_ATTENTION. No se implementa un motor genérico de eventos ni un broker.

Una respuesta perdida exige consultar por assignmentAttemptId antes de generar otro intento. Los procesos pendientes revalidan la solicitud antes de ejecutar; no confirman una solicitud cancelada. La limpieza de una reserva incierta continúa aunque haya vencido el plazo comercial. No se elimina la intención hasta resolver posibles efectos remotos.

Una solicitud cerrada con liberación pendiente expone resourceReleasePending. Los recursos siguen ocupados y el vehículo no admite nueva solicitud hasta confirmar su liberación. Un fallo agotado permanece diagnosticable y requiere intervención, sin descartarse silenciosamente.

### 9.3 Tratamiento de fallos

| Parámetro técnico | Valor |
|---|---|
| Timeout de conexión | 2 s |
| Timeout de respuesta | 5 s por llamada |
| Reintentos iniciales de recuperación | A los 5, 15 y 30 s |
| Reintentos posteriores | Cada 60 s mientras haya incertidumbre pendiente |
| Espera por 429 sin Retry-After | 60 s |

Se realiza una llamada por intento y no se repiten escrituras inmediatamente. Toda escritura reintentada debe contar con identificación idempotente. HTTP 429 respeta Retry-After. Un 401/403 técnico requiere corregir credenciales/permisos y no debe generar una secuencia de reintentos indiscriminada. Los conflictos de dominio se distinguen de fallas de red.

Reconciliación, vencimientos, evaluaciones y demoras se ejecutan mediante tareas programadas. Los endpoints aplican también las condiciones temporales; no dependen de que una tarea haya corrido exactamente en el instante de vencimiento. Se contempla una instancia ejecutable por servicio; la coordinación de múltiples réplicas queda fuera del MVP.

## 10. Identidad, autenticación y autorización

### 10.1 Realm, clientes y permisos

Keycloak administra el realm `auxiruta` y los roles humanos de la sección 2. El permiso técnico `FLEET_SERVICE` pertenece exclusivamente a la cuenta de servicio; no constituye un actor adicional ni se asigna a usuarios humanos.

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
| Assistance | AssistanceRequest / assistance_requests | Vehículo, conductor, estado actual y previo a suspensión, coordenadas, condición de acceso, datos declarados/revisión, descripción, intento/plazos, versión y snapshot de reglas, idempotencia/huella y cierre |
| Assistance | Assignment / assignments | Intento único, solicitud, candidato evaluado, IDs externos de recursos/reserva, subject del operador, estado, reserva/propuesta/aceptación/despacho y cierre; llegada planificada |
| Assistance | Evaluation / evaluations | Solicitud/intento/revisión, datos del vehículo, coordenadas usadas, parámetros efectivos, resultado/motivos y fecha de evaluación/obtención de ruta |
| Assistance | CandidateEvaluation / candidate_evaluations | Recursos externos, capacidad/compatibilidades, habilitación usada, ubicación/fecha, resultados de cobertura/elegibilidad, rutas crudas y ajustadas, ranking y fin estimado |
| Assistance | RequestStatusHistory / request_status_history | Solicitud, estado anterior/nuevo, actor, fecha, motivo, detalles, claves de operación y cambios de revisión/intento |
| Assistance | Incident / incidents | Solicitud/asignación, tipo/clase, descripción, etapa de origen, reportante, estado, resolución y responsable; ubicación y custodia si corresponde; deduplicación automática |
| Assistance | CancellationRequest / cancellation_requests | Solicitud, solicitante/motivo, estado, fechas, responsable y resolución |
| Assistance | PendingFleetAction / pending_fleet_actions | Operación idempotente, solicitud/asignación/incidencia, tipo, datos explícitos de recursos y snapshot necesario, estado, reintentos, próximos intentos y resultado/error |
| Fleet | Operator / operators | Subject único, nombre/contacto, estados de registro/disponibilidad, habilitación vigente, tiempo libre, fechas y versión |
| Fleet | TowTruck / tow_trucks | Patente única, operador actual único/opcional, capacidad, compatibilidad de movilidad, estado operativo, coordenadas y fecha/versión |
| Fleet | ResourceReservation / resource_reservations | Intento único, solicitud externa, recursos locales, estado, creación/vencimiento/confirmación/liberación, deadline recibido, fin estimado, huella y versión |
| Fleet | TruckStatusHistory / truck_status_history | Equipo, estado anterior/nuevo, motivo, actor, fecha y clave de operación de avería |
| Fleet | tow_truck_supported_types | Colección de tipos admitidos por grúa, con unicidad por equipo/tipo |

Los datos vigentes de habilitación y vínculo se guardan en Operator y TowTruck; las reservas/evaluaciones conservan los datos históricos usados. Un historial administrativo completo de renovaciones o vínculos no forma parte del MVP. Los cambios administrativos relevantes se registran en logs.

Los snapshots no requieren una entidad adicional: son columnas de solicitud/evaluación/candidato. Las reglas no requieren tablas de configuración versionada. Tampoco se crean tablas para cada enum, DTO o rol humano.

### 11.3 Mapeo y restricciones

Se utilizan Jakarta Persistence, constructor sin argumentos, @Entity, @Id, @Column y enums STRING. UUID identifica registros; BigDecimal representa pesos/coordenadas e Instant/TIMESTAMPTZ los instantes. Las relaciones locales parten preferentemente de N hacia 1 con LAZY; los listados e historiales se consultan y paginan, sin colecciones bidireccionales innecesarias.

Los repositorios extienden JpaRepository. Las consultas usan métodos derivados o JPQL parametrizado; SQL específico se reserva para restricciones o necesidades justificadas. No se serializan entidades directamente ni se propaga borrado hacia auditoría mediante cascade REMOVE.

Se aplican NOT NULL, FK locales, unicidad de patentes/subjects/intentos/claves y CHECK de pesos, coordenadas y fechas. Los índices cubren consultas por propietario/estado/fecha, operador/estado, historial por solicitud, reservas por vencimiento y acciones por próximo intento. Las reglas de exclusividad y registros OPEN se protegen mediante control transaccional sobre la solicitud o recursos.

### 11.4 Esquema y evolución

Se entregan scripts SQL controlados y `spring.jpa.hibernate.ddl-auto=validate`. create-drop se limita a entornos descartables. Los scripts de inicialización de PostgreSQL actúan sobre volumen vacío; la actualización de bases pobladas requiere un procedimiento SQL explícito. No se incorpora Flyway como dependencia obligatoria.

Cada servicio utiliza su DataSource/pool administrado por Spring. No comparte Connection como singleton ni administra manualmente un EntityManager por petición. H2 puede utilizarse en pruebas aisladas; la ejecución integral y comprobación de concurrencia se realizan sobre PostgreSQL.

## 12. APIs REST y contratos

### 12.1 Convenciones

El acceso público utiliza `/api/v1`, representaciones JSON, DTOs validados y ResponseEntity. Los listados tienen paginación y filtros. Los endpoints de consulta no cambian el estado. Las acciones específicas representan transiciones de negocio, sin permitir saltos arbitrarios.

Los identificadores y las fechas de autoridad son del servidor. La API filtra por propietario/asignación salvo permisos globales expresos. Una operación idempotente devuelve su resultado durable y no repite efectos.

### 12.2 Operaciones públicas

Las rutas de la tabla son relativas a `/api/v1`. A = Assistance; F = Fleet. Los permisos globales de ADMIN comprenden coordinación; las operaciones personales siguen requiriendo propiedad o asignación.

| Métodos y rutas | Servicio | Actor / comportamiento |
|---|---|---|
| POST /vehicles; GET /vehicles; GET /vehicles/{id}; PATCH /vehicles/{id} | A | DRIVER: registrar, listar, consultar y modificar vehículos propios bajo sus restricciones |
| PATCH /vehicles/{id}/registration-status | A | DRIVER: activar/desactivar, sin solicitud activa para desactivar |
| POST /assistance-requests | A | DRIVER: crear solicitud válida y disparar evaluación |
| GET /assistance-requests; GET /assistance-requests/{id} | A | DRIVER: propias; COORDINATOR/ADMIN: globales |
| GET /assistance-requests/{id}/history; GET /assistance-requests/{id}/incidents | A | Consultas autorizadas con información operativa pertinente; DRIVER sin notas internas/datos sensibles de terceros |
| POST /assistance-requests/{id}/cancellations | A | DRIVER antes de despacho; COORDINATOR/ADMIN según precondiciones |
| POST /assistance-requests/{id}/cancellation-requests | A | DRIVER: solicitar revisión posterior a despacho y anterior a carga |
| GET /operators/me; PATCH /operators/me/availability | F | OPERATOR: perfil y disponibilidad; no abandonar una reserva mediante cambio de disponibilidad |
| GET /tow-trucks/assigned-to-me; PUT /tow-trucks/assigned-to-me/location | F | OPERATOR: equipo propio y ubicación manual |
| GET /operator/assignments; GET /assignments/{id} | A | OPERATOR: propuestas, servicios e historial asignados |
| POST /assignments/{id}/acceptance; POST /assignments/{id}/rejection | A | OPERATOR: responder propuesta vigente |
| POST /assistance-requests/{id}/dispatch | A | OPERATOR: revalidar y registrar partida dentro del plazo |
| POST /assistance-requests/{id}/arrival | A | OPERATOR: registrar llegada |
| POST /assistance-requests/{id}/loading | A | OPERATOR: confirmar carga y compatibilidad observada |
| POST /assistance-requests/{id}/transport-start | A | OPERATOR: comenzar traslado |
| POST /assistance-requests/{id}/completion | A | OPERATOR: confirmar entrega/descarga y gestionar liberación |
| POST /assistance-requests/{id}/incidents | A | OPERATOR asignado o COORDINATOR/ADMIN: registrar problema tipificado |
| GET /incidents; GET /incidents/{id} | A | COORDINATOR/ADMIN: consultar casos y resoluciones |
| POST /incidents/{id}/resolution | A | COORDINATOR/ADMIN: resolver conforme al catálogo |
| GET /cancellation-requests; POST /cancellation-requests/{id}/resolution | A | COORDINATOR/ADMIN: revisar solicitudes OPEN |
| POST /assistance-requests/{id}/reassignments | A | COORDINATOR/ADMIN: iniciar búsqueda alternativa válida; con bloqueante abierta se utiliza su resolución REASSIGN |
| PATCH /assistance-requests/{id}/declared-vehicle-data | A | COORDINATOR/ADMIN: corregir discrepancia anterior a carga y reevaluar |
| GET /operators; GET /operators/{id}; GET /tow-trucks; GET /tow-trucks/{id} | F | COORDINATOR/ADMIN: consultar recursos y capacidades |
| POST /tow-trucks/{id}/out-of-service | F | COORDINATOR/ADMIN: registrar impedimento; Assistance lo detecta por consulta |
| POST /operators; PATCH /operators/{id}; PATCH /operators/{id}/registration-status | F | ADMIN: registrar perfil de un subject existente y administrar datos/estado |
| PUT /operators/{id}/qualification | F | ADMIN: registrar habilitación vigente verificada manualmente |
| POST /tow-trucks; PATCH /tow-trucks/{id} | F | ADMIN: registrar equipo inicialmente INACTIVE y administrar capacidades |
| PUT /tow-trucks/{id}/operator; DELETE /tow-trucks/{id}/operator | F | ADMIN: vincular/desvincular recursos libres |
| PATCH /tow-trucks/{id}/operational-status | F | ADMIN: activar, mantener o inactivar según condiciones; avería mediante acción específica |
| GET /configuration/assistance; GET /configuration/fleet | A / F | ADMIN: consultar parámetros efectivos sin secretos; sin edición por API |

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
| POST /internal/tow-trucks/{id}/out-of-service | Registrar avería idempotente vinculada a incidencia |
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

## 13. Componente común reutilizable

`auxiruta-commons` concentra el formato de error y utilidades transversales efectivamente reutilizadas: extracción de roles de Keycloak, representación común de errores y acceso a identidad cuando sea compatible con el entorno consumidor. Los adaptadores servlet/reactivos y las autorizaciones específicas permanecen en cada aplicación.

La biblioteca se construye como artefacto Maven independiente y se instala en `.m2`; ambos servicios funcionales declaran su dependencia. Copiar archivos fuente entre proyectos no constituye consumo del componente.

No contiene entidades JPA, repositorios, DTOs de negocio ni reglas de asignación o capacidad. Esta frontera permite que cada servicio conserve su modelo y contrato y evita que un cambio funcional obligue a evolucionar todos los proyectos simultáneamente.

## 14. Logging y operación

Se incluyen logs básicos con SLF4J/Logback, versiones administradas por Boot y salida a consola recogida por Docker. No se crea una base, tabla ni API de logs. El formato contiene instante, nivel, servicio, clase y mensaje; se añaden requestId, assignmentId y reservationId cuando existan.

| Aplicación | Eventos |
|---|---|
| Gateway | Método, ruta normalizada, código, duración y fallos de acceso/enrutamiento |
| Assistance | Creación, decisiones de evaluación, propuestas, transiciones, cancelaciones, incidencias, reasignación y recuperación |
| Fleet | Reservas, confirmaciones, liberaciones, vencimientos, conflictos, cambios de equipo y administración relevante |
| Cliente de rutas | Operación, resultado o tipo de error y duración, sin URL/cuerpo sensible |

INFO registra eventos relevantes; WARN, fallos recuperables y situaciones operativas excepcionales; ERROR, fallos inesperados o acciones que requieren intervención; DEBUG, diagnóstico. No se registra una excepción repetidamente en cada capa ni una consulta periódica sin cambios como evento importante.

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
| KC_HOSTNAME, KC_HOSTNAME_BACKCHANNEL_DYNAMIC | Dirección pública y acceso interno de Keycloak |

En Compose, las URLs funcionales de base son `jdbc:postgresql://postgres:5432/assistance_db` y `jdbc:postgresql://postgres:5432/fleet_db`. La de Keycloak corresponde a `keycloak_db`; Fleet se accede mediante `http://fleet-service:8081` y Assistance mediante `http://assistance-service:8082`.

`.env.example` contiene nombres y valores ficticios; `.env` no se versiona. Los secretos requeridos no tienen contraseñas reales de respaldo en código. Las credenciales iniciales de administración de Keycloak se inyectan conforme a la versión elegida.

Los parámetros de negocio se cargan con @ConfigurationProperties y validación. El archivo de referencia [AuxiRuta-parametros-MVP.yml](documentacionPartes/AuxiRuta-parametros-MVP.yml) debe coincidir con la sección 5 y distribuirse/importarse en la configuración efectiva; almacenarlo en docs no lo carga automáticamente.

### 15.3 Organización documental y técnica

```text
BACK-TPI-G1/
├── README.md
├── docs/
│   ├── DECISIONES_ARQUITECTONICAS.md
│   ├── documentacionPartes/
│   ├── diagramas/
│   │   ├── AuxiRuta_C1.png
│   │   └── AuxiRuta_C2.png
│   └── api/                         [por construir]
├── auxiruta-commons/                [por construir]
├── fleet-service/                  [por construir]
├── assistance-service/             [por construir]
├── api-gateway/                    [por construir]
├── infra/                          [por construir]
├── docker-compose.yml              [por construir]
└── .env.example                    [por construir]
```

El C1 representa actores, sistema e integraciones externas; el C2, contenedores, tecnologías y comunicaciones. Las imágenes existentes deben revisarse contra el diseño y el despliegue final. La biblioteca se representa como dependencia, no contenedor. Los scripts shell se guardan con LF y los archivos de compilación/secretos se excluyen de Git.

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
| Concurrencia | Dos solicitudes sobre un recurso; aceptación contra vencimiento; cancelación contra despacho |
| Integración | Ruta inexistente frente a timeout, cuota, error de credenciales y respuesta inválida |
| Recuperación | Reserva/confirmación con respuesta perdida, reinicio con acción pendiente, cierre con liberación incierta |
| Excepciones | Avería antes/después de cargar, corrección permitida y cierre con custodia |
| Seguridad | Rol insuficiente, propietario ajeno, operador no asignado y acceso externo a interfaces internas |
| Logging | Eventos relevantes localizables por ID, niveles coherentes y ausencia de secretos/datos sensibles |
| Despliegue | Arranque con volumen vacío, reinicio conservando datos y procedimientos de actualización documentados |

Los tests automatizados pueden aportar evidencia cuando sean útiles; no se presume una cobertura o suite inexistente. Las comprobaciones temporales pueden usar Clock inyectado. Los criterios deben mantenerse sincronizados con cada cambio de reglas.

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
| Incidencia | Problema u observación asociado a una solicitud y asignación |
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
| Suspensión | Interrupción controlada por incidencia bloqueante, conservando recursos y responsabilidad |
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
| 1.0 | 2026-11-03 | Consolidación de dominio AR-MVP-1, arquitectura, seguridad, persistencia, contratos, despliegue, logging, matriz de requisitos y glosario |

Toda revisión posterior debe agregar una entrada y precisar las secciones afectadas. Antes de declarar una capacidad implementada se deben registrar su evidencia y verificación. Las versiones exactas del entorno Java/Docker, la configuración efectiva de Keycloak, los contratos OpenAPI, las restricciones SQL y las cuotas del proveedor se completarán durante la construcción sin modificar silenciosamente esta línea base.
