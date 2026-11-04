# AuxiRuta: contexto y decisiones del proyecto

Backend de microservicios para coordinar asistencia y remolque de vehículos livianos. Trabajo Práctico Integrador de Backend de Aplicaciones, 2026.

Este documento cumple el mismo propósito que el archivo de contexto de SkyClear: reunir las decisiones técnicas para que el equipo y las herramientas de asistencia trabajen con una base común. Describe un proyecto por implementar; no acredita que exista código, infraestructura funcionando o contratos OpenAPI terminados.

## Documentos de referencia y precedencia

La documentación actual está en docs/documentacionPartes/, junto a este archivo. Las imágenes C1 y C2 están en docs/diagramas/. Las referencias a documentos de esta tabla se resuelven dentro de documentacionPartes; los enlaces del README parten de la raíz del repositorio.

| Documento actual | Contenido |
|---|---|
| AuxiRuta.md | Resumen del dominio y división funcional |
| AuxiRuta-reglas-de-negocio-MVP.md | Reglas AR-MVP-5 y restricciones fijas |
| AuxiRuta-parametros-MVP.yml | Valores de referencia para configurar los servicios |
| AuxiRuta-estados-y-transiciones.md | Estados de solicitud, vehículo, grúa, reserva |
| AuxiRuta-operaciones-por-rol-y-alcance-MVP.md | Roles, endpoints propuestos y alcance |
| AuxiRuta-modelo-persistente-MVP.md | Entidades, tablas y precisiones de AR-MVP-5 |
| AuxiRuta-logging-basico.md | Logging básico incorporado por decisión del equipo |

Referencia consolidada vigente: [DECISIONES_ARQUITECTONICAS.md](../DECISIONES_ARQUITECTONICAS.md). Sus decisiones prevalecen ante contradicciones con estos documentos parciales. Este archivo se conserva como guía de contexto auxiliar; el YAML de parámetros y los contratos deben mantenerse alineados con la especificación consolidada.

Los contratos OpenAPI todavía deben construirse. Ya existen imágenes C1 y C2; deben revisarse y mantenerse alineadas con las operaciones y decisiones del proyecto. Una discrepancia se resuelve actualizando explícitamente diseño, contrato o implementación, sin inventar una funcionalidad para taparla.

## 1. Nombre y coordenadas Maven

- Sistema: AuxiRuta.
- groupId propio: com.auxiruta.
- Versión inicial de los artefactos: 1.0.0.
- Paquetes base: com.auxiruta.fleet, com.auxiruta.assistance, com.auxiruta.gateway y com.auxiruta.commons.
- Esta elección fija com.auxiruta y reemplaza la coordenada ilustrativa ar.edu.utn.auxiruta mencionada en el chat de dependencias.

| Carpeta | artifactId | Tipo y responsabilidad |
|---|---|---|
| auxiruta-commons/ | auxiruta-commons | Biblioteca Maven normal compartida por ambos servicios funcionales |
| fleet-service/ | fleet-service | Spring Boot MVC: operadores, grúas, compatibilidad, disponibilidad y reservas |
| assistance-service/ | assistance-service | Spring Boot MVC: vehículos, solicitudes, evaluación de rutas, asignación |
| api-gateway/ | api-gateway | Spring Boot con Spring Cloud Gateway WebFlux: entrada y control de acceso |

Cada carpeta es un proyecto Maven independiente con pom.xml propio. Se elige no incorporar un agregador multimódulo al inicio por simplicidad; no es una prohibición del enunciado. La independencia del JAR común exige publicarlo/instalarlo y consumirlo como dependencia, pero no impide por sí misma tener un padre o agregador Maven.

Los proyectos Boot utilizan spring-boot-starter-parent 3.5.16. auxiruta-commons puede utilizar su gestión de dependencias mediante parent o BOM, pero no incluye spring-boot-maven-plugin ni main ejecutable.

Los tres ejecutables usan finalName=app para producir target/app.jar. La biblioteca conserva auxiruta-commons-1.0.0.jar y se instala primero con mvn clean install. Los servicios la consumen como com.auxiruta:auxiruta-commons:1.0.0.

## 2. Versiones y dependencias

Se mantiene la base académica propuesta para trabajar cerca de los apuntes de Spring Boot 3. Estas son versiones fijadas para comenzar, no una afirmación de que sean las últimas ni de que la combinación ya se haya probado en un arranque integral.

| Componente | Decisión |
|---|---|
| Java | 21 LTS, distribución Temurin; registrar el parche exacto al preparar el entorno |
| Maven | 3.9.11 mediante Maven Wrapper por proyecto |
| Spring Boot | 3.5.16 en Gateway y ambos servicios |
| Spring Cloud BOM | 2025.0.3 en Gateway |
| Gateway | spring-cloud-starter-gateway-server-webflux; versión que resuelva ese BOM |
| Spring MVC, Data JPA, Security, Hibernate y Jackson | Versiones administradas por Spring Boot; sin overrides independientes |
| springdoc-openapi | 2.10.0, módulo webmvc-ui en servicios funcionales |
| PostgreSQL | 17.11 |
| Keycloak | 26.7.5 |
| Docker Desktop/Engine y Compose | Versiones estables del entorno de desarrollo; registrar las versiones exactas al instalar, sin fingir que ya están fijadas |
| auxiruta-commons | 1.0.0 |

Boot 3.5 ya terminó su soporte open source. Su elección es académica por alineación con los materiales. Un cambio a Boot 4 exige revisar en conjunto starters, Spring Cloud, springdoc y serialización; no se mezclan ejemplos de ambas generaciones.

### Dependencias de ambos servicios funcionales

| groupId | artifactId | Uso / alcance |
|---|---|---|
| org.springframework.boot | spring-boot-starter-web | REST/MVC, JSON y RestClient |
| org.springframework.boot | spring-boot-starter-data-jpa | Hibernate, JpaRepository y transacciones |
| org.postgresql | postgresql | Driver, scope runtime |
| org.springframework.boot | spring-boot-starter-validation | Validación de DTOs |
| org.springframework.boot | spring-boot-starter-oauth2-resource-server | Validación JWT y autorización |
| org.springdoc | springdoc-openapi-starter-webmvc-ui | OpenAPI/Swagger, versión 2.10.0 |
| com.auxiruta | auxiruta-commons | Biblioteca común, versión 1.0.0 |

Assistance agrega org.springframework.boot:spring-boot-starter-oauth2-client para obtener el token técnico de Fleet. Fleet no necesita cliente OAuth2 ni cliente de Assistance. RestClient no requiere un starter adicional sobre Spring Web.

Gateway incluye org.springframework.cloud:spring-cloud-starter-gateway-server-webflux y org.springframework.boot:spring-boot-starter-oauth2-resource-server. No se agrega starter-web/MVC ni JPA al Gateway reactivo. No necesita OAuth2 Client para reenviar un bearer token ya recibido y validado.

Commons utiliza solamente lo necesario para sus clases: spring-web para ProblemDetail, spring-security-core para authorities y spring-security-oauth2-jose para Jwt. Sus versiones se administran con Boot. No incluye un starter de servidor ni entidades/repositorios de negocio. Los adaptadores de seguridad servlet/reactivos quedan en sus aplicaciones.

Lombok, DevTools y dependencias de tests son opcionales según su uso. El requisito es verificar el funcionamiento de la solución; no se transforma lo visto en tests en una nueva obligación de la cátedra.

No se incluyen en el compromiso base Actuator, Resilience4j, Flyway/Liquibase, Kafka/RabbitMQ, Eureka, Config Server ni plataformas de logs. Tampoco se usa un adaptador Java específico de Keycloak: se integra con los estándares OAuth2/OIDC y JWT.

## 3. Estructura futura del repositorio

```text
auxiruta/
├── README.md
├── docker-compose.yml
├── .env.example
├── .gitignore
├── .dockerignore
├── .gitattributes
├── infra/
│   ├── postgres/init/
│   │   ├── 01-init-databases.sh
│   │   ├── assistance-schema.sql
│   │   └── fleet-schema.sql
│   └── keycloak/realm-auxiruta.json
├── docs/
│   ├── documentacionPartes/
│   │   ├── AuxiRuta-contexto-proyecto.md
│   │   ├── AuxiRuta-reglas-de-negocio-MVP.md
│   │   ├── AuxiRuta-parametros-MVP.yml
│   │   └── ...otros documentos actuales
│   ├── api/                           [pendiente]
│   │   ├── fleet-service.yaml          [pendiente]
│   │   └── assistance-service.yaml     [pendiente]
│   └── diagramas/
│       ├── AuxiRuta_C1_C2.md  # vigente
│       ├── AuxiRuta_C1.png    # histórico
│       └── AuxiRuta_C2.png    # histórico
├── auxiruta-commons/
│   ├── pom.xml
│   └── src/
├── fleet-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
├── assistance-service/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
└── api-gateway/
    ├── Dockerfile
    ├── pom.xml
    └── src/
```

El árbol es un diseño; las carpetas ejecutables y su contenido todavía no están creados. Se elige un Dockerfile por ejecutable, en lugar del Dockerfile parametrizado del compañero. La biblioteca común se compila antes de empaquetar los servicios.

## 4. Servicios, puertos y rutas

| Componente | Puerto dentro del contenedor | Puerto publicado al host en entrega |
|---|---|---|
| api-gateway | 8000 | 8000 |
| Keycloak | 8080 | 8080, para autenticación y administración local |
| fleet-service | 8081 | Ninguno |
| assistance-service | 8082 | Ninguno |
| PostgreSQL | 5432 | Ninguno |

http://localhost:8000 es la URL local de negocio. Estos puertos resuelven los ejemplos anteriores que usaban 8080 para un Gateway genérico. En ejecución local sin Docker se usan los mismos puertos internos; la exposición directa de servicios se reserva al desarrollo.

En el Gateway, los servicios públicos conservan el prefijo /api/v1, sin reescritura innecesaria:

| Prefijos públicos | Destino |
|---|---|
| /api/v1/vehicles/** | Assistance |
| /api/v1/assistance-requests/** | Assistance |
| /api/v1/assignments/** y /api/v1/operator/assignments | Assistance |
| /api/v1/operators/** y /api/v1/tow-trucks/** | Fleet |
| /api/v1/configuration/assistance | Assistance |
| /api/v1/configuration/fleet | Fleet |

Las rutas exactas y sus métodos están en el documento de operaciones. La seguridad distingue método HTTP, ruta y rol; el servicio verifica además propietario/asignación.

Rutas /internal/**: nunca se exponen por Gateway. Se rechazan explícitamente y el mapa público no incluye comodines que las reescriban hacia un endpoint interno. Las comunicaciones Assistance → Fleet usan http://fleet-service:8081/internal/....

Swagger y /v3/api-docs se enrutan expresamente bajo rutas documentales separadas para cada servicio y usuarios autenticados, sin exponer operaciones internas. No se agrega una API pública de monitoreo.

## 5. Seguridad

### Realm y roles

Realm: auxiruta.

Roles humanos de realm: DRIVER, OPERATOR y ADMIN. Se agrega un permiso técnico de realm FLEET_SERVICE, que no se asigna a usuarios humanos. Ese permiso no constituye un cuarto actor del negocio.

Los roles llegan en realm_access.roles y se transforman en authorities ROLE_*. La extracción reutilizable se implementa en auxiruta-commons; la adaptación a Authentication servlet o reactiva se hace en cada aplicación. Claims ausentes no conceden permisos.

### Clientes de Keycloak

| Cliente | Tipo | Uso |
|---|---|---|
| auxiruta-api | Público | Postman/Swagger; Authorization Code con PKCE S256, sin secreto embebido |
| assistance-service | Confidencial, service account | client_credentials para acceder a los endpoints internos de Fleet |

Se configuran redirect URIs concretas para las herramientas utilizadas. Password grant no se habilita como mecanismo base. No se construye login propio ni se guardan contraseñas en Assistance/Fleet.

Audiencia humana prevista: auxiruta-api. Audiencia del token técnico: auxiruta-fleet. Se configuran los audience mappers correspondientes en Keycloak. Gateway y Assistance aceptan la audiencia humana; Fleet admite la audiencia humana para sus rutas públicas y la técnica para las internas, con comprobaciones adicionales de rol/cliente en cada grupo de rutas.

### Comunicación

- Cliente → Gateway → servicio: bearer token humano recibido, validado y reenviado. El subject determina la identidad.
- Assistance → Fleet: nuevo token técnico con client_credentials, sin depender de una sesión de usuario. Se gestiona con Spring Security OAuth2 Client y RestClient.
- Tareas de búsqueda, limpieza y reconciliación usan ese mismo cliente técnico.
- Fleet no llama a Assistance. Assistance consulta estado de reservas y recursos.
- Gateway y ambos servicios validan firma, emisor, fechas y audiencia del JWT. Los microservicios controlan rol y pertenencia.
- /internal/** en Fleet exige FLEET_SERVICE y cliente técnico esperado; el rol ADMIN por sí solo no habilita esa interfaz.

El registro de cliente OAuth2 en Assistance se llama fleet-client. El flujo técnico se obtiene de KEYCLOAK_TOKEN_URI; no usa discovery contra un localhost inaccesible dentro de Docker. El cliente tiene permisos acotados a candidatos, reservas y elegibilidad.

### Emisor y Docker

Configuración prevista para la demostración local, pendiente de verificación al implementar:

```text
KC_HOSTNAME=http://localhost:8080
KC_HOSTNAME_BACKCHANNEL_DYNAMIC=true

KEYCLOAK_ISSUER_URI=http://localhost:8080/realms/auxiruta
KEYCLOAK_JWK_SET_URI=http://keycloak:8080/realms/auxiruta/protocol/openid-connect/certs
KEYCLOAK_TOKEN_URI=http://keycloak:8080/realms/auxiruta/protocol/openid-connect/token
```

Cada resource server configura issuer-uri y jwk-set-uri, mantiene validación exacta de iss y obtiene claves por la red interna. Ambas propiedades permiten evitar el discovery del emisor público para obtener esa ubicación. Las audiencias se validan explícitamente.

Si se declara un JwtDecoder propio, debe conservar también validadores de emisor, fechas y audiencia: no se asume que las propiedades sigan actuando automáticamente sobre un bean personalizado.

Esta configuración es local; para acceso desde otro equipo se cambia de forma coherente la URL pública y las redirecciones. Si falla al probar, se corrige hostname/red/configuración; no se elimina la comprobación de issuer como solución alternativa.

## 6. Base de datos y persistencia

Un contenedor PostgreSQL 17.11 con volumen persistente:

| Base | Usuario de aplicación | Dueño del modelo |
|---|---|---|
| assistance_db | assistance_user | Assistance: vehículos, solicitudes, evaluaciones, asignaciones y recuperación |
| fleet_db | fleet_user | Fleet: operadores, grúas, reservas e historial operativo |
| keycloak_db | keycloak_user | Keycloak administra sus propias tablas |

Usuarios separados, privilegios sobre su propia base y revocación explícita de accesos públicos innecesarios en la inicialización. No se utilizan credenciales de superusuario desde las aplicaciones. El usuario de inicialización de PostgreSQL es independiente.

Scripts SQL controlados para las bases funcionales y spring.jpa.hibernate.ddl-auto=validate. Los scripts se ejecutan al crear un volumen vacío; para actualizar una base existente se documenta y ejecuta el SQL de actualización correspondiente. No se promete que reiniciar Compose migre un esquema ya poblado.

Flyway del ejemplo no se incorpora: quedó fuera del MVP. Keycloak mantiene sus propias migraciones. No hay claves foráneas ni consultas SQL cruzadas entre servicios.

Entidades y tablas vigentes: ver AuxiRuta-modelo-persistente-MVP.md, con campos y restricciones AR-MVP-5. Relaciones locales principalmente N→1 LAZY, enums como texto y referencias externas como UUID simples.

Las reservas requieren exclusividad real con transacciones, bloqueo y restricciones adecuadas. @Version no evita por sí solo dos inserciones concurrentes. Nunca se mantiene un bloqueo de base mientras se espera a OpenRouteService.

## 7. Variables de entorno y configuración

Cada application.yml utiliza variables para infraestructura y secretos. En Docker, los servicios se buscan por nombre; localhost dentro de un contenedor no es otro servicio.

| Variable | Aplicación | Valor de referencia en Docker |
|---|---|---|
| SERVER_PORT | Cada ejecutable | 8081, 8082 o 8000 |
| DB_URL | Fleet/Assistance | jdbc:postgresql://postgres:5432/fleet_db o assistance_db |
| DB_USER | Fleet/Assistance | fleet_user o assistance_user |
| DB_PASSWORD | Fleet/Assistance | Secreto externo, sin valor real en Git |
| KEYCLOAK_ISSUER_URI | Gateway y ambos servicios | http://localhost:8080/realms/auxiruta |
| KEYCLOAK_JWK_SET_URI | Gateway y ambos servicios | http://keycloak:8080/realms/auxiruta/protocol/openid-connect/certs |
| KEYCLOAK_TOKEN_URI | Assistance | http://keycloak:8080/realms/auxiruta/protocol/openid-connect/token |
| KEYCLOAK_CLIENT_ID | Assistance | assistance-service |
| KEYCLOAK_CLIENT_SECRET | Assistance | Secreto de su cliente confidencial |
| FLEET_URL | Assistance | http://fleet-service:8081 |
| FLEET_URI | Gateway | http://fleet-service:8081 |
| ASSISTANCE_URI | Gateway | http://assistance-service:8082 |
| ORS_BASE_URL | Assistance | URL vigente del proveedor verificada al integrar |
| ORS_API_KEY | Assistance | Clave externa; no se registra ni versiona |
| POSTGRES_ADMIN_PASSWORD | Inicialización de PostgreSQL | Secreto independiente de los usuarios de aplicación |
| ASSISTANCE_DB_PASSWORD | Inicializador/Assistance | Contraseña de assistance_user |
| FLEET_DB_PASSWORD | Inicializador/Fleet | Contraseña de fleet_user |
| KEYCLOAK_DB_PASSWORD | Inicializador/Keycloak | Contraseña de keycloak_user |
| KC_DB | Keycloak | postgres |
| KC_DB_URL | Keycloak | jdbc:postgresql://postgres:5432/keycloak_db |
| KC_DB_USERNAME | Keycloak | keycloak_user |
| KC_DB_PASSWORD | Keycloak | Mismo secreto de KEYCLOAK_DB_PASSWORD, inyectado por Compose |

Compose traduce las variables comunes del .env a DB_PASSWORD de cada servicio. Las credenciales de administrador inicial de Keycloak se suministran externamente según la versión elegida y se documentan al crear el Compose.

.env.example contiene nombres y valores ficticios; .env no se versiona. Las variables sensibles se exigen al arrancar, sin contraseñas de respaldo en el código.

Reglas: @ConfigurationProperties con validación de valores. AuxiRuta-parametros-MVP.yml es una referencia que debe distribuirse/importarse en la configuración efectiva, no se carga automáticamente por estar dentro de docs. Los servicios que comparten una restricción reciben el mismo valor AR-MVP-5. No hay edición de reglas mediante API.

Valores principales: radio 15 km desde (-31.4167, -64.1833); vehículos CAR/SUV/PICKUP de 500 a 3.000 kg; aproximación <=15 km por calles; traslado 500 m a 30 km; búsqueda 10 min; hasta 5 propuestas; reserva <=60 s; 2 reasignaciones; despacho antes de 5 min. Fórmulas, excepciones y límites completos están en el documento de reglas.

## 8. Convenciones técnicas y de trabajo

1. Arquitectura Controller → Service → Repository. Clients encapsula RestClient y DTOs externos; jobs ejecuta vencimientos/reconciliación usando los mismos servicios de negocio.
2. Inyección por constructor. DTOs preferentemente como records. Entradas con Bean Validation y respuestas con ResponseEntity.
3. Entidades JPA locales, enums STRING e Instant UTC. Las fechas se muestran en America/Argentina/Cordoba. Clock inyectado en lógica temporal permite verificar vencimientos sin alterar el reloj del sistema.
4. Errores con ProblemDetail, código funcional estable y Advice. Las excepciones de negocio específicas pertenecen a cada servicio. El formato y las utilidades reutilizables pueden ir a commons.
5. Seguridad por ruta/método y comprobación de propietario/asignación en Service. No basta aplicar una anotación de rol y omitir pertenencia.
6. SecurityFilterChain en MVC; SecurityWebFilterChain en Gateway. No mezclar filtros servlet con la cadena reactiva. La lógica del negocio permanece en los microservicios.
7. Límites en configuración, no constantes repartidas por controllers. Cada decisión conserva el snapshot necesario para justificarla.
8. Desarrollo por recorridos pequeños y verificables: entidad/repositorio/regla/endpoint y demostración funcional. Mantener un servicio compilable al avanzar.
9. No presentar clases, bases, contratos, realm o diagramas como implementados si solo están diseñados. Registrar fallos y pendientes con precisión.
10. No agregar tecnologías ajenas al alcance sin una necesidad justificada y una decisión explícita del equipo. Actuator y monitoreo siguen opcionales.
11. Logging básico incluido por decisión del equipo: SLF4J/Logback a consola, IDs de solicitud/asignación/reserva, niveles y retención Docker. Ver AuxiRuta-logging-basico.md. Sin tablas adicionales, Actuator ni stack externo obligatorio. El historial persistente sigue separado.
12. Cada cambio relevante actualiza las reglas, contratos y C2 afectados. El equipo debe poder explicar y modificar el código presentado.
13. Scripts .sh con LF en Windows; .gitattributes declara *.sh text eol=lf. .gitignore excluye target/, .env y archivos de IDE apropiados.
14. Probar idempotencia, concurrencia, plazos y recuperación con casos funcionales; los tests automatizados se incorporan cuando aporten evidencia útil, sin inflar el compromiso académico.

Este archivo contiene decisiones del proyecto, no impone pedir aprobación para cada edición rutinaria. El uso como GEMINI.md, AGENTS.md u otro archivo de instrucciones se decide al configurar la herramienta; la entrega actual es un documento de contexto y no instala reglas en un repositorio.

## 9. Diferencias respecto del ejemplo y decisiones anteriores

| Tema | Decisión de AuxiRuta |
|---|---|
| Dominio | Asistencia vehicular y reservas exclusivas, sin pilotos ni planes de vuelo |
| Coordenadas Maven | com.auxiruta y cuatro artefactos propios |
| API externa | OpenRouteService; sin Open-Meteo ni Overpass |
| Cobertura | Círculo comercial de 15 km, reemplaza el rectángulo de la primera propuesta |
| Servicio técnico | Cliente Assistance → Fleet y rol FLEET_SERVICE |
| Retorno de cambios de Fleet | Sondeo desde Assistance, sin callback inverso |
| Migraciones | SQL controlado + validate; Flyway opcional, no incluido |
| Resiliencia | Timeouts y recuperación idempotente; Resilience4j opcional |
| Seguridad de issuer | Se conserva validación; no se utiliza el fallback de desactivarla |
| Infraestructura | Dockerfile por ejecutable; infraestructura todavía pendiente |
| Monitoreo | Opcional, sin agregarlo como criterio del TPI |
| Multimódulo | No se utiliza inicialmente por decisión propia; no se atribuye su prohibición a la cátedra |
| Estado del trabajo | Diseño y documentos disponibles; todavía sin backend implementado |

El ejemplo de SkyClear se utiliza como referencia de organización. Sus instrucciones para una IA, su estado de avance, versiones alternativas y cambios de documentos no se aplican automáticamente a AuxiRuta.

## 10. Estado real y próximos pasos

- [x] Dominio, microservicios y roles definidos.
- [x] Reglas AR-MVP-5 y valores concretos documentados.
- [x] Estados, operaciones y alcance del MVP documentados.
- [x] Modelo persistente propuesto, con ajustes por reglas fijas.
- [x] Decisiones técnicas de este documento preparadas para implementar.
- [ ] Construir contratos OpenAPI de ambos servicios y revisarlos contra las reglas.
- [x] Archivos C1 y C2 disponibles en docs/diagramas/.
- [ ] Revisar su correspondencia con el diseño final y completar el diagrama de datos.
- [ ] Crear los proyectos Maven y comprobar resolución de dependencias/versiones.
- [ ] Implementar auxiruta-commons e instalarlo en .m2.
- [ ] Preparar PostgreSQL, SQL de inicialización y realm de Keycloak; verificar emisión/validación de JWT con emisor y audiencias.
- [ ] Implementar un primer recorrido protegido de Fleet y enrutarlo por Gateway.
- [ ] Implementar vehículo y solicitud en Assistance y la consulta autenticada a Fleet.
- [ ] Implementar OpenRouteService, evaluaciones y reserva/aceptación exclusivas.
- [ ] Completar ejecución, cancelación y reasignación automática únicamente antes del despacho.
- [ ] Implementar vencimientos, acciones pendientes y reconciliación.
- [ ] Implementar el logging básico según AuxiRuta-logging-basico.md.
- [ ] Completar Dockerfiles/Compose, documentación y colección funcional; verificar arranque reproducible con volumen vacío y reinicio conservando datos.

Pendientes técnicos que no deben darse por probados: parche exacto de Temurin, versiones del entorno Docker/Compose, URL vigente y cuotas de la cuenta OpenRouteService, configuración real de audiencias/redirect URIs, mecanismo SQL/JPA de exclusividad y pasos de actualización del esquema con datos existentes.

## Referencias técnicas utilizadas

- [Matriz de compatibilidad Spring Cloud / Boot](https://spring.io/projects/spring-cloud/).
- [Starter Gateway WebFlux 4.3](https://docs.spring.io/spring-cloud-gateway/reference/4.3/spring-cloud-gateway-server-webflux/starter.html).
- [Dependencias administradas por Boot 3.5](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html).
- [Springdoc para Boot 3](https://springdoc.org/v2/).
- [JWT y validación del emisor](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
- [Hostname público y backchannel de Keycloak](https://www.keycloak.org/server/hostname).

Las páginas oficiales pueden mostrar por defecto una generación posterior de Spring; el código concreto debe contrastarse con la documentación de la línea 6.5/Boot 3.5 elegida antes de implementarlo.

## Automatización operativa AR-MVP-5

Cada reasignación posterior a una aceptación abre automáticamente un nuevo intento de búsqueda de 600 s y hasta cinco propuestas. Se admiten dos reasignaciones, es decir, tres intentos totales contando el inicial. Los rechazos anteriores a aceptar no consumen reasignación. No hay endpoint humano para iniciar una búsqueda alternativa ni seleccionar su candidato.

Antes de despachar se revalidan reserva confirmada, estado operativo, ubicación, rutas y habilitación. El vencimiento de los 300 s para despachar o la pérdida confirmada de elegibilidad antes del despacho genera una búsqueda alternativa, previa liberación. Una falla temporal no extiende el plazo ni habilita el despacho a ciegas.

Si se agotan las reasignaciones, vence la búsqueda o se agotan sus propuestas sin aceptación, la solicitud termina UNFULFILLED con motivo. Una confirmación remota incierta se reconcilia antes de concluir que no existe aceptación válida. La nueva búsqueda comienza al confirmar la liberación anterior; su plazo no se consume durante esa limpieza técnica.

Una vez en DISPATCHED no se reasigna ni se cancela el servicio: el operador registra llegada, carga, traslado y entrega. Se conservan el origen, el destino y los datos declarados.

El sistema opera para una única empresa. El alta de operadores requiere identidad en Keycloak, rol OPERATOR, perfil en Fleet, habilitación y vínculo con grúa administrados. No hay registro libre de prestadores. ADMIN administra recursos libres y consulta; no dirige servicios ni fuerza estados. Los diagramas vigentes son [C1 y C2](../diagramas/AuxiRuta_C1_C2.md).
