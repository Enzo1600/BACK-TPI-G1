# Cambios y guía de aprendizaje

Este documento conserva el historial de construcción por fases. Desde fase 5, las instrucciones vigentes de pruebas están en [PRUEBAS_JAVA.md](PRUEBAS_JAVA.md). Cada fase revisa la anterior y los componentes afectados; registra correcciones, código incorporado, verificaciones y pendientes.

## Fase 1 — Estructura y compilación — 6 de noviembre de 2026

### Revisión inicial

El repositorio contenía documentación y diagramas, sin proyectos Maven ni código Java. No existía una fase previa de implementación. Se contrastaron nombres, versiones, dependencias y puertos con las decisiones arquitectónicas. Los diagramas se conservaron según la decisión del equipo.

### Qué se construyó

Cuatro proyectos Maven independientes con groupId `com.auxiruta` y versión `1.0.0`: auxiruta-commons, fleet-service, assistance-service y api-gateway. Cada uno incluye Wrapper 3.3.4 que descarga y ejecuta Maven 3.9.11. El Wrapper y Maven tienen versiones propias y distintas.

Se configuraron Java 21, Spring Boot 3.5.16, Spring Cloud 2025.0.3 para Gateway y springdoc 2.10.0 para los servicios MVC. Las dependencias transitivas son administradas por el parent/BOM. El entorno de compilación utiliza Oracle JDK 21.0.3; la imagen Temurin para Docker se prepara en la fase 2.

Los ejecutables producen `target/app.jar`; commons produce un JAR normal que ambos servicios consumen desde el repositorio Maven local. `.gitignore` excluye compilaciones, secretos locales y archivos del IDE. `.gitattributes` conserva los scripts Unix con LF y los comandos Windows con CRLF.

### Explicación del código y la configuración

- **pom.xml:** describe la identidad del artefacto, sus dependencias y cómo compilarlo. `parent` hereda versiones y configuración de Boot; `dependencyManagement` en Gateway importa la familia compatible de Spring Cloud. `runtime` indica que el driver PostgreSQL se necesita al ejecutar; `test` limita las herramientas de prueba a esa etapa.
- **FleetApplication, AssistanceApplication y GatewayApplication:** contienen el punto de entrada `main`. `SpringApplication.run` inicia Spring. `@SpringBootApplication` combina configuración, autoconfiguración y búsqueda de componentes bajo el paquete base de cada aplicación.
- **application.yml:** asigna el nombre del servicio y su puerto: Fleet 8081, Assistance 8082 y Gateway 8000. `${SERVER_PORT:8081}`, por ejemplo, utiliza la variable externa si existe y 8081 en caso contrario.
- **UtcClock:** utilidad Java del JAR común que proporciona `Clock.systemUTC()`. Centraliza la fuente de tiempo UTC sin depender de entidades ni reglas de negocio.
- **ClockConfiguration:** cada servicio declara mediante `@Configuration` y `@Bean` un Clock obtenido de commons. Las futuras clases de negocio lo recibirán por constructor. Así podrán usar `Instant.now(clock)` y, en pruebas, sustituirlo por un reloj controlado. Las reglas temporales todavía no están implementadas.
- **Maven Wrapper:** `mvnw.cmd` ejecuta Maven en Windows y `mvnw` en Linux/macOS. `.mvn/wrapper/maven-wrapper.properties` fija la distribución descargada. Los compañeros usan la misma versión aunque su Maven global sea diferente.

Gateway utiliza WebFlux; los servicios funcionales utilizan MVC y JPA. Commons no contiene un servidor, un método main ni el plugin de empaquetado ejecutable de Boot. El uso de UtcClock desde ambos servicios verifica una dependencia real de la biblioteca.

### Cómo reproducir la compilación

Desde la raíz del repositorio, en PowerShell:

```powershell
.\auxiruta-commons\mvnw.cmd -f auxiruta-commons/pom.xml clean install
.\fleet-service\mvnw.cmd -f fleet-service/pom.xml clean verify
.\assistance-service\mvnw.cmd -f assistance-service/pom.xml clean verify
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml clean verify
```

Se instala primero commons porque los otros proyectos resuelven su dependencia desde `.m2`. `clean` borra únicamente los productos de compilación del proyecto; `verify` recorre compilación, pruebas disponibles y empaquetado; `install` además publica el artefacto en el repositorio local. La primera ejecución requiere acceso a Internet para descargar Maven y dependencias.

### Límites de esta fase

Se verifica compilación y empaquetado. Todavía no hay endpoints, entidades, contratos funcionales ni pruebas de negocio. Maven puede informar que no existen tests: ejecutar verify no significa que se haya probado un proceso funcional.

El arranque de los servicios MVC requiere configurar DataSource y PostgreSQL en la fase 2. La política JWT y el enrutamiento se implementan en la fase 3. No se deshabilitó JPA ni seguridad para aparentar un backend operativo. Docker Compose, realm, credenciales e imágenes propias permanecen pendientes.

### Verificación

Los cuatro proyectos finalizaron con BUILD SUCCESS. Ambos ejecutables funcionales incluyen auxiruta-commons-1.0.0.jar; la biblioteca contiene UtcClock.class sin estructura BOOT-INF. La revisión de fase 2 repitió las cuatro compilaciones satisfactoriamente.

## Fase 2 — Infraestructura e identidad — 6 de noviembre de 2026

### Revisión y correcciones de la fase anterior

Se repitieron clean install de commons y clean verify de las tres aplicaciones: cuatro BUILD SUCCESS. Se corrigió la codificación de este documento, que mostraba acentos dañados, y se completó el apartado de verificación de fase 1 que había quedado con texto pendiente. No fue necesario cambiar las clases Java de esa fase.

### Implementación y explicación

- **docker-compose.yml:** describe cinco servicios, una red privada de Compose y el volumen postgres-data. Solo Gateway (8000) y Keycloak (8080) publican puertos en localhost. Los nombres de servicio funcionan como DNS interno. Las variables obligatorias usan `${VARIABLE:?Configurar .env}` para detectar configuración incompleta. La rotación de logs limita el uso de disco.
- **Dockerfile de cada aplicación:** la primera etapa usa un JDK, instala commons con el Wrapper y compila el ejecutable. La segunda usa un JRE y copia únicamente app.jar; se ejecuta con usuario auxiruta sin privilegios. El JDK incluye compilador, mientras que el JRE permite ejecutar el resultado. Cada build resuelve sus propias dependencias y no necesita Maven ni .m2 del host.
- **infra/postgres/01-databases.sh:** PostgreSQL lo ejecuta al inicializar un volumen vacío. Crea assistance_db, fleet_db y keycloak_db, cada una con usuario propietario propio. Revoca permisos de PUBLIC para impedir conexiones cruzadas. Los valores de contraseña se pasan como variables psql y se citan como literales SQL. No se incorporan aún tablas de negocio.
- **infra/keycloak/auxiruta-realm.json:** configura el realm, roles, clientes y usuarios de demostración. Los placeholders de secretos se resuelven desde el entorno del contenedor. El cliente público utiliza Authorization Code con PKCE S256; el confidencial utiliza client_credentials. Los mappers agregan las audiencias y los scopeMappings limitan los roles que cada cliente puede recibir.
- **application-docker.yml:** Spring activa este archivo con el perfil docker. Los servicios configuran DataSource y ddl-auto=validate, por lo que Hibernate verificará el esquema sin crearlo ni destruirlo. open-in-view=false mantiene la persistencia fuera de la serialización web. Los tres ejecutables configuran emisor público y claves JWKS por URL interna. Assistance declara además fleet-client para obtener credenciales técnicas; su uso por RestClient se incorporará con las integraciones.
- **.env.example y .env:** el primero documenta variables con placeholders. El segundo contiene secretos locales generados y está excluido de Git. No se debe copiar ni publicar su contenido. .dockerignore evita enviar secretos, Git y compilaciones previas al contexto del build.

Los healthchecks de PostgreSQL y Keycloak permiten esperar su disponibilidad antes de iniciar dependencias. No se agregó Actuator. Keycloak administra sus propias migraciones mediante herramientas internas; esto no introduce Liquibase como dependencia de nuestros servicios.

### Uso y alcance

La guía [infra/README.md](../infra/README.md) documenta arranque, parada, datos de prueba, variables y repetición de las comprobaciones. Los comandos principales son `docker compose up -d --build`, `docker compose ps` y `docker compose logs`. `docker compose down` sin -v conserva el volumen. Cambiar variables o el JSON no modifica automáticamente una base/realm ya inicializados.

Entorno observado: Docker Engine 29.9.2, Compose 5.6.1, PostgreSQL 17.11, Keycloak 26.7.5 y Temurin 21.0.13+9 dentro del contenedor. Las imágenes Java usan tags 21-jdk-jammy y 21-jre-jammy; el parche descargado puede variar al actualizar esos tags.

La fase 2 acredita infraestructura e identidad. La autorización propia por roles/audiencias, las rutas del Gateway y el manejo común de errores se completan en fase 3. Los usuarios demo en Keycloak no sustituyen los perfiles de operadores de Fleet. Las aplicaciones arrancan con sus mecanismos de seguridad autoconfigurados, sin endpoints de negocio todavía.

### Verificaciones realizadas

- Cuatro compilaciones locales de la fase 1 exitosas y construcción de las tres imágenes mediante Maven dentro de Docker.
- Tres aplicaciones iniciadas, con PostgreSQL y Keycloak disponibles y sus healthchecks satisfactorios.
- Las tres bases tienen propietarios separados; los usuarios funcionales no tienen permiso CONNECT sobre la base del otro servicio.
- Token técnico emitido con issuer público, audiencia auxiruta-fleet y FLEET_SERVICE, sin roles humanos.
- Login real Authorization Code + PKCE exitoso para DRIVER, OPERATOR y ADMIN, con audiencia auxiruta-api y sin FLEET_SERVICE.
- Secreto técnico incorrecto rechazado con HTTP 401.

La lectura de claims en el script verifica la configuración de emisión; la validación criptográfica y la política de acceso de las APIs tendrán sus comprobaciones específicas en fase 3.
- Reinicio de PostgreSQL y Keycloak: realm conservado con el mismo ID; prueba de identidad repetida satisfactoriamente después del reinicio. .env verificado como excluido de Git.

## Fase 3 — Gateway, seguridad y componentes transversales

Fecha: 7 de noviembre de 2026. Se conserva la línea base funcional AR-MVP-5. Esta fase implementa infraestructura de aplicación, sin adelantar CRUD ni decisiones del negocio.

### Integridad de la fase anterior y corrección

Se inició Docker Desktop y se recuperaron los cinco contenedores conservando el volumen existente. Se repitió la emisión de tokens técnicos, los tres logins humanos Authorization Code + PKCE y el rechazo de un secreto técnico incorrecto.

La validación real de JWT detectó una omisión de la fase 2: los tokens humanos tenían audiencia y roles correctos, pero no incluían `sub`. Se agregó el scope predeterminado `basic` a ambos clientes en `infra/keycloak/auxiruta-realm.json`. La prueba idempotente `KeycloakScopeSetupTest` aplica la misma corrección al realm existente mediante la API administrativa local, sin eliminar usuarios, credenciales o bases. `BackendIntegrationTest` comprueba ahora también que `sub` exista. No se debilitó el validador para aceptar usuarios sin identificador.

### Implementación y explicación del código

- **TokenSupport, en auxiruta-commons:** extrae exclusivamente los roles reconocidos de `realm_access.roles` y los convierte a authorities con prefijo `ROLE_`. Compone validadores de emisor, vigencia, audiencia y presencia de `sub`/`exp`. `permits` exige además el cliente autorizado (`azp`) y separa tokens humanos de técnicos. `subject` obtiene el identificador estable; `requireOwner` compara ese identificador con el propietario persistido y responde 404 ante una diferencia. La aplicación deberá cargar o consultar los recursos por propietario; esta utilidad no filtra automáticamente repositorios.
- **SecurityConfiguration de cada aplicación:** declara la cadena de filtros que interviene antes del controlador. Nimbus verifica la firma RS256 mediante JWKS interno de Keycloak. El emisor esperado conserva la dirección pública. Gateway acepta audiencia `auxiruta-api`; Assistance también; Fleet admite además `auxiruta-fleet`, restringida a las rutas internas y al cliente `assistance-service` con `FLEET_SERVICE`.
- **Permisos por método y ruta:** se incorporó la matriz de endpoints comprometida. DRIVER, OPERATOR y ADMIN tienen permisos explícitos y no jerárquicos. Todo acceso no declarado se rechaza. Las rutas personales `me` y `assigned-to-me` se resuelven antes de las plantillas `{id}`, evitando que un método distinto herede permisos administrativos.
- **GatewayRoutes:** dirige los recursos de flota a Fleet y los de asistencia a Assistance. Mantiene el Bearer original, que el destino verifica nuevamente. Los prefijos `/docs/fleet` y `/docs/assistance` permiten consultar los documentos OpenAPI JSON autenticados. No hay rutas públicas para `/internal`. Las direcciones de destino se pueden configurar con `FLEET_URI` y `ASSISTANCE_URI`.
- **Seguridad sin sesión:** las APIs usan Bearer y no login por formulario ni HTTP Basic. Las cadenas no conservan una sesión autenticada. CSRF está deshabilitado para estas APIs sin autenticación por cookie; el login del usuario sigue realizándose en Keycloak mediante PKCE.
- **Problems, en commons:** produce el formato compartido `ProblemDetail`, con estado HTTP, título, detalle controlado y código estable `HTTP_<estado>`. No expone mensajes arbitrarios de excepciones, tokens ni detalles internos.
- **SecurityErrors:** adapta ese formato a errores previos al controlador: 401 y 403. Gateway utiliza la API reactiva; los microservicios utilizan Servlet. Se incluye el encabezado `WWW-Authenticate: Bearer` en 401. Los errores de enrutamiento del Gateway también reciben una respuesta controlada.
- **ApiExceptionHandler:** `@RestControllerAdvice` en cada microservicio transforma excepciones MVC y `ResponseStatusException` al formato compartido. Los errores inesperados producen 500. La lógica de representación reutilizable permanece en commons; los adaptadores HTTP permanecen en cada aplicación para evitar introducir Servlet en Gateway.
- **RequestLoggingFilter:** registra método, estado HTTP y duración. Gateway usa INFO; los microservicios usan DEBUG para diagnóstico. No registra cabeceras, cuerpos, URLs con coordenadas ni tokens. Se mantiene la salida a consola y rotación Docker de fase 2. Los logs de transiciones y auditoría persistente se incorporarán cuando existan esas operaciones. El diagnóstico de excepciones registra por ahora su clase; no vuelca mensajes potencialmente sensibles.

El Gateway también depende ahora del JAR común para reutilizar validación y formato de errores. La librería no contiene entidades, repositorios ni DTOs funcionales de los microservicios.

### Pruebas y forma de repetirlas

Las pruebas de commons cubren audiencia, emisor, vencimiento, rol, cliente autorizado, separación técnica/humana y ocultamiento de recursos ajenos. Las pruebas MVC de Fleet y Assistance verifican sus cadenas sin depender del Gateway, mediante controladores de prueba que solo existen en `src/test`. Estas últimas usan autenticaciones simuladas y no sustituyen las pruebas criptográficas de integración.

```powershell
./auxiruta-commons/mvnw.cmd -f auxiruta-commons/pom.xml install
./fleet-service/mvnw.cmd -f fleet-service/pom.xml verify
./assistance-service/mvnw.cmd -f assistance-service/pom.xml verify
./api-gateway/mvnw.cmd -f api-gateway/pom.xml verify
docker compose up -d --build
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.integration=true" "-Dtest=BackendIntegrationTest"
```

`BackendIntegrationTest` reutiliza el login de `BackendIntegrationTest`: recibe tokens reales, consulta OpenAPI por ambos destinos y comprueba rechazos por falta de token, rol, audiencia y firma alterada. Las llamadas directas dentro de Docker comprueban la protección propia de los microservicios. Los tokens permanecen en memoria y se pasan a curl por stdin; no aparecen en argumentos ni archivos.

### Límites de la fase

Las rutas están protegidas y enrutadas, pero sus controladores de negocio aún no existen: una ruta autorizada puede devolver 404. La prueba positiva de extremo a extremo utiliza documentos OpenAPI reales; no se agregaron endpoints ficticios de producción. Los filtros de listas por propietario y los chequeos contra la base se implementarán junto con vehículos, operadores y solicitudes. El contrato OpenAPI funcional se ampliará con esos controladores. La interfaz Swagger UI con login, las llamadas de negocio Assistance → Fleet, los logs de transiciones y la integración ORS pertenecen a fases posteriores. No se modificaron diagramas ni reglas funcionales.

### Resultados de verificación

- Compilación de los cuatro módulos y construcción de las tres imágenes Docker satisfactorias.
- 21 pruebas Java: 12 de commons, 5 de seguridad MVC de Fleet y 4 de Assistance, sin fallos. Gateway se verifica mediante las pruebas HTTP sobre su contenedor real.
- 22 comprobaciones HTTP satisfactorias con tokens emitidos por Keycloak: documentos OpenAPI 200 para los tres roles; 401 sin token, firma alterada o audiencia incorrecta; 403 por rol no autorizado; 404 para rutas autorizadas todavía sin controlador.
- En la red privada, Fleet rechaza tokens humanos para endpoints internos y admite el cliente técnico hasta la resolución del recurso; Assistance rechaza esa audiencia técnica. Ambos aplican permisos aunque la petición no pase por Gateway.
- La emisión de identidad exige ahora `sub`; los tres logins PKCE y el rechazo de un secreto técnico inválido se repitieron después de corregir el realm.
- `.env`, `target/` excluidos de Git. Diagramas conservados. No se realizó commit ni push.

## Fase 4 — Administración de flota — 7 de noviembre de 2026

### Alcance y revisión de la fase anterior

Se incorporó el [plan de diez fases](FASES_IMPLEMENTACION.md) proporcionado por el equipo. Esta etapa implementa el catálogo administrativo de Fleet. Las reservas corresponden a fase 7 y la búsqueda/elegibilidad a fase 6.

Antes de modificar código se inició la infraestructura conservando sus volúmenes y se ejecutaron satisfactoriamente las 22 comprobaciones HTTP de la fase 3. Al incorporar GET /operators, su expectativa de regresión cambia de 404 a 200 para ADMIN; los casos de seguridad conservan sus resultados. Se corrigieron fragmentos con codificación dañada del README heredados de la fase anterior. Se amplió el manejo de errores de Fleet para responder 409 ante violaciones de unicidad y conflictos de concurrencia, sin devolver mensajes SQL.

### Funcionalidades implementadas

- ADMIN registra y consulta operadores, actualiza nombre/teléfono, activa/inactiva el registro y administra habilitaciones LIGHT_TOWING con fechas coherentes. El subject es inmutable y único. La identidad OPERATOR se prepara previamente en Keycloak; Fleet no crea usuarios ni administra roles, y la correspondencia del subject suministrado se verifica administrativamente en esa consola.
- OPERATOR consulta su perfil por el sub del JWT y declara disponibilidad. Un perfil INACTIVE no puede pasar a AVAILABLE. Inactivar un perfil retira su disponibilidad. Repetir AVAILABLE no reinicia availableSince, evitando alterar el futuro criterio de antigüedad.
- ADMIN registra grúas INACTIVE y administra patente, capacidad de 500 a 5.000 kg, tipos admitidos CAR/SUV/PICKUP y compatibilidad con vehículos no rodables. La patente se normaliza a mayúsculas sin espacios ni guiones y admite 6 a 10 caracteres alfanuméricos.
- Los vínculos son exclusivos: una grúa por operador y un operador por grúa. Repetir el vínculo existente es válido; sustituirlo exige desvincular primero. La desvinculación repetida devuelve 204.
- OPERATOR consulta únicamente su equipo vinculado y actualiza coordenadas. El servidor fija locationUpdatedAt. Las coordenadas deben ser geográficamente válidas; la cobertura comercial y la antigüedad para elegir candidatos se aplicarán en fase 6.
- ADMIN cambia el estado operativo entre INACTIVE, ACTIVE y MAINTENANCE con motivo y encabezado Idempotency-Key. Cada transición produce un evento persistente. Repetir igual clave, recurso, actor y contenido devuelve exactamente ese evento, incluso si luego hubo otros cambios; no vuelve a aplicar el estado anterior. Reutilizar la clave con otra operación devuelve 409.
- Los listados tienen página inicial 0, tamaño predeterminado 20 y máximo 100; admiten filtros registrationStatus u operationalStatus. GET /configuration/fleet expone los límites administrativos vigentes, sin secretos.

### Explicación del código

**Entidades y SQL.** Operator, TowTruck y TruckStatusHistory usan @Entity para mapear objetos a tablas. TowTruck contiene una referencia @ManyToOne LAZY al operador con columna UNIQUE, por lo que el vínculo es exclusivo aunque la navegación sea unidireccional. Los tipos compatibles se guardan mediante @ElementCollection en tow_truck_supported_types. Los enums se persisten como texto. UUID identifica cada registro; Instant representa fechas UTC; BigDecimal conserva precisión en capacidad y coordenadas. @Version mantiene la versión de cada fila para detectar escrituras concurrentes.

El archivo fleet-service/src/main/resources/db/001-fleet-catalog.sql crea cuatro tablas con claves primarias, FK locales, unicidad, CHECK e índices. No existen FK hacia Keycloak. El perfil docker aplica este bootstrap aditivo con CREATE TABLE IF NOT EXISTS antes de que Hibernate ejecute ddl-auto=validate. Funciona con una base nueva y con la base vacía de fase 3, sin borrar el volumen. No es un motor de migraciones: futuros cambios de columnas exigirán scripts ALTER explícitos y ordenados; IF NOT EXISTS no corrige tablas incompatibles.

**Repositorios.** Las interfaces JpaRepository proporcionan consultas y persistencia administradas por Spring Data. Las consultas de vínculo y subject permiten resolver el ámbito propio. @Lock(PESSIMISTIC_WRITE) obtiene bloqueos de fila durante una transacción. La vinculación bloquea primero grúa y luego operador; la unicidad SQL refuerza la exclusividad. Las lecturas que preceden a una actualización propia evitan reutilizar una entidad desactualizada antes de adquirir el bloqueo.

**FleetService.** Contiene las reglas y los límites transaccionales con @Transactional. Las lecturas usan transacciones de solo lectura. El servicio recibe Clock por constructor para decidir fechas de forma comprobable; normaliza los instantes a microsegundos, la precisión de PostgreSQL, para que una respuesta idempotente coincida con la representación recuperada. Un proxy LAZY de Hibernate se inicializa/desenvuelve cuando el bloqueo devuelve un operador ya referenciado por una grúa; esto evita confundir campos del proxy con datos de la entidad.

Los métodos flush fuerzan la detección de restricciones antes de construir la respuesta. Una excepción revierte la transacción. Los eventos de alta, habilitación y estado se registran en consola después del commit mediante TransactionSynchronization, evitando anunciar éxito si la transacción se revierte. El historial de estado se guarda en la misma transacción que la modificación de la grúa.

**DTOs y controlador.** Los records de FleetDtos definen entradas y respuestas. Jakarta Validation comprueba nulos, longitudes, rangos y tipos antes del servicio. Se rechazan propiedades JSON desconocidas: enviar owner, version, estado inicial o fecha de ubicación no permite modificar campos del servidor. Los PATCH actualizan los campos no nulos suministrados; para vaciar el teléfono se usa cadena vacía. FleetController recibe @AuthenticationPrincipal Jwt para las operaciones personales, en lugar de aceptar un subject en la URL o el cuerpo. Responde 201 y Location al crear, 204 al desvincular y DTOs en consultas/modificaciones. Las entidades JPA nunca se serializan como contrato público.

**Documentación REST.** OpenApiConfiguration declara Bearer JWT y servidor relativo al host del Gateway. Los endpoints incorporan descripción funcional, roles, parámetros y respuestas. El JSON está disponible con token en /docs/fleet/v3/api-docs. Las rutas personales devuelven 404 cuando falta perfil o equipo; ADMIN no hereda esos permisos.

### Pruebas reproducibles

```powershell
docker compose up -d --build
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.integration=true" "-Dtest=BackendIntegrationTest"
```

BackendIntegrationTest realiza login real con PKCE y usa Gateway, Fleet y PostgreSQL. Mantiene datos identificados con patentes TEST; no borra filas de auditoría. Al finalizar deja las grúas creadas INACTIVE y sin vínculo y el operador demo UNAVAILABLE. Debe ejecutarse en el entorno local de pruebas: utiliza el perfil operator.demo y rechaza desvincular una grúa cuya patente no tenga el prefijo de prueba. Cada ejecución agrega grúas de prueba nuevas. No imprime ni guarda tokens.

Las pruebas Java de FleetService usan un Clock fijo para comprobar fecha de ubicación, antigüedad de disponibilidad, rechazo de intervalos inválidos, perfil inexistente y repetición durable de estados. Las pruebas HTTP cubren ambos actores, endpoints administrativos/personales, normalización, duplicados, límites, propiedades desconocidas, idempotencia y dos vinculaciones simultáneas sobre PostgreSQL real.

### Continuidad de las fases

La administración queda operativa. La existencia de una identidad OPERATOR en Keycloak es una precondición administrativa; esta fase no agrega credenciales administrativas de Keycloak a Fleet. Registrar disponibilidad o activar una grúa no afirma que sea un candidato elegible: habilitación vigente, ubicación reciente, cobertura y compatibilidad se evaluarán en fase 6. Las restricciones por compromisos HELD/CONFIRMED se integrarán con las reservas en fase 7, usando un protocolo de bloqueo coherente para impedir cambios administrativos sobre recursos comprometidos. No hay un indicador editable o ficticio de ocupación en esta fase.

Se deshabilitó el logger SQL SqlExceptionHelper: los mensajes de restricciones PostgreSQL pueden incluir valores personales de la fila. Los conflictos esperados se representan mediante 409 sin volcar esos valores; los fallos inesperados conservan el registro controlado de tipo de excepción.

### Resultados observados

- Construcción Docker de Fleet satisfactoria; 12 pruebas de commons y 10 de Fleet (5 de seguridad y 5 de reglas del catálogo), sin fallos.
- 61 comprobaciones HTTP de administración de flota satisfactorias en la ejecución final, con tokens reales, Gateway y PostgreSQL. El número puede variar según exista previamente el perfil demo.
- Carrera de vinculación: dos grúas intentaron vincularse al mismo operador y se obtuvo exactamente un 200 y un 409.
- Repetición durable de un cambio operativo verificada antes y después de una transición posterior: se devolvió el mismo evento, sin revertir el estado actual.
- Las 22 comprobaciones de regresión de fase 3 pasaron con el nuevo GET /operators autorizado devolviendo 200.
- Reconstrucción/reinicio de Fleet: Hibernate validó el esquema y las cuatro tablas conservaron exactamente sus datos, comprobados mediante hashes ordenados antes/después, incluido el historial.
- git diff --check sin errores. No se modificaron diagramas ni se realizó commit o push. Los contenedores quedan iniciados.

Durante las pruebas se corrigió la lectura de un operador representado por proxy JPA al repetir su vínculo. También se alineó la precisión temporal de las respuestas con PostgreSQL para asegurar igualdad en reintentos. Son correcciones de esta fase; no modifican las reglas funcionales acordadas.

## Fase 5 — Vehículos, solicitudes y verificación Java — 7 de noviembre de 2026

### Revisión de fase 4 y correcciones

Se inició la infraestructura existente sin reemplazar volúmenes. Se migraron primero los casos de identidad, permisos y catálogo de Fleet a Java. La ejecución detectó que el DTO de ubicación de fase 4 admitía siete decimales, frente a los seis de AR-MVP-5; se corrigió la validación y se agregó un caso HTTP que exige 400 para siete decimales. La configuración y el modelo de datos existentes de Fleet se conservan.

Las pruebas automatizadas se implementan en Java con JUnit. KeycloakScopeSetupTest permite reparar la configuración local de identidad de forma optativa e idempotente. La colección Postman prevista para la fase 10 se mantiene.

### Funcionalidades incorporadas

DRIVER registra vehículos ACTIVE con patente normalizada y única, tipo CAR/SUV/PICKUP, peso de 500 a 3.000 kg y marca/modelo opcionales. Consulta y modifica exclusivamente los propios; los listados filtran por subject y registro, con paginación de 1 a 100 elementos. Un recurso ajeno y uno inexistente producen el mismo 404 genérico. La edición y el cambio de registro se rechazan si el vehículo tiene una solicitud activa.

La creación de solicitudes requiere vehículo propio ACTIVE, coordenadas mundiales válidas de hasta seis decimales, extremos distintos, movilidad y descripción. El estado inicial es REQUESTED. El servidor copia tipo y peso del vehículo, reglas completas y revisión AR-MVP-5; fija creación, intento inicial 1 y searchDeadline a 600 segundos de la creación mediante Clock. El cliente no puede enviar estado, propietario, snapshots, fechas ni asignaciones. La cobertura comercial y las rutas se evalúan en fase 6; una coordenada estructuralmente válida todavía no significa que el servicio sea viable.

Idempotency-Key es obligatorio, no vacío y de hasta 100 caracteres. La misma clave y contenido para un conductor recuperan la solicitud con 200; una creación nueva responde 201 y Location. Otra entrada con la misma clave devuelve 409. El fingerprint SHA-256 se calcula sobre campos normalizados y prefijados con su longitud, evitando ambigüedades por separadores en descripciones o escalas decimales equivalentes. La recuperación devuelve la representación actual del recurso, incluso si ya fue cancelado; no lo reabre.

DRIVER consulta solicitudes e historial propios; ADMIN tiene consulta global. OPERATOR recibe un listado vacío y 404 en detalles hasta disponer de asignaciones en fase 7: no se concede acceso global para suplir esa funcionalidad pendiente. Los listados permiten filtrar por estado y paginar. GET /configuration/assistance muestra las reglas efectivas sin secretos.

La cancelación exige propietario, motivo, Idempotency-Key y ausencia de primera aceptación. Solo admite REQUESTED, PENDING_EVALUATION, AWAITING_ACCEPTANCE o SEARCHING_ALTERNATIVE con firstAssignedAt nulo. Cambia a CANCELLED, fija cierre y motivo DRIVER_CANCELLED y persiste historial en la misma transacción. Repetir la misma operación recupera el evento original; cambiar el motivo con esa clave produce 409. firstAssignedAt nunca se borra para habilitar una cancelación. Sus escenarios futuros están cubiertos por pruebas unitarias; la aceptación real corresponde a fase 7.

### Persistencia y explicación del código

**Vehicle, AssistanceRequest y RequestStatusHistory.** @Entity define tres tablas en assistance_db. Vehicle almacena propietario y características actuales; AssistanceRequest referencia ese vehículo local y conserva sus datos declarados de forma independiente; RequestStatusHistory registra creación y cancelación con actor, instante y operación. Los UUID son propios de cada entidad; los subjects son referencias simples a Keycloak. Las relaciones JPA son LAZY, los enums se guardan como texto y @Version protege actualizaciones concurrentes. Los DTOs evitan serializar entidades o proxies.

**SQL.** db/001-assistance.sql crea las tablas, FK, índices y restricciones sin borrar datos. El índice único parcial sobre vehicle_id WHERE closed_at IS NULL impide dos solicitudes activas para un vehículo. La unicidad driver_subject/idempotency_key respalda la idempotencia. Los CHECK verifican estados, pesos, coordenadas y consistencia entre estado terminal y closedAt. El historial tiene unicidad por request_id/operation_key. El perfil Docker aplica este bootstrap aditivo antes de ddl-auto=validate. Como en Fleet, cambios posteriores de columnas necesitarán una migración explícita, no editar silenciosamente un CREATE TABLE IF NOT EXISTS.

**Repositorios.** JpaRepository proporciona persistencia y consultas paginadas. Las búsquedas de vehículos incluyen el propietario en SQL. Los bloqueos PESSIMISTIC_WRITE serializan las operaciones sobre el vehículo. La cancelación consulta primero su ID por proyección y luego bloquea vehículo y solicitud, en ese orden, para no actualizar una entidad leída antes del bloqueo. El mismo orden deberá conservarse al incorporar aceptación y compromisos.

**RequestService.** @Transactional delimita una escritura completa, incluyendo su historial. La creación bloquea el vehículo antes de recuperar la clave idempotente, validar estado y comprobar exclusividad. Así, dos solicitudes simultáneas con claves diferentes no pasan juntas el chequeo; con igual clave/cuerpo, la segunda recupera el resultado durable. Las fechas se obtienen del Clock inyectado y se normalizan a microsegundos para coincidir con PostgreSQL. Los logs de transición se emiten después del commit y solo incluyen ID y estados.

**RulesConfiguration.** Carga rules/mvp.yml, copia íntegra de los parámetros normativos, y serializa las reglas efectivas como JSON. Cada solicitud guarda ese texto y su revisión. Un cambio posterior del archivo o del vehículo no modifica solicitudes anteriores. Esta fase aplica el perfil temporal normal; los overrides demo se integran en fase 9. La prueba de snapshot valida también límites que se usarán en fases posteriores.

**RequestController y RequestDtos.** El controlador recibe JSON validado, extrae identidad del JWT y delega en el servicio. Los records contienen exclusivamente campos admitidos. Se rechazan propiedades desconocidas para impedir asignación de campos del servidor. Los errores se normalizan como ProblemDetail: 400 estructura inválida, 401 autenticación, 403 rol, 404 ámbito/recurso y 409 conflicto. Se agregaron descripciones, seguridad Bearer, respuestas de creación y esquemas OpenAPI. La especificación real se consulta por /docs/assistance/v3/api-docs.

### Pruebas Java y forma de ejecución

La guía [PRUEBAS_JAVA.md](PRUEBAS_JAVA.md) reemplaza los comandos anteriores. Las pruebas aisladas no necesitan Docker; las de integración se habilitan explícitamente:

```powershell
docker compose up -d --build
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.integration=true" "-Dtest=BackendIntegrationTest"
```

BackendClient usa HttpClient de Java 21 para realizar PKCE, obtener credenciales técnicas y enviar HTTP. Mantiene tokens en memoria y no los imprime. BackendIntegrationTest agrupa identidad/seguridad, administración Fleet, solicitudes/propiedad/concurrencia y persistencia tras reinicio. Vive en src/test de Gateway como suite transversal; no se agrega un quinto servicio ni se incluyen credenciales de prueba en los JAR de producción.

La prueba de propiedad crea un segundo DRIVER temporal en Keycloak y lo elimina al finalizar. El mantenimiento de esa identidad utiliza exclusivamente credenciales locales de la prueba; las aplicaciones no reciben permisos administrativos. Los datos de negocio de prueba quedan identificados como TEST/TST en PostgreSQL. La suite cancela sus solicitudes, deja las grúas nuevas inactivas/desvinculadas y el operador demo no disponible. El caso de persistencia reinicia Assistance y compara solicitud e historial completos.

RequestRulesTest verifica fechas con Clock fijo, snapshot independiente del vehículo, normalización de claves de entrada, fingerprint sensible al contenido y prohibición de cancelar después de la primera aceptación o durante ejecución/cierre. Las pruebas HTTP verifican PostgreSQL real y no reemplazan las restricciones de base con mocks.

### Límites y continuidad

La fase termina con solicitudes persistidas en REQUESTED; no se inventan evaluaciones ni asignaciones. La evaluación y ORS corresponden a fase 6. En fase 7 se incorporarán las asignaciones visibles al operador, las reservas y los controles adicionales por confirmación o liberación remota incierta. En fase 9 se automatizarán búsquedas y vencimientos; por ahora el deadline se conserva pero no hay tarea que cierre solicitudes por tiempo. Los diagramas y la línea base funcional AR-MVP-5 no se modificaron.

### Resultados de verificación de fase 5

- Construcción de las tres imágenes satisfactoria. Pruebas aisladas: commons 12, Fleet 10 y Assistance 12; 34 en total, sin fallos.
- BackendIntegrationTest ejecutó sus cuatro casos Java sin fallos, con 147 comprobaciones HTTP en la ejecución final. El total puede variar por las consultas de disponibilidad durante el reinicio y por la existencia previa del perfil demo.
- Se verificó firma JWT real, identidad PKCE, rol/audiencia, defensa interna, catálogo Fleet y su precisión de ubicación corregida.
- Concurrencia sobre PostgreSQL: distintas claves para un mismo vehículo produjeron 201/409; igual clave y contenido produjeron 201/200 y el mismo ID.
- Un segundo conductor real no pudo consultar ni modificar el vehículo o solicitud ajenos; los listados conservaron el ámbito propio. La identidad temporal fue eliminada al finalizar.
- Cancelaciones e historial idempotentes, snapshots inmutables y conservación de solicitud/historial después de reiniciar Assistance verificados.
- KeycloakScopeSetupTest también se ejecutó satisfactoriamente, confirmando la reparación Java idempotente de basic.
- Los casos de integración/reparación quedan omitidos por defecto en builds normales y Docker builds; su ejecución real exige las propiedades explícitas documentadas.
- git diff --check sin errores. No se modificaron diagramas ni se realizó commit o push. Los contenedores quedan iniciados.

## Fase 6 — Evaluación, rutas y colaboración — 8 de noviembre de 2026

### Revisión de fase 5 y correcciones

Antes de modificar el backend se ejecutaron satisfactoriamente los cuatro casos Java de integración de fase 5, incluyendo PostgreSQL y reinicio. Se conservaron propiedad, idempotencia, exclusividad por vehículo y cancelación. Se actualizaron sus expectativas porque ahora cada solicitud nueva incorpora una evaluación y otro evento de historial. Repetir una creación idempotente no repite la llamada externa.

Durante la integración de fase 6, la validación del esquema detectó que Hibernate interpretaba transportDistanceM y approachDistanceM con nombres distintos de las columnas SQL. Se agregaron nombres explícitos mediante @Column; el esquema y los datos existentes se conservaron. La clave proporcionada se incorporó únicamente al .env local ignorado por Git. Los diagramas se conservaron.

### Proceso incorporado

Tras crear y confirmar una solicitud, RequestController invoca EvaluationService fuera de la transacción de creación. Primero comprueba origen y destino dentro del círculo comercial de 15 km. Luego obtiene el traslado real con OpenRouteService: distancia entre 500 y 30.000 m inclusive y duración ajustada no mayor a 5.400 segundos. El factor 1,25 se aplica con BigDecimal y redondeo hacia arriba a segundos; las distancias se comparan sin redondear.

Assistance consulta candidatos mediante credenciales técnicas a Fleet. Fleet devuelve hasta cinco recursos activos, disponibles, compatibles, con ubicación de antigüedad máxima 300 segundos y habilitación vigente; preselecciona por Haversine y UUID. Assistance evalúa la aproximación vial, distancia máxima de 15 km y presupuesto de llegada de 2.700 segundos incluyendo despacho y margen. Calcula fin estimado con ventana restante de aceptación, despacho, aproximación, carga, traslado, descarga y contingencia. La habilitación debe vencer estrictamente después de ese fin. La vigencia de rutas y ubicación se revisa antes de producir el ranking.

Los candidatos elegibles se ordenan por menor aproximación ajustada, mayor tiempo disponible y UUID de grúa. Como los márgenes de llegada son iguales para todos, ese orden equivale al presupuesto de llegada. Si la grúa está exactamente en el origen, su aproximación es cero; previamente se exige una ruta de traslado válida que confirme ese origen. No se simulan distancias ante fallos externos.

Un incumplimiento confirmado de admisión produce solicitud REJECTED y libera su exclusividad mediante closedAt. La ausencia de ruta hacia un candidato solo lo descarta. Una falla técnica, cuota o falta de recursos conserva PENDING_EVALUATION. latestEvaluation diferencia VIABLE, SEARCHING, PENDING_DEPENDENCY y REJECTED e incluye motivos y candidatos. VIABLE no crea reservas ni asignación: estas pertenecen a fase 7.

### Clases y responsabilidades

- **GeoMath, en commons:** función Haversine reutilizada por ambos servicios. No contiene entidades ni contratos de negocio compartidos.
- **CandidateController/CandidateService, en Fleet:** endpoint interno GET /internal/fleet/candidates con parámetros latitude, longitude, vehicleType, weightKg y nonRollable. Exige token técnico FLEET_SERVICE/audiencia auxiruta-fleet; Gateway continúa bloqueando rutas internas. El repositorio carga operador y tipos admitidos mediante EntityGraph, evitando lecturas diferidas fuera de la transacción.
- **FleetClient, en Assistance:** cliente REST con DTO propio. Obtiene y reutiliza temporalmente el token client_credentials de assistance-service. No propaga el JWT humano ni accede a fleet_db.
- **RouteProvider y OpenRouteServiceClient:** separan el contrato necesario para evaluar del proveedor HTTP. Usan RestClient, perfil driving-car, coordenadas [longitud, latitud] y cabecera Authorization. Verifican geometría y ajuste máximo de 100 m a la red. Solo conservan métricas y fechas, no el JSON completo del proveedor.
- **EvaluationService:** aplica las reglas del snapshot de la solicitud y coordina llamadas. No mantiene una transacción de base abierta mientras espera la red.
- **EvaluationStore:** carga una entrada con versión/estado y guarda resultado, candidatos, transición e historial en una transacción corta. Antes de guardar bloquea y vuelve a verificar versión, estado y deadline. Una cancelación concurrente impide aplicar el resultado anterior; no se revive la solicitud.
- **EvaluationReadService:** devuelve la última evaluación y candidatos por DTO, conservando el control de acceso de RequestService. No expone credenciales ni datos personales del operador. Los esquemas OpenAPI públicos reflejan latestEvaluation.

### Persistencia y presupuesto de llamadas

Evaluation/evaluations conserva datos de entrada, reglas relevantes, traslado, resultado y motivos. CandidateEvaluation/candidate_evaluations conserva identificadores remotos, capacidad, habilitación, ubicación, métricas, elegibilidad, fin estimado y ranking. Las FK son locales a Assistance; no hay FK hacia Fleet o Keycloak. Las métricas viales usan numeric sin redondeo impuesto para preservar valores limítrofes. SQL se ejecuta de forma aditiva y Hibernate usa ddl-auto=validate.

RoutingBudget utiliza JdbcTemplate y transacciones para bloquear una fila única de routing_budget antes de reservar una llamada en routing_calls. Así dos evaluaciones no consumen el mismo último cupo. Se limita conservadoramente a 40 solicitudes en 60 segundos y 2.000 en una ventana móvil de 24 horas; no se supone reinicio a medianoche. Intentos fallidos también consumen el presupuesto local. La pausa y el contador persisten tras reiniciar Assistance.

La caché de rutas es local en memoria, con máximo de 500 entradas, coordenadas normalizadas, perfil, revisión y límite de ajuste como clave. Reutiliza solo resultados de hasta 300 segundos, nunca errores; un reinicio invalida la caché pero conserva el presupuesto. El traslado se consulta una sola vez por evaluación y se comparte entre candidatos.

### Fallos y límites de esta fase

Los clientes tienen timeout de conexión de 2 segundos y respuesta de 5 segundos, sin reintento inmediato. ORS 2009/2010 en respuestas 400/404 confirma ausencia de ruta/punto. Un 429 respeta Retry-After, con 60 segundos por defecto; 403 de cuota diaria utiliza reset o espera conservadora de 24 horas. Credenciales inválidas generan CONFIGURATION y pausa local de 300 segundos. Una respuesta desconocida o inválida permanece como dependencia fallida. Los errores y logs no incluyen clave, token ni cuerpo remoto.

La evaluación inicial es sincrónica; la solicitud ya existe antes de consultar dependencias. Si el proceso cae entre creación y evaluación, puede quedar REQUESTED: la recuperación programada se incorpora en fase 9. También quedan para esa fase búsquedas periódicas, reintentos diferidos y cierres por vencimiento. No hay endpoint humano para aprobar resultados. El límite local de cuota no contabiliza usos de la misma clave fuera de esta instalación; prevalecen las respuestas del proveedor.

Los parámetros del dominio permanecen en AR-MVP-5. La exclusión de recursos reservados se conectará en fase 7, cuando exista la reserva; hasta entonces una evaluación indica viabilidad sin comprometer recursos.

### Ejecución

Con .env configurado y Docker Desktop iniciado, ejecutar docker compose up -d --build. La guía PRUEBAS_JAVA.md contiene la regresión y la opción auxiruta.realRouting=true para exigir rutas reales. No es necesario volver a instalar commons al repetir tests, salvo que cambie la biblioteca; esta fase agregó GeoMath y por ello sí requirió su instalación inicial.

### Resultados de verificación de fase 6

- Pruebas Java aisladas: commons 12, Fleet 14 y Assistance 36; 62 casos aprobados. Assistance incluye pruebas HTTP locales del proveedor y controles de cuota sin acceso externo.
- BackendIntegrationTest: cinco casos aprobados y 162 comprobaciones HTTP en la ejecución final. Se verificaron permisos, flota, propiedad, idempotencia, concurrencia, cancelación y reinicio, además de la evaluación real.
- La evaluación real con Java obtuvo traslado de 1.736,6 m y aproximación de 1.057,6 m; conservó resultado VIABLE y candidato elegible con ranking 1. El destino fuera de cobertura produjo REJECTED/OUTSIDE_COVERAGE.
- Solicitud, historial, última evaluación y presupuesto de llamadas se conservaron tras reiniciar Assistance. La prueba dejó el operador demo no disponible y su grúa de prueba inactiva/desvinculada.
- Imágenes reconstruidas y aplicaciones iniciadas con validación SQL/JPA satisfactoria. .env está excluido tanto de Git como del contexto Docker; .env.example conserva la clave vacía.
- La documentación de ejecución, pruebas y avance se actualizó. Las cuotas locales son conservadoras respecto de la referencia pública; no se afirma que se haya inspeccionado el plan contratado de la cuenta.
- No se realizó commit ni push. Reservas/aceptación continúan en fase 7 y automatización en fase 9.

## Fase 7 — Reservas, propuestas y aceptación — 8 de noviembre de 2026

### Revisión de la fase anterior

Antes de modificar el sistema se ejecutó BackendIntegrationTest con integración real de rutas: cinco casos y 162 comprobaciones HTTP aprobados. Se conservaron los límites AR-MVP-5 y los diagramas. La regresión ahora espera AWAITING_ACCEPTANCE cuando hay candidato y reserva válidos, y comprueba liberación al cancelar. Los listados del operador pueden contener solicitudes históricas propias; la prueba de privacidad verifica que no aparezca una solicitud ajena, en lugar de exigir un listado globalmente vacío.

La documentación conserva instrucciones de ejecución exclusivamente en Java/JUnit y elimina menciones y comandos de herramientas de pruebas anteriores, según la decisión del equipo.

### Proceso implementado

Después de la evaluación, AssignmentCoordinator solicita a AssignmentStore un intento durable sobre el primer candidato elegible del ranking. La elección revisa antigüedad de rutas/ubicación, deadline, cupo y ausencia de otros compromisos locales. Una reserva denegada de forma durable permite avanzar al siguiente candidato; una respuesta incierta detiene esa secuencia. Solo las propuestas efectivamente emitidas consumen el cupo de cinco propuestas del intento de búsqueda.

Fleet revalida operación, disponibilidad, vínculo, capacidad, tipo, movilidad, cobertura, ubicación y habilitación. Crea HELD con vencimiento mínimo entre 60 segundos y searchDeadline. Un recurso no elegible o comprometido produce un intento terminal EXPIRED conservado en base, evitando que una petición tardía cree posteriormente esa misma reserva. Un mismo identificador con contenido distinto produce 409; una repetición idéntica devuelve su estado vigente y nunca revive un intento terminal.

Al recibir HELD, Assistance publica PROPOSED y pasa la solicitud a AWAITING_ACCEPTANCE. OPERATOR consulta exclusivamente sus propuestas/servicios, sus solicitudes asociadas y el historial. ADMIN puede consultar asignaciones por ID, pero no responder en nombre del operador. DRIVER no tiene acceso a los endpoints de asignación.

Aceptar registra primero una acción CONFIRM y la clave/huella de la respuesta. Fleet confirma solo antes del vencimiento y después de revalidar elegibilidad. Assistance persiste ACCEPTED, ASSIGNED y firstAssignedAt a partir de confirmedAt remoto. Confirmaciones repetidas conservan ese instante incluso cuando ya venció la antigua retención. Una confirmación rechazada libera/cierra el intento y devuelve la solicitud a evaluación pendiente.

Rechazar exige motivo de 10 a 500 caracteres. La respuesta queda REJECTED y se registra RELEASE. Hasta confirmar la liberación no se puede ofrecer otro recurso; después puede intentarse otro candidato del ranking vigente. Repetir la clave y contenido devuelve el resultado; cambiar respuesta, motivo o clave después de responder produce conflicto.

### Concurrencia y fallos

Fleet toma bloqueos en orden grúa, operador y reserva. Los índices únicos parciales sobre HELD/CONFIRMED refuerzan la exclusividad por ambos recursos. El estado de una reserva consultada antes de esperar bloqueos se refresca bajo bloqueo para no confirmar o liberar una versión obsoleta. Un compromiso confirmado no vence automáticamente.

Las modificaciones administrativas de capacidad, tipos, vínculo, estado operativo, habilitación, disponibilidad y registro verifican recursos libres bajo los mismos bloqueos locales. El filtro de candidatos excluye reservas activas. Actualizar ubicación continúa permitido.

En Assistance, aceptar y cancelar toman primero el vehículo y luego la solicitud. Si cancelar gana, no se crea una confirmación posterior y se compensa cualquier reserva en curso. Si existe CONFIRM pendiente o incierta, cancelar devuelve 409; debe resolverse el resultado remoto antes de decidir. Una solicitud cerrada con retención incierta conserva resourceReleasePending y bloquea nuevas solicitudes para su vehículo hasta confirmar la liberación.

No se mantiene una transacción abierta durante HTTP. Cada intención se confirma localmente, luego se ejecuta la llamada, y finalmente otra transacción aplica el resultado. Los fallos dejan PENDING/RETRYING; se conserva el ID y contenido originales. Recuperar una reserva incierta repite el PUT original, no interpreta un 404 como prueba de que nunca existirá. Una creación que llega después del cierre se compensa mediante RELEASE del mismo intento.

### Clases, tablas y aprendizaje del código

**ResourceReservation, ReservationRepository y ReservationService (Fleet).** La entidad JPA almacena intento, recursos, estado, fechas, contenido canónico y versión. El servicio implementa creación/recuperación, consulta, confirmación y liberación. ReservationController expone únicamente las rutas técnicas ya protegidas por FLEET_SERVICE. El script aditivo 002-reservations.sql incorpora resource_reservations, FK locales e índices de exclusividad.

**AssignmentStore (Assistance).** Mantiene propuestas y acciones durables en assignments y pending_actions, creadas por 003-assignments.sql. Usa JdbcTemplate para SQL explícito de estas operaciones y JPA para solicitud, evaluaciones e historial. Ambos participan en la misma transacción Spring y el mismo DataSource de assistance_db. Esta combinación permite observar las consultas y actualizaciones condicionales sin compartir entidades entre servicios. Los records View y Action son DTOs, no entidades ni tablas adicionales.

**AssignmentCoordinator.** Coordina llamadas remotas fuera de la transacción. Una respuesta perdida mantiene la acción pendiente; un resultado conocido habilita la siguiente transacción o compensación. recover reutiliza acciones persistidas, sin generar identificadores nuevos. Los bucles acotados recorren candidatos distintos o continúan compensaciones; no repiten inmediatamente una llamada fallida.

**AssignmentController.** Agrega listado paginado propio, detalle autorizado, aceptación y rechazo. Usa JWT para identidad, Idempotency-Key para respuesta y Bean Validation para el motivo. La documentación OpenAPI incluye estas operaciones. Los GET no ejecutan confirmaciones ni liberaciones.

**RequestService/RequestController.** Integran cancelación y recuperación de liberación, amplían visibilidad del operador a solicitudes vinculadas y exponen resourceReleasePending. La exclusividad por vehículo contempla también solicitudes terminales con limpieza remota pendiente. Los logs de transición se emiten después del commit y contienen IDs/estados/motivos, sin tokens ni secretos.

### Alcance restante

Esta fase llega a ASSIGNED. Despacho, ejecución y finalización normal corresponden a fase 8. Las acciones inciertas quedan durables y pueden recuperarse al repetir la respuesta idempotente o continuar una cancelación; el procesamiento periódico tras reinicios, vencimiento automático de propuestas, búsquedas programadas y perfil demo se conectan en fase 9. No se afirma que esa automatización ya esté activa. Los endpoints siguen comprobando vencimientos con Clock aunque ninguna tarea programada haya corrido.

La línea base AR-MVP-5 permanece sin cambios. Los contratos técnicos de revalidación de despacho y consultas agrupadas se incorporarán con sus consumidores de fases 8 y 9, respectivamente.

### Ejecución y verificación

La guía PRUEBAS_JAVA.md documenta las pruebas aisladas, la regresión y Phase7IntegrationTest. Para incorporar las tablas y aplicaciones ejecutar docker compose up -d --build; no se borran volúmenes. La suite específica se habilita con -Dauxiruta.phase7=true y usa datos locales de prueba.

### Resultados finales de fase 7

- Construcción y pruebas aisladas: commons 12, Fleet 22 y Assistance 39; 73 pruebas aprobadas sin fallos. Las imágenes finales se construyeron ejecutando sus verificaciones Maven.
- Regresión BackendIntegrationTest: cinco casos aprobados, con 161 comprobaciones HTTP en la ejecución final, incluida evaluación real ORS. El total de consultas de espera puede variar durante el reinicio.
- Phase7IntegrationTest: cinco casos aprobados sobre las imágenes finales, incluida concurrencia de reservas, carrera aceptación/cancelación, rechazo, aceptación durable y recuperación tras reinicio de una confirmación cuyo resultado no había sido aplicado localmente.
- La cancelación con confirmación pendiente devuelve 409 con code=ACCEPTANCE_CONFIRMATION_PENDING. La recuperación conserva el confirmedAt original como firstAssignedAt.
- La ventana mínima de propuesta se vuelve a comprobar al recibir la reserva: si ya quedan menos de 15 segundos, se libera sin publicar la propuesta. Al liberar un compromiso confirmado, el tiempo disponible del operador se reinicia; liberar una retención no aceptada no altera su antigüedad disponible.
- Se verificó git diff --check, exclusión de .env y ausencia de referencias a las herramientas de pruebas retiradas en README, docs e infra. Los diagramas no se modificaron.
- Las pruebas dejan el operador demo no disponible y sus recursos de prueba inactivos/desvinculados. Los contenedores quedan iniciados. No se realizó commit ni push.

## Fase 8 — Ejecución y finalización — 8 de noviembre de 2026

### Revisión de fase 7

Antes de implementar se ejecutaron los cinco casos de Phase7IntegrationTest sin fallos: exclusividad, aceptación/cancelación concurrentes, rechazo, aceptación durable y recuperación de confirmación tras reinicio. Se preservaron los cambios locales existentes, las reglas AR-MVP-5 y los diagramas. La implementación reutiliza los bloqueos, historial, reserva confirmada y acciones RELEASE durables de fase 7.

No fue necesario modificar las reglas de la fase anterior. Las pruebas y documentación permanecen en Java/JUnit.

### Funcionalidades y endpoints

El operador de la asignación vigente registra cinco POST sobre /api/v1/assistance-requests/{id}: dispatch, arrival, loading, transport-start y completion. Todos exigen JWT válido, rol OPERATOR, pertenencia a la solicitud mediante la asignación e Idempotency-Key. ADMIN y DRIVER no pueden ejecutar estas acciones. Un operador sin vínculo recibe 404; no se permite declarar acciones sobre servicios ajenos.

La secuencia válida es ASSIGNED → DISPATCHED → ARRIVED → VEHICLE_LOADED → IN_TRANSIT → COMPLETED. Cada acción exige su predecesor y rechaza saltos, repeticiones con otra clave o alteraciones de contenido. Repetir la misma clave y huella recupera el evento original, incluso después de que el servicio avance. No vuelve a consumir API externa para un despacho ya registrado.

Completion exige note de 10 a 500 caracteres después de quitar espacios extremos. Se guardan nota, fecha de entrega, cierre de asignación, cierre de solicitud y acción RELEASE en una única transacción. Después se invoca Fleet fuera de esa transacción. Una caída remota no revierte una entrega ya declarada: resourceReleasePending conserva la limpieza pendiente y bloquea otra solicitud del mismo vehículo. Repetir la entrega recupera la liberación sin duplicar historia.

### Revalidación antes del despacho

ReservationService.eligibility consulta el recurso de la reserva confirmada bajo bloqueos coherentes; no busca una grúa libre para reemplazarlo. Comprueba reserva, vínculo, operación, disponibilidad, compatibilidad, cobertura, ubicación y habilitación. Devuelve identidad de recursos, coordenadas, fechas y resultado mediante GET /internal/reservations/{id}/eligibility, accesible solo al cliente técnico de Assistance. No confirma ni libera reservas mediante GET.

DispatchValidator usa el snapshot de reglas de la solicitud. Requiere ubicación de antigüedad máxima 120 segundos, rutas de hasta 300 segundos y los mismos límites de traslado/aproximación. Conserva el margen de partida de 300 segundos en la planificación de habilitación, con aceptación restante cero. La habilitación debe vencer estrictamente después del nuevo fin estimado; el margen no extiende el plazo de salida.

La salida debe registrarse antes de acceptedAt + 300 segundos de la asignación vigente. Se valida antes de consultar dependencias y otra vez al guardar. La evidencia de rutas y ubicación también se vuelve a comprobar después de recuperar los bloqueos locales. Incluye coordenadas, fechas remotas, distancias, duraciones ajustadas, vencimiento de habilitación y fin estimado en details del historial. No se guarda el JSON completo del proveedor ni credenciales.

Un incumplimiento confirmado produce 409 sin avanzar. Timeout, cuota, respuesta inválida o indisponibilidad producen 503 y no habilitan una salida a ciegas. Los tiempos estimados de carga/descarga no obligan a esperar: las acciones representan declaraciones del operador sobre tareas realizadas. Después de DISPATCHED no se reevalúa para cancelar o sustituir recursos; se conserva el compromiso hasta la entrega.

### Código y persistencia

**ExecutionController.** Define endpoints concretos y un DTO Completion con validación y normalización de nota. Obtiene el subject del JWT y nunca recibe propietario, estado o fechas arbitrarios. OpenAPI publica los esquemas y descripciones.

**ExecutionStore.** Delimita transacciones locales. Step describe los estados anterior/siguiente y la columna de fecha de cada acción; la columna SQL se obtiene únicamente de ese enum. prepare bloquea vehículo y solicitud, comprueba ámbito, idempotencia, asignación y estado, y devuelve una entrada independiente de la entidad JPA. commit vuelve a comprobar versión y asignación después de las llamadas remotas, y guarda transición/historial/fechas de forma atómica. Los logs de transición se emiten tras el commit.

**DispatchValidator.** Realiza las consultas de Fleet y RouteProvider fuera de la transacción. La evidencia incluye límites de vigencia para comprobar que una espera por bloqueo no permita aplicar datos vencidos. Los conflictos de dominio y fallos técnicos se distinguen sin exponer mensajes remotos.

**ExecutionService.** Coordina preparación, revalidación y commit. En completion continúa con AssignmentCoordinator.recover para ejecutar la liberación durable. Una repetición de completion puede recuperar RELEASE aunque el evento original ya exista.

**004-execution.sql.** Agrega dispatched_at, arrived_at, loaded_at, transport_started_at, completed_at y completion_note a assignments sin borrar datos. El historial mantiene actor, estado anterior/nuevo, instante, clave y huella. No se agrega una base de datos ni una entidad compartida entre microservicios.

Fleet libera la reserva sin reactivar ni cambiar el estado operativo de la grúa. Al liberar un compromiso confirmado actualiza la antigüedad disponible del operador, según lo implementado en fase 7. Los índices de exclusividad dejan de ocupar el recurso cuando la reserva pasa a RELEASED.

### Límites respecto de la fase 9

Esta fase permite completar el recorrido principal. Si el despacho vence o pierde elegibilidad, la acción devuelve conflicto y conserva el estado actual; la búsqueda alternativa automática, sus cupos, los cierres por plazo y los procesos periódicos se incorporan en fase 9. Ante una dependencia temporalmente caída, tampoco se extiende el plazo de salida.

Las liberaciones pendientes son durables y recuperables repitiendo la entrega idempotente. Todavía no hay un scheduler que las procese solo después de reiniciar. La cola y los contratos existentes quedan preparados para esa siguiente fase. No hay intervención de ADMIN para aprobar excepciones.

### Ejecución

Actualizar con docker compose up -d --build. La guía PRUEBAS_JAVA.md documenta pruebas aisladas y Phase8IntegrationTest, habilitada con -Dauxiruta.phase8=true. Esta suite detiene/reinicia Fleet para verificar el fallo real y debe ejecutarse sin otra demostración simultánea. Los recorridos completados normalmente se conservan para inspección; los fixtures incompletos se limpian de manera acotada.

### Resultados finales de fase 8

- Pruebas Java aisladas aprobadas: commons 12, Fleet 22 y Assistance 51; 85 en total, sin fallos. Los casos nuevos cubren estados, idempotencia, deadline exclusivo, vigencia inclusiva de ubicación, habilitación estricta, rechazo de reserva inválida y fallos técnicos sin transición.
- Phase8IntegrationTest: tres casos aprobados sobre las imágenes finales. Se completó el recorrido con rutas reales, se verificaron historial único, nota, liberación y vehículo nuevamente editable. ADMIN/DRIVER fueron rechazados y un segundo operador real no asignado recibió 404; la identidad temporal se eliminó al finalizar.
- La ubicación antigua y el plazo de despacho vencido produjeron 409 sin avanzar. La prueba de caída real de Fleet conservó COMPLETED con resourceReleasePending y bloqueó otra solicitud del mismo vehículo. Tras reiniciar Fleet, repetir la entrega original liberó sin duplicar el evento.
- Phase7IntegrationTest pasó antes de comenzar y después de la implementación: cinco casos sin fallos en la ejecución final, incluida recuperación de confirmación incierta tras reiniciar Assistance.
- Se conservaron las solicitudes completadas normalmente y su historial. Los fixtures incompletos se limpiaron de manera acotada. Los contenedores quedan iniciados y el operador demo queda no disponible con sus grúas de prueba inactivas/desvinculadas.
- README, plan de fases, guía de pruebas, infraestructura y este registro se actualizaron. git diff --check no detectó errores. .env sigue ignorado; no se modificaron diagramas ni se realizó commit o push.

## Fase 9 — Automatización y recuperación completa — 8 de noviembre de 2026

### Verificación de la fase anterior y correcciones

Antes de los cambios, los tres escenarios de integración de fase 8 pasaron. La recuperación automática exigió adaptar sus expectativas: un despacho vencido puede iniciar una alternativa y la liberación puede concluir de manera asíncrona. Se conservan los controles de autorización, propiedad, idempotencia y orden del recorrido.

Se corrigió la paginación del cliente de pruebas para encontrar propuestas después de cien registros y se acotó la limpieza para impedir que el scheduler siga buscando recursos para sus fixtures. La limpieza resuelve una reserva incierta repitiendo el PUT original antes de liberarla. Se añadió compatibilidad con los payloads persistidos antes de incorporar los tiempos de reserva.

En la integración apareció una recuperación incorrecta del Gateway tras parar Fleet y reiniciar Assistance. Se limitó a diez segundos la caché DNS del cliente, sin caché negativa, para renovar las direcciones de los servicios en Docker.

### Código y comportamiento construido

- `AutomationJobs` separa la recuperación de acciones y el avance del negocio en dos tareas programadas. El polling detecta trabajo vencido; las fechas persistidas y los snapshots determinan cuándo corresponde actuar. Las reconciliaciones consultan Fleet por lotes.
- `AutomationStore` aplica decisiones locales dentro de transacciones: plazos de búsqueda y propuesta, cupos, cierre y reemplazo previo al despacho. Bloquea vehículo y solicitud antes de decidir. El nuevo intento comienza solo después de liberar el compromiso anterior. Registra cada transición en el historial con actor SYSTEM.
- `AssignmentStore` guarda `failure_count` y `next_attempt_at`. Una reclamación temporal impide ejecutar simultáneamente la misma acción; tras una caída queda recuperable. `AssignmentCoordinator` realiza HTTP fuera de la transacción y reintenta a los 5, 15, 30 y luego 60 segundos, sin descartar incertidumbres por agotar intentos. Credenciales inválidas se difieren y los límites remotos respetan Retry-After.
- `FleetClient` consulta antes de volver a confirmar y preserva el instante original confirmado por Fleet. Un GET ausente no autoriza a olvidar una reserva incierta: se repite el PUT original y luego se compensa si la solicitud terminó.
- `ReservationJobs` vence retenciones HELD. Nunca vence automáticamente una reserva CONFIRMED. El contrato interno de consulta de estados permite reconciliar hasta cien intentos por lote.
- `EvaluationStore` reclama temporalmente la evaluación para evitar que el scheduler y una petición inicial la ejecuten a la vez. Las fechas técnicas de planificación no alteran la versión optimista del negocio. Al reiniciar se recuperan las solicitudes abiertas; el despliegue soportado es una instancia por servicio.
- `RulesConfiguration` aplica el perfil demo y guarda su versión y valores en el snapshot. Fleet recibe los tiempos del intento; así una solicitud existente conserva sus reglas al cambiar el perfil.
- Las migraciones SQL son aditivas. No requieren borrar datos ni reinicializar las bases. La variable `AUXIRUTA_AUTOMATION_ENABLED`, activa por defecto, permite aislar diagnósticos técnicos.

### Reglas preservadas

Cinco ofertas por intento, dos reemplazos previos al despacho, cancelación únicamente antes de la primera aceptación y ninguna reasignación automática desde DISPATCHED. Una propuesta vencida no consume por sí sola un reemplazo de servicio aceptado. Una confirmación incierta bloquea cierre y cancelación hasta resolverla; una solicitud terminal con liberación pendiente mantiene bloqueado su vehículo. No se agregan notificaciones ni decisiones operativas humanas.

La guía PRUEBAS_JAVA.md documenta el perfil demo y los siete escenarios integrales. README, plan, infraestructura y parámetros de referencia reflejan la implementación. La colección Postman y la entrega integral siguen perteneciendo a fase 10.

### Resultados de verificación de fase 9

- Pruebas Java aisladas: commons 12, Fleet 24 y Assistance 62; 98 aprobadas, sin fallos. Las imágenes Docker finales compilan y validan los esquemas existentes.
- Phase9IntegrationTest: siete escenarios aprobados con perfil demo y ORS real. La recuperación tras detener Fleet y reiniciar Assistance funcionó sin reiniciar el Gateway ni repetir la entrega. Se verificaron los cinco ofrecimientos, los dos reemplazos, expiración y compensación de incertidumbres.
- Regresión Phase7IntegrationTest: cinco escenarios aprobados. Phase8IntegrationTest: tres escenarios aprobados con perfil normal. La suite general BackendIntegrationTest aprobó cuatro escenarios y 148 comprobaciones HTTP; su quinto caso optativo de rutas no fue habilitado en esta ejecución. Las suites de fases 7, 8 y 9 sí utilizaron rutas reales.
- El perfil final de los contenedores es docker, con automatización activa. Los cinco contenedores quedan iniciados. Los recursos de prueba fueron liberados; el operador demo queda no disponible y las grúas de prueba inactivas/desvinculadas. Se conservan filas e historiales para inspección.
- Documentación arquitectónica 2.6: refleja implementación y perfil demo sin cambiar la línea base AR-MVP-5. No se modificaron diagramas. .env continúa ignorado; no se imprimieron credenciales ni se realizó commit o push.
## Fase 10 — Verificación y entrega — 9 de noviembre de 2026

### Integridad de fase 9

Se inició Docker Desktop, se levantó el perfil demo y se ejecutaron los siete escenarios de Phase9IntegrationTest antes de modificar la entrega. Todos aprobaron: búsquedas, expiración, cupos, dos reemplazos y recuperación durable tras reinicios. Se restauró el perfil normal para la demostración pública. Se preservaron los cambios de fases anteriores que estaban sin commit.

### Construcción y explicación del código

- `DeliveryArtifacts` define el recorrido de demostración mediante una lista de pasos Java. Cada paso declara rol, método, URL, cuerpo, HTTP esperado, capturas de IDs, condiciones observables y claves de idempotencia. Genera la colección Postman y el entorno vacío; no contiene secretos ni IDs fijos de datos existentes.
- `DeliveryArtifactsTest` compara el JSON versionado con la definición Java y exige 47 solicitudes reales dentro de las carpetas. Detecta que un cambio en los pasos no haya sido exportado. Se ejecuta sin Docker. El Dockerfile de Gateway copia los JSON públicos al build para ejecutar esta comprobación; no los empaqueta en la imagen final.
- `DeliveryIntegrationTest` lee las solicitudes del JSON publicado, sustituye variables y las ejecuta con el cliente Java existente. Comprueba códigos, estados, rutas reales, idempotencia, historial único y liberación. Las consultas asíncronas esperan un resultado acotado; no modifican los deadlines del negocio. La paginación busca la propuesta por solicitud, por lo que el historial de demostraciones anteriores no impide encontrarla.
- La misma suite consulta las dos especificaciones OpenAPI, compara todos los mappings públicos de los controladores y verifica autenticación, resúmenes y respuestas. Por defecto compara contra los contratos versionados; solo `auxiruta.export=true` permite actualizarlos deliberadamente.
- `DemoDataSetupTest` prepara mediante APIs el perfil de operator.demo, lo crea si falta y renueva su habilitación. Rechaza una preparación sobre una grúa vinculada en lugar de retirar recursos ajenos. No crea una vía administrativa de excepción ni cambia las reglas del dominio.
- `Start-AuxiRuta.ps1` valida Compose, construye o reutiliza imágenes y espera disponibilidad HTTP protegida. `Verify-AuxiRuta.ps1` coordina Maven y las suites Java en serie; restaura el perfil normal después de las pruebas demo. Los scripts son herramientas operativas, no implementaciones alternativas de reglas o pruebas de negocio.
- `docs/ENTREGA.md` reúne requisitos, arranque, identidades, preparación, PKCE en Postman, orden de ejecución, escenarios, regeneración de contratos, recuperación ante fallos y exclusiones. Los contratos privados se documentan separadamente en `docs/api/INTEGRACION_INTERNA.md` y permanecen inaccesibles por Gateway.

### Correcciones durante la verificación

La descripción OpenAPI de Assistance todavía indicaba que la integración de rutas estaba pendiente. Se actualizó y se añadieron las respuestas de error comunes a los controladores de asignación y ejecución. No cambió ningún permiso ni endpoint de negocio.

El primer generador copiaba arreglos vacíos al crear las carpetas, por lo que su exportación no contenía solicitudes. Se corrigió el orden de construcción y se añadió el conteo obligatorio de 47 pedidos tanto en el artefacto como en su ejecución Java. También se corrigió la longitud de las patentes generadas para respetar el máximo normalizado de diez caracteres.

La grúa de demostración se ubica en un punto distinto del origen para verificar una aproximación vial positiva, además del traslado. Se corrigió la limpieza de la prueba: una ejecución exitosa ya deja los recursos libres e inactivos por las propias APIs y no debe intentar repetir un cambio de estado con otra clave. La limpieza técnica acotada se reserva para fixtures interrumpidos.

### Material entregado

La colección contiene cinco carpetas y 47 pasos con casos positivos y negativos. Los access tokens del entorno se entregan vacíos. Los JSON OpenAPI describen todos los endpoints públicos; la integración privada conserva sus DTOs independientes. El README, el plan de fases y la documentación arquitectónica se sincronizan con la entrega, sin modificar AR-MVP-5 ni los diagramas.

Los scripts de Postman utilizan el lenguaje JavaScript exigido por su sandbox para encadenar peticiones y mostrar comprobaciones interactivas. Las pruebas automatizadas mantenidas en el repositorio se implementan en Java. Los comandos de preparación y verificación no necesitan instalar dependencias de otro lenguaje.

La construcción aislada del Gateway en Docker también detectó diferencias CRLF/LF en los cuerpos de las peticiones generadas. Se normalizaron a LF para que la comparación del artefacto sea reproducible entre Windows y Linux. La detección de raíz del proyecto usa los módulos Maven, sin depender de que Compose se copie dentro de la etapa de construcción.

### Resultados finales de fase 10

- Verify-AuxiRuta.ps1 -Integration -IncludeDemo finalizó correctamente: 99 pruebas aisladas aprobadas (commons 12, Fleet 24, Assistance 62 y coherencia de colección 1), además de 22 escenarios integrales (base con ORS real 5, fase 7: 5, fase 8: 3, entrega: 2 y fase 9: 7). Las suites optativas se omiten deliberadamente en builds ordinarios y se habilitan en sus pasos de integración.
- OpenAPI verificó los 38 endpoints públicos: 18 de Fleet y 20 de Assistance. Los JSON exportados coinciden con los contratos vivos y todos los mappings públicos de los controladores.
- La colección se ejecutó además con Newman 6.2.3: 48 llamadas, incluidas las consultas adicionales de paginación, y 111 aserciones aprobadas, sin fallos. Se detectó y corrigió un conflicto con la variable global data del sandbox; los scripts ahora se ejecutan en su propio ámbito. Esta comprobación adicional utilizó un lanzador temporal Java y tokens efímeros; el lanzador y el entorno temporal se eliminaron. Newman no es una dependencia para compilar ni para ejecutar el backend o su suite Java.
- El perfil final es docker, con los cinco contenedores iniciados. La preparación y las pruebas conservaron el volumen, historiales y recursos identificados como fixtures; los recursos de prueba terminaron libres, las grúas inactivas/desvinculadas y el operador demo no disponible.
- La guía ENTREGA.md, README, plan de fases, guía de pruebas, infraestructura y decisiones arquitectónicas 2.7 documentan la entrega. No se modificó AR-MVP-5, no se tocaron los diagramas y no se realizó commit ni push. .env permanece ignorado y los archivos publicados no incluyen tokens ni claves reales.

## Auditoría posterior a fase 10 — 9 de noviembre de 2026

Se contrastó AR-MVP-5 y las decisiones finales del chat con el código. La verificación aislada aprobó 99 pruebas; no se repitió integración en esta revisión. AUDITORIA_CONFORMIDAD.md registra tres diferencias funcionales de prioridad alta y una brecha de pruebas. Los hallazgos están pendientes: no se modificó código productivo ni se cambió la línea base para acomodar la implementación. La finalización de las fases no implica conformidad total mientras sigan abiertos.

## Ajuste de ubicación acordado el 9 de noviembre de 2026

La ubicación informada se considera fija hasta la salida. Su vigencia de 300 segundos, inclusiva, se comprueba al seleccionar, reservar y confirmar una propuesta. Superado ese plazo, el operador debe volver a enviar coordenadas para recibir o aceptar propuestas. Una asignación ya aceptada conserva esa posición sin caducidad por antigüedad: no se exige actualizar para despachar ni se libera por ese motivo. Permanecen vigentes la comprobación de rutas, la habilitación y el plazo independiente de 300 segundos para declarar el despacho. Este ajuste sustituye la restricción previa de ubicación de 120 segundos; no elimina los controles de ubicación para una futura solicitud.
Se separó la elegibilidad de reserva/confirmación de la elegibilidad de un compromiso confirmado en Fleet. DispatchValidator mantiene las fechas de vigencia de rutas sin usar antigüedad de posición. Se añadieron pruebas de ubicación antigua aceptada y rechazo de confirmación con más de cinco minutos. El hallazgo H2 de la auditoría queda atendido por este cambio; H1 y H3 no se declaran cerrados.

## Ubicación bloqueada durante propuesta o servicio — 9 de noviembre de 2026

El operador puede informar coordenadas mientras su grúa esté libre. Desde que Fleet crea una retención HELD, y durante el compromiso CONFIRMED, la actualización devuelve 409 sin modificar coordenadas ni locationUpdatedAt, incluso si se envían las mismas coordenadas. Se asume permanencia en la posición informada hasta el despacho. El bloqueo comparte el bloqueo transaccional de grúa con la reserva para evitar una carrera entre ofrecer y actualizar.

Si el operador rechaza una propuesta, o esta vence o el conductor cancela antes de aceptación, se vuelve a permitir informar ubicación cuando Fleet confirma la liberación o materializa la expiración. Mientras la liberación sea incierta no se permite actualizar. El operador rechaza propuestas: no se incorpora una cancelación operativa posterior a aceptar. Una grúa libre puede actualizar su ubicación aunque todavía tenga vigencia; superar 300 segundos exige actualizarla para nuevas propuestas y confirmaciones.

Este ajuste elimina el cambio de coordenadas entre propuesta y aceptación identificado en H3. No demuestra por sí mismo la vigencia temporal de las rutas cacheadas: ese borde de la auditoría sigue pendiente de verificación específica. H1 continúa pendiente.
Se agregó freeTruck bajo la transacción de FleetService.location. La prueba comprueba 409, conservación de posición/fecha y nueva actualización después de liberar. La colección conserva 47 pasos y ahora espera 409 en el paso 28; la prueba de fase 8 se ajustó al mismo contrato.

## Evaluación conservada durante la propuesta — 9 de noviembre de 2026

Por decisión explícita del usuario, la ruta debe estar vigente al emitir la propuesta y conserva su validez durante toda su ventana de aceptación, sin recalcular por superar luego los 300 segundos. Se asumen posición y condiciones constantes; la posición está bloqueada por Fleet mientras exista HELD/CONFIRMED. No se extienden los plazos de propuesta o búsqueda. La ubicación debe seguir dentro de sus cinco minutos al confirmar y la habilitación debe ser válida. El despacho mantiene su revalidación independiente de rutas.

AssignmentStore.respond ya confirmaba sin solicitar nuevas rutas; se documentó esta decisión en el código y se sustituyó la regla anterior en la especificación consolidada y el documento de reglas. Se agregó una prueba Java de integración que envejece exclusivamente la evidencia de su fixture mientras la propuesta permanece vigente, comprueba el bloqueo de coordenadas, acepta y verifica que no aumenten las llamadas registradas a ORS. No se cambia la hora del sistema ni se agregan endpoints de simulación.

Esta simplificación sustituye la obligación anterior de recalcular al aceptar y atiende el borde temporal de H3. H1 permanece pendiente; el cierre de regresión requiere ejecutar los escenarios correspondientes.
Validación del ajuste: reconstrucción Docker completada y prueba Phase7IntegrationTest#activeProposalKeepsItsRouteEvidenceUntilAcceptance aprobada contra los contenedores reales. La aceptación mantuvo la evidencia de más de 300 segundos sin aumentar routing_calls y el cambio de ubicación devolvió 409. No se ejecutó la suite integral completa en este ajuste; H1 sigue abierto y la regresión global permanece pendiente.

## Rechazo y vencimiento de despacho — 9 de noviembre de 2026

El rechazo explícito excluye al operador de nuevas propuestas para esa misma solicitud, en el intento actual y los posteriores, aunque cambie de grúa. No lo excluye de otras solicitudes. La exclusión usa el registro durable response_kind=REJECT, independiente de que luego se cierre la asignación.

El vencimiento del despacho no constituye rechazo: una vez confirmada la liberación, el mismo operador puede competir en el siguiente intento si mantiene disponibilidad, habilitación, compatibilidad y ubicación vigente. No obtiene una asignación garantizada ni prioridad especial. Se conservan los cupos de propuestas y los dos reemplazos máximos, y la primera aceptación sigue impidiendo cancelar.

La prueba de cinco ofertas se adapta a propuestas vencidas, ya que cinco rechazos del mismo operador no son válidos bajo la regla nueva. Se agrega una prueba que conserva un rechazo real y prepara el segundo intento para comprobar la exclusión. La prueba de dos reemplazos verifica el caso permitido de reutilización tras vencer despacho.
### Resultado de verificación de H1

Las tres pruebas seleccionadas de Phase9IntegrationTest aprobaron el 9 de noviembre de 2026: rejectingOperatorIsExcludedInNextAttempt, twoReassignmentsThenCloseWithoutRestoringCancellation y fiveExpiredOffersExhaustTheSearchWithoutChangingDeadline. Verifican exclusión del operador que rechaza al preparar un segundo intento, reutilización permitida después de vencer el despacho y conservación del cupo de cinco ofertas. El primer escenario prepara el segundo intento mediante SQL sobre su fixture; no pretende reproducir por sí solo todo el recorrido de un segundo operador.

H1 queda atendido según la regla explícitamente reformulada por el usuario. La regresión integral conjunta de todos los ajustes posteriores a la auditoría sigue pendiente; estos tres resultados no se presentan como una ejecución de toda la suite. No se modificaron los límites de cinco ofertas ni de dos reemplazos.

## Cierre de regresión conjunta — 9 de noviembre de 2026

Se ejecutó `scripts/Verify-AuxiRuta.ps1 -Integration -IncludeDemo` con resultado satisfactorio. Aprobaron 102 pruebas aisladas (12 Commons, 27 Fleet, 62 Assistance y 1 de artefactos) y 24 pruebas de integración: BackendIntegrationTest (5), Phase7IntegrationTest (6), Phase8IntegrationTest (3), DeliveryIntegrationTest (2) y Phase9IntegrationTest (8). Las suites optativas omitidas en la compilación inicial se ejecutaron posteriormente con sus indicadores correspondientes; no se cuentan omisiones como aprobaciones.

La ejecución incluyó los contenedores reales, autenticación con Keycloak, consulta real a ORS, concurrencia de aceptación/cancelación, recuperación, ejecución del traslado, automatización y los 47 pasos de la colección publicados, ejecutados por el verificador Java. También se contrastaron los contratos OpenAPI. No se ejecutó Newman en esta regresión.

H1, H2 y H3 quedan atendidos respecto de las reglas reformuladas explícitamente y documentadas en los apartados anteriores. H4 queda atendido mediante las pruebas adicionales de exclusión por rechazo, vigencia de ubicación y conservación de evidencia durante la propuesta, junto con esta regresión conjunta. La prueba de exclusión prepara el segundo intento mediante SQL sobre su fixture; no representa por sí sola una ejecución completa con dos operadores.

Al finalizar se restituyó el perfil normal docker. Los cinco contenedores quedaron en ejecución, con cero reservas HELD/CONFIRMED, cero grúas TEST vinculadas y cero acciones remotas pendientes. No fue necesario modificar código productivo durante esta regresión. Los resultados cierran el pendiente de verificación conjunta, sin constituir una garantía de ausencia de defectos fuera de los escenarios cubiertos.
