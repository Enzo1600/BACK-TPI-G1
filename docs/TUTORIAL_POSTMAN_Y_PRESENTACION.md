# Tutorial de Postman, presentación y primer arranque

## 1. Qué archivos se importan y para qué sirve cada uno

El repositorio contiene ahora dos colecciones ejecutables y un entorno. La segunda colección, agregada para demostrar propiedad entre usuarios, se describe en [DEMO_PRIVACIDAD.md](DEMO_PRIVACIDAD.md). El entorno no es una colección.

| Archivo en docs/api | Función |
|---|---|
| AuxiRuta.postman_collection.json | Recorrido preparado de 47 pasos, con datos nuevos, capturas de identificadores, esperas y verificaciones positivas y negativas. |
| AuxiRuta-privacidad.postman_collection.json | Diez peticiones para demostrar vehículos propios y ajenos e identidades de operadores. Requiere la preparación de privacidad. |
| Local.postman_environment.json | Configuración local: URL del Gateway y tokens de ADMIN y de dos identidades por rol DRIVER/OPERATOR. No contiene solicitudes ni se ejecuta. |
| fleet.openapi.json | Contrato de todos los endpoints públicos de Fleet. Se puede importar como una colección para exploración manual. |
| assistance.openapi.json | Contrato de todos los endpoints públicos de Assistance. Se puede importar como otra colección para exploración manual. |

Las dos colecciones generadas desde OpenAPI sirven para consultar y probar todos los endpoints de cada microservicio. No traen el encadenamiento, los datos válidos ni las verificaciones del recorrido de 47 pasos. No deben ejecutarse indiscriminadamente con Run: cada operación tiene precondiciones.

Los contratos internos de comunicación entre servicios no se exponen por Gateway y no forman parte de estas colecciones públicas.

## 2. Qué necesita quien clona el repositorio

En Windows: Git, Docker Desktop con motor Linux/WSL 2, Postman Desktop y conexión a Internet. JDK 21 se necesita para los comandos Java de preparación y verificación de esta guía; construir y arrancar mediante Docker no requiere Java instalado en el host. No hace falta Maven global: el repositorio incluye Maven Wrapper.

Verificar desde PowerShell:

~~~powershell
git --version
docker --version
docker compose version
docker info
java -version
~~~

Docker Desktop debe estar iniciado y mostrar Engine running. Los puertos 8000 y 8080 deben estar libres. El primer arranque descarga imágenes y dependencias; ORS requiere Internet y una clave válida con cuota.

Clonar usando la URL real del repositorio:

~~~powershell
git clone <URL_DEL_REPOSITORIO>
cd BACK-TPI-G1
if (!(Test-Path .env)) { Copy-Item .env.example .env }
notepad .env
~~~

Reemplazar todos los valores CAMBIAR_... por contraseñas/secretos locales elegidos por quien instala. Completar ORS_API_KEY con una clave propia y conservar inicialmente ORS_BASE_URL. Dejar SPRING_PROFILES_ACTIVE=docker y AUXIRUTA_AUTOMATION_ENABLED=true. El .env no se sube a Git ni se comparte como parte de la entrega.

En una instalación nueva se crean las tres bases, los usuarios de base y el realm de Keycloak. Cambiar después una contraseña en .env no actualiza automáticamente los datos persistidos. No utilizar docker compose down -v como procedimiento de actualización: elimina el volumen y sus datos.

Arrancar desde la raíz:

~~~powershell
.\scripts\Start-AuxiRuta.ps1 -Profile docker
docker compose ps
~~~

El script construye e inicia PostgreSQL, Keycloak, Fleet, Assistance y Gateway, y espera disponibilidad. Si PowerShell impide ejecutar scripts descargados, se puede habilitar solo la sesión actual, sujeto a las políticas del equipo:

~~~powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
~~~

No ejecutar además los microservicios desde el IDE para esta demostración: ya están dentro de Docker.

| Dirección | Uso |
|---|---|
| http://localhost:8000 | API Gateway: destino de todas las peticiones públicas de negocio. |
| http://localhost:8080 | Keycloak: inicio de sesión y administración de identidades. |

No hay frontend de negocio. Fleet y Assistance tienen puertos internos sin acceso directo desde el host.

Preparar el operador de demostración:

~~~powershell
.\auxiruta-commons\mvnw.cmd -f auxiruta-commons/pom.xml install
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.seed=true" "-Dtest=DemoDataSetupTest"
~~~

La preparación crea el perfil de negocio si falta, activa al operador, renueva su habilitación por 24 horas y lo deja UNAVAILABLE. Exige que no tenga una grúa vinculada. No borra servicios ni libera recursos ocupados. La cuenta Keycloak operator.demo y el perfil de negocio son registros distintos.

El install de Commons se necesita inicialmente y cuando cambia esa biblioteca, no antes de cada llamada a Postman.

## 3. Importación y autenticación

1. Abrir Postman Desktop y seleccionar Import.
2. Importar AuxiRuta.postman_collection.json y Local.postman_environment.json.
3. Seleccionar el entorno “AuxiRuta local - sin secretos”.
4. Comprobar base_url = http://localhost:8000.
5. Crear una petición auxiliar, fuera del recorrido, para obtener tokens.

En Authorization seleccionar OAuth 2.0 y configurar:

| Campo | Valor |
|---|---|
| Grant Type | Authorization Code (With PKCE) |
| Auth URL | http://localhost:8080/realms/auxiruta/protocol/openid-connect/auth?prompt=login |
| Access Token URL | http://localhost:8080/realms/auxiruta/protocol/openid-connect/token |
| Client ID | auxiruta-api |
| Client Secret | Vacío |
| Callback URL | https://oauth.pstmn.io/v1/callback |
| Code Challenge Method | SHA-256 / S256 |
| Scope | openid |

Pulsar Get New Access Token e iniciar sesión. Repetir para las tres cuentas:

| Usuario | Contraseña configurada en .env | Variable donde copiar el access token |
|---|---|---|
| admin.demo | DEMO_ADMIN_PASSWORD | admin_token |
| operator.demo | DEMO_OPERATOR_PASSWORD | operator_token |
| driver.demo | DEMO_DRIVER_PASSWORD | driver_token |

Copiar solo el access token, sin anteponer Bearer, en el valor local de la variable. No usar el ID token ni el refresh token. No sincronizar ni exportar los tokens. La opción prompt=login permite seleccionar otra cuenta aunque ya exista una sesión. Si el token vence, obtener uno nuevo.

Las peticiones del recorrido ya eligen la variable del rol correspondiente; no necesitan cambiar manualmente de sesión en cada paso. bootstrap-admin es el administrador de Keycloak, no el ADMIN del negocio. ADMIN tampoco reemplaza al conductor o al operador en sus acciones operativas.

Referencia: [OAuth 2.0 en Postman](https://learning.postman.com/docs/use/send-requests/authorization/oauth-20).

## 4. Ejecutar el recorrido automático de 47 pasos

Precondiciones: perfil normal docker, operador preparado sin grúa vinculada, tres tokens vigentes, clave ORS válida y ninguna suite Java u otra demostración ejecutándose sobre el mismo entorno.

1. Seleccionar la colección AuxiRuta y abrir Run.
2. Elegir el entorno local.
3. Mantener todos los pasos seleccionados, en el orden original.
4. Configurar una iteración y delay 0.
5. Ejecutar y revisar los resultados de las aserciones.

| Carpeta | Qué demuestra |
|---|---|
| 01 Seguridad y precondiciones | Autenticación, rechazo por rol y preparación del entorno. |
| 02 Recursos reproducibles | Alta y vínculo de grúa, ubicación, disponibilidad y vehículo propio; validaciones inválidas. |
| 03 Evaluación y propuesta | Cobertura, rutas reales, exclusividad, idempotencia y aparición de propuesta. |
| 04 Aceptación y ejecución | Aceptación, restricciones posteriores, orden del traslado, entrega e historial. |
| 05 Cancelación y cierre | Cancelación previa a aceptación y liberación de los recursos de prueba. |

Una respuesta 400, 401, 403, 404 o 409 puede ser el resultado correcto de un caso negativo. Revisar la descripción del paso y la aserción, no exigir 200 a todas las peticiones.

Los identificadores se capturan como variables de colección. El recorrido genera patentes y claves nuevas. Los pasos de espera consultan hasta observar el resultado o alcanzar el límite de espera. Esos saltos requieren Runner; Send no ejecuta toda la cadena.

Si la ejecución termina correctamente, quedan los registros y el historial para inspección, pero la grúa queda inactiva/desvinculada y el operador no disponible. Una ejecución interrumpida requiere revisar el estado antes de reiniciar la colección: renovar un token no revierte las operaciones ya realizadas.

No usar docker,demo para esta colección: sus ventanas reducidas dificultan el recorrido y la colección comprueba las reglas normales.

## 5. Importar y usar las dos colecciones de OpenAPI

Importar fleet.openapi.json y assistance.openapi.json y elegir generar una colección para cada contrato. Postman permite importar OpenAPI como colección o como especificación con colección: [documentación de importación](https://learning.postman.com/docs/getting-started/importing-and-exporting/importing-data).

En cada colección:

1. Inspeccionar la URL generada y configurar su variable base para http://localhost:8000. Puede llamarse baseUrl; no asumir que coincide con base_url del entorno.
2. Conservar el path /api/v1/... de cada petición sin duplicarlo.
3. Elegir Authorization → Bearer Token y usar {{admin_token}}, {{operator_token}} o {{driver_token}}, según la operación.
4. Si se configura la autenticación en la colección, comprobar que la petición herede esa autenticación. Una configuración propia de la petición prevalece.
5. Reemplazar los ejemplos generados, UUID y parámetros por valores reales. No enviar literalmente marcadores como <uuid>.
6. Completar Content-Type: application/json en cuerpos JSON y el header Idempotency-Key cuando el contrato lo exige.
7. Pulsar Send y observar código HTTP, cuerpo y estado posterior mediante GET.

Fleet permite preparar y consultar operadores, habilitaciones, grúas, vínculos, disponibilidad y ubicación. Assistance permite vehículos propios, solicitudes, historial, propuestas y etapas del traslado.

Estas colecciones no preparan automáticamente un estado válido. Por ejemplo, aceptar exige una propuesta vigente dirigida al operador autenticado; cargar exige haber declarado llegada.

## 6. Probar un endpoint con distintos usuarios y resultados

Sí, se puede probar cada endpoint individualmente. El resultado depende del rol, del propietario/asignatario, de los datos, del estado y de los plazos; cambiar únicamente de rol no produce todos los resultados posibles.

Para experimentar, usar las colecciones importadas desde OpenAPI o una colección manual separada. Alterar los tokens del recorrido automático hará fallar sus expectativas predefinidas.

Ejemplos reproducibles, siempre con cuerpo y headers válidos salvo que se esté probando su validación:

| Caso | Resultado esperado |
|---|---|
| GET /api/v1/vehicles con No Auth | 401. |
| GET /api/v1/vehicles con driver_token válido | 200; únicamente sus vehículos. |
| GET /api/v1/vehicles con operator_token | 403. |
| GET /api/v1/operators con admin_token | 200. |
| GET /api/v1/operators con driver_token | 403. |
| GET /api/v1/vehicles/{id} con UUID válido inexistente y DRIVER | 404. |
| GET de un vehículo de otro DRIVER, autenticado como DRIVER | 404; oculta su existencia. |
| POST de vehículo con patente propia ya registrada | 409, usando un cuerpo por lo demás válido. |
| Editar un vehículo propio con solicitud activa | 409. |
| Cancelar la solicitud propia después de la primera aceptación | 409. |
| Aceptar una propuesta con DRIVER | 403. |
| Actualizar ubicación de la grúa propia retenida o confirmada | 409; no cambia la fecha ni las coordenadas. |
| Declarar llegada antes de despacho con el operador asignado | 409. |
| Completar con nota demasiado corta y datos restantes correctos | 400. |

Para probar propiedad ya están previstas driver2.demo y operator2.demo; ejecutar la preparación de [DEMO_PRIVACIDAD.md](DEMO_PRIVACIDAD.md). Hace falta otra identidad del mismo rol. ADMIN u OPERATOR no sustituyen a un segundo DRIVER. Se puede crear un usuario local adicional en el realm auxiruta, asignarle únicamente DRIVER y obtener su token mediante PKCE; registrar un vehículo con cada identidad. Las pruebas Java ya crean identidades temporales para comprobar estos casos. Para otro OPERATOR también hace falta preparar su perfil de negocio y recursos cuando el escenario lo requiera.

La secuencia normal de ejecución después de aceptar es:

~~~text
ASSIGNED → DISPATCHED → ARRIVED → VEHICLE_LOADED → IN_TRANSIT → COMPLETED
~~~

Usar GET /api/v1/assistance-requests/{id} y GET /api/v1/assistance-requests/{id}/history para observar el resultado. La respuesta de una acción puede ser el evento de historial, no el objeto completo de solicitud. Una confirmación o liberación remota puede quedar pendiente y completarse después.

Las etapas de ejecución se declaran mediante:
- POST /api/v1/assistance-requests/{id}/dispatch
- POST /api/v1/assistance-requests/{id}/arrival
- POST /api/v1/assistance-requests/{id}/loading
- POST /api/v1/assistance-requests/{id}/transport-start
- POST /api/v1/assistance-requests/{id}/completion

Cada acción usa una clave de idempotencia propia. Conservar la misma clave y el mismo contenido para repetir esa acción; no generar una clave nueva al comprobar idempotencia. Crear una clave nueva para una operación nueva.

En modo normal, actualizar la ubicación antes de buscar recursos: su vigencia para recibir y aceptar propuestas es de cinco minutos. Desde que existe retención no puede actualizarse. Aceptar dentro del plazo de la propuesta y despachar dentro de los cinco minutos posteriores. La antigüedad de ubicación no vuelve a invalidar por sí sola una asignación aceptada.

No hay endpoint para forzar un estado arbitrario. Preparar solicitudes independientes para ramas incompatibles: una solicitud cancelada no sirve luego para demostrar un traslado completado. Los errores externos o carreras concurrentes requieren preparación específica; la colección manual por sí sola no cubre exhaustivamente todos los fallos posibles. Las suites Java cubren esos escenarios controlados.

## 7. Preparación para presentar al profesor

### Antes del día de presentación

1. Tener la versión acordada del repositorio, .env completo y Docker funcionando.
2. Construir y verificar con tiempo suficiente:
   
~~~powershell
.\scripts\Verify-AuxiRuta.ps1 -Integration -IncludeDemo
~~~

Este comando requiere JDK 21 y ORS; consume cuota, crea datos y reinicia servicios. No ejecutarlo simultáneamente con Postman. Al finalizar restaura el perfil normal. Conservar los reportes de pruebas para mostrar evidencia.
3. Importar colección, entorno y contratos en Postman.
4. Ensayar el recorrido completo. No depender de descargas iniciales durante la exposición.

### Al comenzar la presentación

Abrir Docker Desktop y esperar Engine running. Desde la raíz:

~~~powershell
.\scripts\Start-AuxiRuta.ps1 -Profile docker -NoBuild
docker compose ps
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.seed=true" "-Dtest=DemoDataSetupTest"
~~~

Usar -NoBuild solo si las imágenes corresponden al código que se presenta. Si hubo cambios desde la última construcción, arrancar sin -NoBuild. Si Commons cambió o es la primera preparación local, ejecutar antes su install.

Obtener tokens nuevos para los tres usuarios y seleccionar el entorno. Ejecutar el recorrido de 47 pasos con Runner. Luego mostrar solicitud COMPLETED, historial, resultados negativos y consultas autorizadas por rol.

Para una demostración paso a paso, preparar antes los recursos y las peticiones. Explicar las reglas antes de crear la solicitud; desde que aparece una propuesta sus plazos siguen corriendo. No detenerse varios minutos entre recibir y aceptar.

No ejecutar las colecciones OpenAPI completas con Runner ni arrancar pruebas integrales mientras se muestra el recorrido.

### Mostrar automatización acelerada, si se necesita

El perfil demo es una configuración de tiempos, independiente de las cuentas driver.demo/operator.demo/admin.demo:

~~~powershell
.\scripts\Start-AuxiRuta.ps1 -Profile 'docker,demo' -NoBuild
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.phase9=true" "-Dtest=Phase9IntegrationTest"
.\scripts\Start-AuxiRuta.ps1 -Profile docker -NoBuild
~~~

Preparar el operador libre y ejecutar esta demostración por separado. Si la prueba falla, restaurar igualmente el perfil normal con el último comando. Demo reduce búsqueda a 60 s, propuesta máxima a 10 s, ventana mínima a 3 s, despacho a 20 s y evaluación a 2 s. No reduce cuotas ORS ni la vigencia de ubicación. Las solicitudes existentes mantienen su snapshot: cambiar de perfil no modifica retroactivamente sus plazos.

### Al terminar

~~~powershell
docker compose stop
~~~

Luego se puede cerrar Docker Desktop. En la próxima sesión abrir Docker Desktop y utilizar el script con -NoBuild. docker compose down elimina contenedores/red, pero sin -v conserva los datos; no es necesario hacerlo para terminar el día.

## 8. Problemas frecuentes

| Síntoma | Qué revisar |
|---|---|
| Error al conectar con dockerDesktopLinuxEngine | Docker Desktop y motor Linux iniciados. |
| Connection refused en Postman | Contenedores arrancados y URL del Gateway 8000. |
| 401 inesperado | Token correcto, vigente y seleccionado en la petición. |
| 403 | Rol requerido; ADMIN no hereda las acciones operativas. |
| 404 | UUID, propiedad o asignación; puede ocultar un recurso ajeno. |
| 409 | Estado, exclusividad, plazo, duplicado o clave de idempotencia. Leer ProblemDetail. |
| 400 | JSON, campos, tipos, parámetros y headers obligatorios. |
| Solicitud pendiente sin propuesta | Consultar evaluación e historial; disponibilidad, habilitación, ubicación, compatibilidad y ORS. |
| 503 en acción | Dependencia temporalmente indisponible; consultar estado antes de repetir con la misma clave. |
| Seed informa grúa vinculada | Completar/liberar el servicio; desvincular mediante ADMIN únicamente cuando el recurso esté libre. |
| Regla demo detectada por la colección | Restaurar perfil docker y preparar una ejecución nueva. |

Diagnóstico sin imprimir secretos:

~~~powershell
docker compose ps
docker compose logs --tail 80 assistance-service fleet-service api-gateway
~~~

No pegar tokens ni el contenido del .env en capturas de la presentación.
