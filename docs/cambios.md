# Cambios y guía de aprendizaje

Este documento conserva el historial de construcción por fases. Desde fase 5, las instrucciones vigentes de pruebas están en [PRUEBAS_JAVA.md](PRUEBAS_JAVA.md); las menciones a Python de fases anteriores son históricas y sus scripts fueron retirados. Cada fase revisa la anterior y los componentes afectados; registra correcciones, código incorporado, verificaciones y pendientes.

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
- **infra/tests/verify_identity.py:** prueba de integración con biblioteca estándar Python, independiente del backend Java. Verifica emisión real de tokens técnicos y humanos, claims y rechazo de credenciales incorrectas. Automatiza el formulario de login local, genera un code_verifier aleatorio y su desafío SHA-256, y canjea el código con PKCE. El token no se imprime ni se guarda. La política de cookies admite HTTP exclusivamente para esta prueba de localhost, que reproduce el comportamiento de navegador en desarrollo; no se utiliza como cliente de producción.

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

La validación real de JWT detectó una omisión de la fase 2: los tokens humanos tenían audiencia y roles correctos, pero no incluían `sub`. Se agregó el scope predeterminado `basic` a ambos clientes en `infra/keycloak/auxiruta-realm.json`. El script idempotente `infra/keycloak/ensure_basic_scope.py` aplica la misma corrección al realm existente mediante la API administrativa local, sin eliminar usuarios, credenciales o bases. `verify_identity.py` comprueba ahora también que `sub` exista. No se debilitó el validador para aceptar usuarios sin identificador.

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
python infra/tests/verify_security.py
```

`verify_security.py` reutiliza el login de `verify_identity.py`: recibe tokens reales, consulta OpenAPI por ambos destinos y comprueba rechazos por falta de token, rol, audiencia y firma alterada. Las llamadas directas dentro de Docker comprueban la protección propia de los microservicios. Los tokens permanecen en memoria y se pasan a curl por stdin; no aparecen en argumentos ni archivos.

### Límites de la fase

Las rutas están protegidas y enrutadas, pero sus controladores de negocio aún no existen: una ruta autorizada puede devolver 404. La prueba positiva de extremo a extremo utiliza documentos OpenAPI reales; no se agregaron endpoints ficticios de producción. Los filtros de listas por propietario y los chequeos contra la base se implementarán junto con vehículos, operadores y solicitudes. El contrato OpenAPI funcional se ampliará con esos controladores. La interfaz Swagger UI con login, las llamadas de negocio Assistance → Fleet, los logs de transiciones y la integración ORS pertenecen a fases posteriores. No se modificaron diagramas ni reglas funcionales.


### Resultados de verificación

- Compilación de los cuatro módulos y construcción de las tres imágenes Docker satisfactorias.
- 21 pruebas Java: 12 de commons, 5 de seguridad MVC de Fleet y 4 de Assistance, sin fallos. Gateway se verifica mediante las pruebas HTTP sobre su contenedor real.
- 22 comprobaciones HTTP satisfactorias con tokens emitidos por Keycloak: documentos OpenAPI 200 para los tres roles; 401 sin token, firma alterada o audiencia incorrecta; 403 por rol no autorizado; 404 para rutas autorizadas todavía sin controlador.
- En la red privada, Fleet rechaza tokens humanos para endpoints internos y admite el cliente técnico hasta la resolución del recurso; Assistance rechaza esa audiencia técnica. Ambos aplican permisos aunque la petición no pase por Gateway.
- La emisión de identidad exige ahora `sub`; los tres logins PKCE y el rechazo de un secreto técnico inválido se repitieron después de corregir el realm.
- `.env`, `target/` y cachés Python excluidos de Git. Diagramas conservados. No se realizó commit ni push.


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
python infra/tests/verify_security.py
python infra/tests/verify_fleet.py
```

verify_fleet.py realiza login real con PKCE y usa Gateway, Fleet y PostgreSQL. Mantiene datos identificados con patentes TEST; no borra filas de auditoría. Al finalizar deja las grúas creadas INACTIVE y sin vínculo y el operador demo UNAVAILABLE. Debe ejecutarse en el entorno local de pruebas: utiliza el perfil operator.demo y rechaza desvincular una grúa cuya patente no tenga el prefijo de prueba. Cada ejecución agrega grúas de prueba nuevas. No imprime ni guarda tokens.

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

Por decisión del equipo, todas las pruebas automatizadas mantenidas se escriben ahora en Java. Se eliminaron infra/tests/verify_identity.py, verify_security.py y verify_fleet.py después de comprobar sus escenarios equivalentes en JUnit. También se reemplazó la reparación ensure_basic_scope.py por KeycloakScopeSetupTest, optativo e idempotente. No se necesita Python para ejecutar, probar o preparar el backend. La colección Postman prevista para la fase 10 se mantiene.

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
- git diff --check sin errores; no quedan archivos fuente Python en el repositorio. No se modificaron diagramas ni se realizó commit o push. Los contenedores quedan iniciados.

## Fase 6 — Evaluación, rutas y colaboración — 8 de noviembre de 2026

### Revisión de fase 5 y correcciones

Antes de modificar el backend se ejecutaron satisfactoriamente los cuatro casos Java de integración de fase 5, incluyendo PostgreSQL y reinicio. Se conservaron propiedad, idempotencia, exclusividad por vehículo y cancelación. Se actualizaron sus expectativas porque ahora cada solicitud nueva incorpora una evaluación y otro evento de historial. Repetir una creación idempotente no repite la llamada externa.

Durante la integración de fase 6, la validación del esquema detectó que Hibernate interpretaba transportDistanceM y approachDistanceM con nombres distintos de las columnas SQL. Se agregaron nombres explícitos mediante @Column; el esquema y los datos existentes se conservaron. La clave proporcionada se incorporó únicamente al .env local ignorado por Git. No se incorporaron pruebas Python, ni se modificaron los diagramas.

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
