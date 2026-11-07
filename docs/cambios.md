# Cambios y guía de aprendizaje

Este documento conserva el historial de construcción por fases. Cada fase revisa la anterior y los componentes afectados; registra correcciones, código incorporado, verificaciones y pendientes.

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
