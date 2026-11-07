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



PostgreSQL y los microservicios no publican puertos. Keycloak se accede por http://localhost:8080 y Gateway por http://localhost:8000, ambos ligados al loopback. En esta fase todavía no hay rutas de negocio en Gateway: una respuesta 401 sin token es esperable. La autorización por rol/audiencia y los contratos funcionales se completan en fase 3.



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



Con Python 3 instalado y el entorno iniciado:



```powershell

python infra/tests/verify_identity.py

```



El script usa exclusivamente la biblioteca estándar de Python. Hace login con los tres usuarios mediante Authorization Code + PKCE y obtiene un token técnico. Comprueba issuer, audiencias y roles emitidos, y rechazo de un secreto incorrecto. No imprime ni persiste tokens. La lectura de claims de este script no sustituye la validación criptográfica del JWT en los resource servers, que se verifica junto con la seguridad de fase 3.



## Persistencia y mantenimiento



```powershell

docker compose stop

docker compose start

docker compose down

docker compose up -d

```



`down` conserva el volumen PostgreSQL si se omite `-v`. No eliminar el volumen para aplicar cambios. El inicializador crea las tres bases y sus roles solo cuando el volumen está vacío; las actualizaciones posteriores requieren SQL explícito. Las tablas de negocio se agregarán con sus entidades, usando ddl-auto=validate. Keycloak administra su esquema.



El import de realm omite un realm existente. Modificar el JSON o las contraseñas del .env no actualiza automáticamente los usuarios, secretos ni contraseñas de base ya persistidos. Los cambios deben aplicarse mediante administración de Keycloak/SQL y reflejarse luego en configuración. El archivo .env contiene secretos locales y queda excluido de Git y del contexto de construcción.



Keycloak usa start-dev para el entorno local evaluativo. Este Compose no constituye un despliegue público con TLS. Los healthchecks de PostgreSQL y Keycloak ordenan el arranque; las aplicaciones deben manejar también fallos posteriores. Los logs usan rotación de 10 MB y tres archivos por contenedor.



## Verificación de seguridad — fase 3

Con los contenedores iniciados:

```powershell
# Solo para realms previamente creados con la fase 2; es idempotente.
python infra/keycloak/ensure_basic_scope.py
python infra/tests/verify_security.py
```

El primer script usa la cuenta bootstrap local para asociar el scope `basic` a los dos clientes y no modifica usuarios ni contraseñas. Requiere conservar las credenciales locales de `.env`. No es un flujo de autenticación de usuarios de AuxiRuta. Los nuevos realms ya incluyen este scope en el JSON de importación. No se debe borrar el volumen para aplicar esta corrección.

La prueba realiza login PKCE, mantiene tokens en memoria y prueba el Gateway en localhost:8000. También verifica Fleet y Assistance directamente mediante `docker compose exec` y curl dentro de la red privada, sin publicar sus puertos. No imprime ni guarda tokens. Las respuestas 404 de rutas de negocio autorizadas indican que sus controladores pertenecen a las fases siguientes; no acreditan funcionalidades de negocio terminadas.

Los roles de acceso se aplican por método y ruta. Un JWT ausente o inválido recibe 401; un rol o cliente sin permiso recibe 403. Los recursos ajenos usarán 404 mediante la utilidad de propietario al implementar sus servicios. Las respuestas de error usan `application/problem+json`.

La consulta JSON de OpenAPI requiere `Authorization: Bearer <token>` en `/docs/fleet/v3/api-docs` o `/docs/assistance/v3/api-docs`. Las aplicaciones conservan los puertos definidos en la fase 2. El Gateway transmite el Bearer original y cada servicio lo vuelve a verificar.


## Administración de Fleet — fase 4

`docker compose up -d --build` aplica el SQL aditivo de Fleet y valida el esquema existente. No es necesario borrar el volumen. La prueba `python infra/tests/verify_fleet.py` utiliza los usuarios demo, verifica el flujo y deja grúas de prueba INACTIVE y desvinculadas, con patentes TEST. El operador demo termina UNAVAILABLE. Ejecutarla sobre el entorno local destinado a pruebas.

Para preparar recursos mediante API, usar un token ADMIN en Gateway y seguir este orden:

1. Crear previamente en Keycloak una identidad con OPERATOR y copiar su ID (subject).
2. POST /api/v1/operators con keycloakSubject, fullName y contactPhone opcional.
3. PUT /api/v1/operators/{id}/qualification con category LIGHT_TOWING y validFrom/validUntil en ISO-8601 UTC; inicio estrictamente anterior a vencimiento.
4. POST /api/v1/tow-trucks con licensePlate, maximumLoadKg, supportsNonRollable y supportedTypes (lista no vacía de CAR, SUV, PICKUP). Se crea INACTIVE.
5. PUT /api/v1/tow-trucks/{id}/operator con operatorId.
6. PATCH /api/v1/tow-trucks/{id}/operational-status con status ACTIVE y reason; enviar Idempotency-Key con una clave única de hasta 100 caracteres. La respuesta es el evento de historial persistido.
7. Con token OPERATOR, PATCH /api/v1/operators/me/availability con status AVAILABLE y PUT /api/v1/tow-trucks/assigned-to-me/location con latitude/longitude. Las fechas son del servidor.

Los contratos y tipos concretos se consultan en /docs/fleet/v3/api-docs con token. Una habilitación registrada o una grúa ACTIVE no garantizan elegibilidad: la selección de candidatos corresponde a la fase 6. Las reservas y restricciones sobre recursos comprometidos se incorporan en fase 7.
