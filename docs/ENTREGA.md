# Entrega y verificación de AuxiRuta

La entrega comprende los cuatro proyectos Maven, Compose, configuración inicial de identidad, esquemas aditivos, contratos OpenAPI, colección Postman y pruebas Java. La línea base funcional es AR-MVP-5. Se ejecuta una instancia por microservicio en el entorno local evaluativo.

## 1. Requisitos y primer arranque

- Docker Desktop con motor Linux/WSL 2 iniciado y Docker Compose disponible.
- JDK 21 para las pruebas desde Windows. Maven Wrapper descarga la distribución acordada; no requiere instalar Maven globalmente.
- Puertos locales 8000 y 8080 libres, acceso a los repositorios de imágenes/dependencias y a OpenRouteService.
- Clave ORS válida con cuota disponible. Sin ella, el backend conserva resultados pendientes; la demostración del recorrido viable no podrá aprobarse.

Desde la raíz del repositorio:

```powershell
# Solo en una copia nueva: no sobrescribir un .env existente.
if (!(Test-Path .env)) { Copy-Item .env.example .env }
# Editar .env: reemplazar CAMBIAR_... y completar ORS_API_KEY.
.\scripts\Start-AuxiRuta.ps1
```

El script construye imágenes, inicia los cinco contenedores y espera que los tres servidores HTTP respondan con 401 a consultas sin credenciales. No imprime secretos ni elimina volúmenes. Esos 401 comprueban disponibilidad y protección; la autenticación completa se verifica con las pruebas siguientes. Para reiniciar imágenes ya construidas: `Start-AuxiRuta.ps1 -NoBuild`.

Gateway: `http://localhost:8000`. Keycloak: `http://localhost:8080`. Abrir estas URLs no muestra una interfaz de negocio: la entrega es un backend REST. Fleet y Assistance no exponen puertos al host.

Las bases nuevas se inicializan al crear el volumen; después, los scripts SQL aditivos se ejecutan en el arranque y JPA valida el esquema. Cambiar contraseñas en `.env` no modifica usuarios ni bases ya persistidos. No utilizar `down -v` para actualizar. Al terminar: `docker compose stop`; para volver: `docker compose start` o el script de inicio.

## 2. Identidades y datos iniciales

Compose importa el realm `auxiruta` con `admin.demo`, `operator.demo`, `driver.demo`, `operator2.demo` y `driver2.demo`. Sus contraseñas son las variables `DEMO_ADMIN_PASSWORD`, `DEMO_OPERATOR_PASSWORD` y `DEMO_DRIVER_PASSWORD` del `.env` local. La cuenta `bootstrap-admin` administra Keycloak; no se utiliza como ADMIN de negocio.

Preparar o renovar el perfil de operador para la demostración:

```powershell
.\auxiruta-commons\mvnw.cmd -f auxiruta-commons/pom.xml install
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.seed=true" "-Dtest=DemoDataSetupTest"
```

Esta preparación Java autentica mediante PKCE, crea el perfil si falta, activa su registro, establece habilitación LIGHT_TOWING hasta 24 horas después y lo deja UNAVAILABLE. Exige que no tenga grúa vinculada; no desvincula un recurso existente ni libera un servicio comprometido. Puede repetirse para renovar la vigencia. Las grúas, vehículos y solicitudes se crean luego por API, con identificadores nuevos en cada ejecución.

## 3. Autenticación en Postman

Importar estos dos archivos:

- [AuxiRuta.postman_collection.json](api/AuxiRuta.postman_collection.json).
- [Local.postman_environment.json](api/Local.postman_environment.json).

Seleccionar el entorno local. Su `base_url` es `http://localhost:8000`; los tres tokens están vacíos deliberadamente.

Obtener un token para cada usuario mediante **OAuth 2.0 → Authorization Code (With PKCE)** en una petición auxiliar de Postman:

| Campo | Valor |
|---|---|
| Authorization URL | `http://localhost:8080/realms/auxiruta/protocol/openid-connect/auth?prompt=login` |
| Access Token URL | `http://localhost:8080/realms/auxiruta/protocol/openid-connect/token` |
| Client ID | `auxiruta-api` |
| Client Secret | Vacío; cliente público |
| Callback URL | `https://oauth.pstmn.io/v1/callback` |
| Code Challenge Method | SHA-256 / S256 |
| Scope | `openid` |

Usar **Get New Access Token**, iniciar sesión con cada identidad y copiar únicamente su access token al valor local correspondiente: `admin_token`, `operator_token` y `driver_token`. `prompt=login` permite cambiar de usuario aunque exista una sesión previa. No activar Share Token/Sync Token ni exportar credenciales. Los tres valores están marcados como secretos en el entorno de ejemplo. Si aparece 401 por expiración, renovar los tokens antes de continuar; no reiniciar automáticamente una secuencia que dejó un servicio en curso.

La configuración sigue el [flujo PKCE documentado por Postman](https://learning.postman.com/docs/use/send-requests/authorization/oauth-20). No se habilita password grant. El cliente técnico de Assistance permanece interno; sus credenciales no se necesitan para ejecutar la colección.

## 4. Orden de ejecución y resultados

Ejecutar la colección completa en **Collection Runner**, una iteración, delay 0, con el perfil normal `docker`. Los scripts de encadenamiento de Postman son JavaScript porque es el lenguaje de su sandbox; las pruebas automatizadas del proyecto están escritas en Java. No hay pruebas mantenidas en otros lenguajes.

| Carpeta | Resultado observable |
|---|---|
| 01 — Seguridad y precondiciones | 401 sin token, 403 por rol, perfil disponible para preparar recursos y reglas normales |
| 02 — Recursos reproducibles | Grúa vinculada, activa y ubicada; operador disponible; vehículo propio; rechazo por patente duplicada, peso y UUID inexistente |
| 03 — Evaluación y propuesta | Destino fuera de cobertura rechazado; solicitud viable con rutas reales; idempotencia y exclusividad; propuesta encontrada con paginación |
| 04 — Aceptación y ejecución | Confirmación durable, cancelación prohibida después de aceptar, vínculo protegido, secuencia hasta COMPLETED, nota válida, historial sin duplicados y liberación confirmada |
| 05 — Cancelación y cierre | Otra solicitud cancelada antes de aceptar; operador UNAVAILABLE; grúa de prueba INACTIVE y desvinculada |

Los 47 pasos generan patentes TEST/TST y claves nuevas; repetir una operación idempotente dentro del recorrido conserva la misma clave. Los IDs devueltos se guardan como variables de colección. La búsqueda de la propuesta recorre páginas y filtra por solicitud propia. Las consultas de espera tienen un límite de 45 segundos; no amplían plazos comerciales. Las transiciones de ejecución representan declaraciones del operador: no esperan la duración estimada del viaje.

Los saltos de ejecución solo funcionan en Runner, según la [documentación de flujos de Postman](https://learning.postman.com/docs/tests-and-scripts/running-collections/building-workflows). Enviar manualmente cada petición requiere respetar el orden y los plazos: una propuesta normal dura como máximo 60 segundos. No usar el perfil demo para este recorrido guiado; sus ventanas son deliberadamente más cortas.

Si falla una precondición, código HTTP o espera, la colección se detiene para revisión. Los fixtures y su historial permanecen en la base. Una ejecución exitosa puede repetirse; una interrumpida debe inspeccionarse antes: solo el propietario puede cancelar antes de aceptar; después corresponde completar con el operador asignado o dejar que venza el despacho. No se agrega un endpoint administrativo para forzar estados.

## 5. Verificación Java

Pruebas aisladas y compilación, sin Docker:

```powershell
.\scripts\Verify-AuxiRuta.ps1
```

Todas las suites de integración, incluida automatización demo:

```powershell
.\scripts\Verify-AuxiRuta.ps1 -Integration -IncludeDemo
```

Ejecutarlas sin otra demostración simultánea. Detienen/reinician servicios, crean identidades temporales para verificar privacidad y consumen cuota ORS real. La fase demo restaura el perfil normal incluso ante un fallo. Los reportes están en `target/surefire-reports` de cada módulo. Las suites integrales aparecen como skipped en una compilación ordinaria porque necesitan habilitación explícita.

Verificar solamente la entrega publicada:

```powershell
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.delivery=true" "-Dtest=DeliveryIntegrationTest"
```

Esta prueba obtiene tokens sin persistirlos, prepara el operador, ejecuta las peticiones del JSON exportado y verifica los resultados definidos en Java. También compara OpenAPI con todos los mappings públicos de los controladores y con los contratos versionados. No ejecuta el sandbox de Postman: verifica el mismo tráfico y las mismas condiciones funcionales. Los scripts de Postman se conservan como mecanismo de ejecución interactiva.

La limpieza Java se limita a sus propios fixtures. En caso de interrupción puede cerrar esas filas por SQL y resolver sus reservas por la API técnica; esto no representa una operación de negocio disponible para usuarios. Los servicios completados normalmente conservan su resultado e historial.

## 6. Contratos y trazabilidad

Los contratos públicos están en [Fleet](api/fleet.openapi.json) y [Assistance](api/assistance.openapi.json), importables en Postman como referencia de **todos** los endpoints. Al importar un contrato por separado, configurar su URL base como http://localhost:8000. Los contratos técnicos privados se describen en [INTEGRACION_INTERNA.md](api/INTEGRACION_INTERNA.md); no se publican por Gateway.

Con JWT de un rol humano, consultar los contratos vivos en `/docs/fleet/v3/api-docs` y `/docs/assistance/v3/api-docs`. Cada contrato documenta Bearer JWT, operaciones, DTOs, validaciones y respuestas. Los errores usan ProblemDetail; 404 oculta recursos ajenos cuando corresponde. ADMIN no sustituye las acciones operativas de DRIVER u OPERATOR.

La definición del recorrido está en `DeliveryArtifacts.java`. Si cambian solicitudes o expectativas, regenerar la colección con `-Dauxiruta.export=true -Dtest=DeliveryArtifactsTest`. Después de reconstruir los servicios, regenerar los contratos con `-Dauxiruta.delivery=true -Dauxiruta.export=true -Dtest=DeliveryIntegrationTest`. Revisar los archivos generados antes del commit. La verificación normal compara sin sobrescribir.

| Compromiso | Evidencia |
|---|---|
| Identidad, roles y privacidad | BackendIntegrationTest: firma/audiencia, 401/403, dos conductores y 404 de recurso ajeno; fase 8: otro operador |
| Recursos, validaciones y propiedad | BackendIntegrationTest y carpeta 02 de la colección |
| Cobertura, rutas reales y ranking | Pruebas de reglas/ORS, evaluación real de BackendIntegrationTest y carpeta 03 |
| Reserva exclusiva, carrera e idempotencia | Phase7IntegrationTest; carpetas 03 y 04 |
| Recorrido, revalidación y liberación | Phase8IntegrationTest y carpeta 04 |
| Cupos, vencimientos y recuperación | Phase9IntegrationTest: siete casos; pruebas con Clock fijo |
| Contratos entregados coherentes | DeliveryArtifactsTest y DeliveryIntegrationTest |
| Arranque reproducible | Compose, Dockerfiles, `.env.example` y Start-AuxiRuta.ps1 |

## 7. Dependencias y límites de entrega

ORS real puede rechazar consultas por clave, cuota, red o ausencia de ruta. Esos casos conservan el comportamiento definido, pero hacen fallar explícitamente la demostración que exige una ruta viable; no se reemplazan por distancias ficticias. El presupuesto local de llamadas persiste y no se acelera con el perfil demo.

La solución no incluye frontend, notificaciones, pagos, seguimiento GPS continuo, monitoreo avanzado ni intervención humana para aprobar excepciones. El proceso continuo después del despacho mantiene los supuestos del MVP. Compose utiliza Keycloak start-dev y puertos loopback; el despliegue entregado es local y evaluativo.


## Evidencia de la entrega del 9 de noviembre de 2026

La ejecución completa aprobó 99 pruebas aisladas y 22 escenarios integrales. La colección también fue ejecutada con Newman 6.2.3: 48 llamadas (47 pasos más paginación) y 111 aserciones sin fallos. Los resultados y correcciones están registrados en [cambios.md](cambios.md). Esta ejecución adicional no agrega Node/Newman como requisito de la solución Java.

## Demostración persistente de privacidad

La preparación DemoDataSetupTest también agrega las cuentas secundarias y vehículos de privacidad. La colección adicional de diez peticiones, las contraseñas por rol y el comando independiente para instalaciones existentes están en [DEMO_PRIVACIDAD.md](DEMO_PRIVACIDAD.md). Local.postman_environment.json incluye driver2_token y operator2_token vacíos. Esta ampliación no modifica los 47 pasos del recorrido principal.
