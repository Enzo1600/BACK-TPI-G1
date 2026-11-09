# Infraestructura local de AuxiRuta

## Primer arranque

1. Iniciar Docker Desktop y esperar a que el motor esté disponible.

2. Copiar `.env.example` a `.env` si todavía no existe. Reemplazar todos los valores `CAMBIAR_...` por secretos locales distintos. El entorno creado durante la fase 2 ya tiene un `.env` con valores aleatorios; no sobrescribirlo.

3. Desde la raíz del repositorio ejecutar:

```powershell

docker compose config --quiet

docker compose up -d --build

docker compose ps

docker compose logs --tail 50 keycloak fleet-service assistance-service api-gateway

```

Los Dockerfiles compilan con Java 21 y Maven Wrapper en una etapa de construcción. Instalan commons dentro de esa etapa; no dependen del `.m2` del equipo. La etapa final contiene JRE y JAR y utiliza un usuario sin privilegios. Las imágenes Temurin se fijan a Java 21/Jammy; el digest y parche efectivamente verificados se registran en cambios.md. Los parches del tag pueden evolucionar al descargarlo nuevamente.

PostgreSQL y los microservicios no publican puertos. Keycloak se accede por http://localhost:8080 y Gateway por http://localhost:8000, ambos ligados al loopback. Gateway protege y enruta el catálogo de Fleet y los vehículos/solicitudes de Assistance. Una petición sin token recibe 401.

## Identidades de desarrollo

| Usuario | Rol | Contraseña en .env |

|---|---|---|

| driver.demo | DRIVER | DEMO_DRIVER_PASSWORD |

| operator.demo | OPERATOR | DEMO_OPERATOR_PASSWORD |

| admin.demo | ADMIN de negocio | DEMO_ADMIN_PASSWORD |

| bootstrap-admin | Administrador inicial de Keycloak | KC_BOOTSTRAP_ADMIN_PASSWORD |

Estas identidades son datos de desarrollo, no registros de operadores en Fleet. No se comparten permisos de administración de Keycloak con ADMIN de negocio.

Cliente humano: `auxiruta-api`, público, Authorization Code con PKCE S256. Authorization URL: `http://localhost:8080/realms/auxiruta/protocol/openid-connect/auth`; token URL: la misma base terminada en `/token`. Callback Postman admitido: `https://oauth.pstmn.io/v1/callback`. También se permite `http://127.0.0.1:8765/callback` para la prueba local. No se habilita password grant.

Cliente técnico: `assistance-service`, client_credentials, secreto KEYCLOAK_CLIENT_SECRET, audiencia auxiruta-fleet y permiso FLEET_SERVICE. Los clientes humanos reciben audiencia auxiruta-api y solo sus roles humanos. El emisor es público; las aplicaciones obtienen claves por DNS interno de Compose.

## Verificación de identidad

Con JDK 21, el JAR común instalado en .m2 y el entorno Docker iniciado:

```powershell

.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.integration=true" "-Dtest=BackendIntegrationTest"

```

La suite usa JUnit 5 y HttpClient de Java 21. Hace login con los tres usuarios mediante Authorization Code + PKCE y obtiene un token técnico. Comprueba issuer, audiencias y roles emitidos, y rechazo de un secreto incorrecto. No imprime ni persiste tokens. La lectura de claims del cliente de pruebas no sustituye la validación criptográfica del JWT en los resource servers, que se verifica junto con la seguridad de fase 3.

## Persistencia y mantenimiento

```powershell

docker compose stop

docker compose start

docker compose down

docker compose up -d

```

`down` conserva el volumen PostgreSQL si se omite `-v`. No eliminar el volumen para aplicar cambios. El inicializador crea las tres bases y sus roles solo cuando el volumen está vacío; las actualizaciones posteriores requieren SQL explícito. Las tablas de negocio se administran mediante scripts SQL aditivos y ddl-auto=validate. Keycloak administra su esquema.

El import de realm omite un realm existente. Modificar el JSON o las contraseñas del .env no actualiza automáticamente los usuarios, secretos ni contraseñas de base ya persistidos. Los cambios deben aplicarse mediante administración de Keycloak/SQL y reflejarse luego en configuración. El archivo .env contiene secretos locales y queda excluido de Git y del contexto de construcción.

Keycloak usa start-dev para el entorno local evaluativo. Este Compose no constituye un despliegue público con TLS. Los healthchecks de PostgreSQL y Keycloak ordenan el arranque; las aplicaciones deben manejar también fallos posteriores. Los logs usan rotación de 10 MB y tres archivos por contenedor.

## Verificación de seguridad — fase 3

Con los contenedores iniciados:

```powershell
# Solo para realms previamente creados con la fase 2; es idempotente.
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.repairIdentity=true" "-Dtest=KeycloakScopeSetupTest"
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.integration=true" "-Dtest=BackendIntegrationTest"
```

La reparación Java usa la cuenta bootstrap local para asociar el scope `basic` a los dos clientes y no modifica usuarios ni contraseñas. Requiere conservar las credenciales locales de `.env`. No es un flujo de autenticación de usuarios de AuxiRuta. Los nuevos realms ya incluyen este scope en el JSON de importación. No se debe borrar el volumen para aplicar esta corrección.

La suite Java realiza login PKCE, mantiene tokens en memoria y prueba el Gateway en localhost:8000. También verifica Fleet y Assistance directamente mediante `docker compose exec` y curl dentro de la red privada, sin publicar sus puertos. No imprime ni guarda tokens. Las rutas internas de reservas están implementadas desde fase 7; los vehículos y solicitudes ya tienen controladores funcionales.

Los roles de acceso se aplican por método y ruta. Un JWT ausente o inválido recibe 401; un rol o cliente sin permiso recibe 403. Los vehículos y solicitudes ajenos devuelven 404; los listados de DRIVER se filtran por propietario. Las respuestas de error usan `application/problem+json`.

La consulta JSON de OpenAPI requiere `Authorization: Bearer <token>` en `/docs/fleet/v3/api-docs` o `/docs/assistance/v3/api-docs`. Las aplicaciones conservan los puertos definidos en la fase 2. El Gateway transmite el Bearer original y cada servicio lo vuelve a verificar.

## Administración de Fleet — fase 4

`docker compose up -d --build` aplica el SQL aditivo de Fleet y valida el esquema existente. No es necesario borrar el volumen. La prueba `.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.integration=true" "-Dtest=BackendIntegrationTest"` utiliza los usuarios demo, verifica el flujo y deja grúas de prueba INACTIVE y desvinculadas, con patentes TEST. El operador demo termina UNAVAILABLE. Ejecutarla sobre el entorno local destinado a pruebas.

Para preparar recursos mediante API, usar un token ADMIN en Gateway y seguir este orden:

1. Crear previamente en Keycloak una identidad con OPERATOR y copiar su ID (subject).
2. POST /api/v1/operators con keycloakSubject, fullName y contactPhone opcional.
3. PUT /api/v1/operators/{id}/qualification con category LIGHT_TOWING y validFrom/validUntil en ISO-8601 UTC; inicio estrictamente anterior a vencimiento.
4. POST /api/v1/tow-trucks con licensePlate, maximumLoadKg, supportsNonRollable y supportedTypes (lista no vacía de CAR, SUV, PICKUP). Se crea INACTIVE.
5. PUT /api/v1/tow-trucks/{id}/operator con operatorId.
6. PATCH /api/v1/tow-trucks/{id}/operational-status con status ACTIVE y reason; enviar Idempotency-Key con una clave única de hasta 100 caracteres. La respuesta es el evento de historial persistido.
7. Con token OPERATOR, PATCH /api/v1/operators/me/availability con status AVAILABLE y PUT /api/v1/tow-trucks/assigned-to-me/location con latitude/longitude. Las fechas son del servidor.

Los contratos y tipos concretos se consultan en /docs/fleet/v3/api-docs con token. Una habilitación registrada o una grúa ACTIVE no garantizan elegibilidad: la selección de candidatos corresponde a la fase 6. Las reservas y restricciones sobre recursos comprometidos se incorporan en fase 7.

## OpenRouteService — fase 6

Agregar la clave personal únicamente en `.env` como `ORS_API_KEY`. `.env.example` contiene la variable vacía y `ORS_BASE_URL=https://api.heigit.org/openrouteservice/v2`. Compose entrega ambas variables exclusivamente a Assistance. Después de modificar `.env`, ejecutar `docker compose up -d assistance-service` para recrearlo con el entorno actualizado; `restart` no recarga las variables. Usar `--build` cuando también cambie código.

Sin clave, Assistance inicia y conserva las solicitudes con evaluación PENDING_DEPENDENCY y motivo CONFIGURATION. No inventa rutas. La configuración necesita salida HTTPS hacia el proveedor. El presupuesto local limita a 40 llamadas por 60 segundos y 2.000 por 24 horas; se conserva en PostgreSQL junto con las pausas indicadas por el proveedor. Una clave compartida con aplicaciones externas puede consumir cuota fuera de este contador; las respuestas remotas siguen siendo autoridad.

El esquema aditivo `002-evaluation.sql` se aplica al arrancar Assistance y Hibernate valida el mapeo. No se borran volúmenes ni datos anteriores. Las tablas nuevas son evaluations, candidate_evaluations, routing_budget y routing_calls.

## Reservas y propuestas — fase 7

El arranque aplica de forma aditiva 002-reservations.sql en Fleet y 003-assignments.sql en Assistance. Las tablas nuevas son resource_reservations, assignments y pending_actions; assistance_requests incorpora resource_release_pending. No se agregan contenedores ni bases de datos.

Actualizar con docker compose up -d --build. La aceptación normal alcanza ASSIGNED. Los registros PENDING/RETRYING conservan las operaciones inciertas. Desde fase 9, un proceso periódico recupera la cola, también después de un reinicio. Repetir una respuesta de operador con la misma Idempotency-Key permite recuperar su operación pendiente.

## Ejecución del servicio — fase 8

El script aditivo 004-execution.sql incorpora las fechas de despacho, llegada, carga, inicio del traslado y entrega, además de la nota de entrega, a assignments. No agrega bases ni contenedores. Actualizar con docker compose up -d --build conservando los volúmenes.

La prueba Phase8IntegrationTest detiene/reinicia fleet-service deliberadamente. Ejecutarla sin otras pruebas simultáneas. Una entrega con Fleet caído permanece COMPLETED con resourceReleasePending y una acción RELEASE durable. Repetir la misma entrega con la misma clave permite recuperar la liberación; el procesamiento periódico de la cola está implementado desde fase 9 y no requiere repetir la entrega.

## Automatización y perfil demo — fase 9

`docker compose up -d --build` incorpora las migraciones aditivas `assistance-service/src/main/resources/db/005-automation.sql` y `fleet-service/src/main/resources/db/003-automation.sql`, cargadas desde los recursos de cada aplicación. Conserva el volumen y los datos existentes. Assistance persiste fechas de ejecución, intentos y reclamaciones de tareas; Fleet registra si llegó a crear una retención.

`SPRING_PROFILES_ACTIVE` vale `docker` por defecto; `docker,demo` activa los plazos reducidos. `AUXIRUTA_AUTOMATION_ENABLED` vale `true`; desactivarlo es una opción técnica de diagnóstico, no el modo comprometido del MVP. Cambiar variables requiere recrear contenedores con `up -d`; `start` conserva la configuración anterior.

La solución se despliega con una instancia de cada microservicio. Al iniciar, Assistance recupera la planificación de solicitudes abiertas y procesa las acciones pendientes de PostgreSQL. La caché DNS del cliente del Gateway tiene un máximo de diez segundos para recuperar cambios de dirección de los contenedores. Las llamadas HTTP no mantienen abiertas las transacciones locales.


## Entrega integrada

La [guía de entrega](../docs/ENTREGA.md) reúne el procedimiento de primer arranque, preparación del operador, colección Postman y contratos. Start-AuxiRuta.ps1 espera disponibilidad real y Verify-AuxiRuta.ps1 coordina las pruebas Java y restaura el perfil normal tras la demostración temporal.
