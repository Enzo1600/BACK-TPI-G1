# Verificación con Java

A partir de la fase 5 todas las pruebas mantenidas del backend se implementan en Java.

## Pruebas aisladas y compilación

Desde la raíz del repositorio, con JDK 21:

```powershell
.\auxiruta-commons\mvnw.cmd -f auxiruta-commons/pom.xml install
.\fleet-service\mvnw.cmd -f fleet-service/pom.xml verify
.\assistance-service\mvnw.cmd -f assistance-service/pom.xml verify
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml verify
```

Install de commons se requiere inicialmente y cuando cambia la biblioteca. Para repetir pruebas de un módulo basta su comando test. JUnit y Mockito comprueban reglas, JWT, cadenas MVC y comportamiento con Clock fijo; estas pruebas no requieren Docker. La integración está desactivada por defecto y se reporta como skipped, evitando depender de servicios durante una compilación normal o un Docker build.

## Integración real

Con Docker Desktop iniciado y .env configurado:

```powershell
docker compose up -d --build
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.integration=true" "-Dtest=BackendIntegrationTest"
```

Las comillas son necesarias para transmitir correctamente las propiedades con puntos en PowerShell. El comando ejecuta pruebas Java contra los contenedores reales; no utiliza los resource servers ni PostgreSQL simulados. Requiere Docker CLI accesible desde la terminal. Los módulos productivos siguen siendo los cuatro acordados; la suite transversal reside en src/test de Gateway y no se empaqueta en su JAR ejecutable.

BackendClient utiliza java.net.http.HttpClient, Jackson y JUnit. Lee credenciales locales de .env, obtiene tokens mediante Authorization Code + PKCE S256 y client_credentials y conserva los tokens únicamente en memoria. Las llamadas privadas se realizan con docker compose exec y curl dentro de la red interna; el token se transmite por stdin, nunca como argumento del proceso. El uso del formulario y cookies HTTP se limita al entorno localhost.

BackendIntegrationTest verifica, en orden:

1. Identidad y seguridad: emisión de sub/issuer/audiencias/roles, credenciales incorrectas, firma alterada, accesos por Gateway y defensa propia de los servicios.
2. Administración Fleet: altas, permisos, paginación, restricciones, habilitación, disponibilidad, coordenadas, vínculo concurrente y estado operativo idempotente.
3. Vehículos y solicitudes: dos conductores reales, privacidad de listas/detalles, duplicados, rangos, snapshot, historial, cancelación e idempotencia. Dos peticiones simultáneas con distintas claves producen un 201 y un 409; con la misma clave/cuerpo producen un 201 y un 200 para el mismo ID.
4. Persistencia: reinicia assistance-service, espera su disponibilidad y comprueba que la solicitud, snapshot e historial conservan exactamente sus datos.

La suite usa el perfil operator.demo. Se ejecuta sobre el entorno local destinado a pruebas: crea grúas TEST y vehículos TST, conserva solicitudes canceladas e historial y deja el operador demo UNAVAILABLE y las grúas nuevas INACTIVE/desvinculadas. Si ese operador tiene una grúa sin prefijo TEST, la suite se detiene para no desvincular un recurso ajeno a las pruebas. Crea un segundo usuario DRIVER temporal en Keycloak para verificar propiedad; lo elimina al finalizar mediante la cuenta bootstrap local. No se agregan credenciales administrativas a las aplicaciones.

Los datos de negocio de prueba permanecen en PostgreSQL para inspección. Un fallo intermedio puede dejar fixtures preparados parcialmente; la suite puede repetirse y utiliza identificadores únicos. El reinicio de Assistance forma parte de la prueba, por lo que no debe ejecutarse durante otra demostración simultánea.

## Reparación de identidad para instalaciones antiguas

Solo para un realm creado con la configuración original de fase 2 que carezca de scope basic:

```powershell
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.repairIdentity=true" "-Dtest=KeycloakScopeSetupTest"
```

La operación Java asocia basic a ambos clientes, verifica el resultado y conserva usuarios/datos. Es optativa e idempotente. Las instalaciones nuevas ya importan el scope corregido.

## Criterios para las fases siguientes

Las nuevas pruebas se escribirán en Java: JUnit/Mockito para decisiones aisladas y pruebas HTTP/JPA de integración para colaboración, persistencia y concurrencia. La colección Postman de fase 10 se mantiene como demostración ejecutable complementaria.

## Evaluación y rutas — fase 6

Las pruebas aisladas de Assistance usan un servidor HTTP local Java para respuestas ORS controladas; no consumen cuota externa. Comprueban interpretación métrica, caché, ausencia de ruta, 429, 403 de cuota o credenciales, respuesta inválida, ajuste a la red y clave ausente. Las pruebas de reglas verifican distancias inclusivas, redondeo hacia arriba, factor de duración, cobertura, ranking, habilitación estricta hasta fin planificado y descarte de una evaluación si ganó una cancelación. RoutingBudgetTest verifica cupos y pausas; CandidateServiceTest verifica filtrado y preselección.

La suite de integración de fases anteriores ahora espera también la evaluación inicial y su evento de historial; comprueba que evaluación y presupuesto de llamadas sobrevivan al reinicio. Utiliza el proveedor configurado en el contenedor: si hay clave válida puede consumir cuota; si falta, verifica la persistencia del resultado pendiente. No realiza reintentos inmediatos de la API externa.

Para exigir además una evaluación VIABLE con distancias reales:

```powershell
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.integration=true" "-Dauxiruta.realRouting=true" "-Dtest=BackendIntegrationTest"
```

El quinto caso prepara una grúa TEST compatible y disponible, actualiza su ubicación, crea una solicitud y verifica traslado, aproximación positiva, candidato elegible y ranking 1. Comprueba también el rechazo por destino fuera de cobertura. Al finalizar cancela su solicitud pendiente, inactiva/desvincula la grúa y deja al operador no disponible. Requiere ORS_API_KEY válida y cuota disponible; una caída externa hace fallar explícitamente este caso. Ejecutar la clase completa, ya que los casos preparan recursos en orden. Las pruebas aisladas siguen independientes del proveedor.

## Reservas y aceptación — fase 7

```powershell
.\fleet-service\mvnw.cmd -f fleet-service/pom.xml verify
.\assistance-service\mvnw.cmd -f assistance-service/pom.xml verify
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.phase7=true" "-Dtest=Phase7IntegrationTest"
```

ReservationServiceTest controla Clock y verifica ventana de reserva, vencimiento exclusivo, elegibilidad, liberación idempotente y conservación de una confirmación aunque venza su antigua retención. AssignmentCoordinatorTest verifica que una respuesta perdida no borre la intención ni produzca un reintento inmediato, que la compensación libere el mismo intento y que la recuperación conserve identificador y contenido.

Phase7IntegrationTest realiza seis comprobaciones contra PostgreSQL, Keycloak, Gateway, Fleet y Assistance reales:

1. Dos reservas simultáneas de los mismos recursos: una queda HELD y la otra obtiene resultado terminal; confirmación/liberación son idempotentes y un PUT tardío no revive la reserva liberada.
2. Carrera aceptación/cancelación: una operación vence y la otra devuelve 409; la solicitud termina ASSIGNED o CANCELLED coherentemente.
3. Aceptación durable: fija firstAssignedAt, impide cancelar y bloquea cambios de capacidad y registro de recursos comprometidos.
4. Rechazo con motivo: valida longitud, permite repetir la respuesta idéntica, rechaza contenido distinto y libera antes de otra propuesta.
5. Respuesta de confirmación perdida: prepara la intención durable en la base de prueba, confirma realmente en Fleet, comprueba que cancelar devuelve 409, reinicia Assistance y recupera la aceptación con la misma clave. firstAssignedAt conserva el instante original de Fleet.

6. Evidencia conservada durante la propuesta: bloquea la actualización de ubicación con 409, envejece las rutas del fixture más de 300 segundos y comprueba la aceptación sin nuevas llamadas a ORS.

Ejecutar esta suite por separado de BackendIntegrationTest, sin otras demostraciones concurrentes. Requiere operator.demo sin grúa vinculada al comenzar y clave ORS válida. Crea recursos TEST/TST y conserva sus filas para inspección. La preparación del quinto caso reproduce mediante SQL el punto exacto de interrupción; no existe un endpoint productivo para simular estados o modificar el reloj.

Para aislar los escenarios de reservas de la ejecución del traslado, el teardown de esta suite libera sus reservas por la API técnica y cierra exclusivamente sus propias filas de prueba mediante SQL, con motivo TEST_FIXTURE_CLEANUP. Esto es limpieza de fixtures, no una operación de negocio ni un caso de servicio completado. Deja la grúa nueva INACTIVE/desvinculada y el operador UNAVAILABLE. El reinicio de Assistance forma parte del quinto caso.

## Ejecución del servicio — fase 8

```powershell
.\assistance-service\mvnw.cmd -f assistance-service/pom.xml verify
.\fleet-service\mvnw.cmd -f fleet-service/pom.xml verify
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.phase8=true" "-Dtest=Phase8IntegrationTest"
```

ExecutionRulesTest verifica los predecesores de cada acción, vencimiento exclusivo del despacho y huellas de idempotencia. DispatchValidatorTest usa Clock fijo y dependencias controladas para ubicación antigua después de aceptar, sin rechazo por antigüedad, habilitación estricta hasta el fin estimado, reserva inválida, cuota temporal y ausencia confirmada de ruta. ExecutionServiceTest comprueba que un fallo de revalidación no aplique la transición y que repetir una entrega reintente su liberación sin volver a completarla.

Phase8IntegrationTest ejecuta tres escenarios con los contenedores y rutas reales:

1. Recorrido completo desde una asignación aceptada: rechaza saltos de estado, roles incorrectos y otro operador no asignado, exige nota válida, verifica historial único e idempotencia, llega a COMPLETED, libera la reserva y permite editar el vehículo nuevamente. La grúa conserva ACTIVE después de liberarse.
2. Despacho inválido por ubicación antigua y plazo vencido. Modifica exclusivamente los timestamps de sus fixtures mediante SQL; el backend no expone endpoints para alterar tiempos. Ninguna llamada rechazada declara la salida; el scheduler puede iniciar la alternativa por vencimiento antes de la consulta posterior.
3. Caída de Fleet durante la entrega: detiene ese contenedor, completa localmente con resourceReleasePending, comprueba que otra solicitud del mismo vehículo devuelve 409 y reinicia Fleet. Repetir la entrega con su clave original no duplica el evento; la prueba espera la liberación, que también puede resolver el scheduler.

Requiere ORS_API_KEY válida, operador demo libre y ejecución aislada de otras suites/demostraciones. Reutiliza la preparación de recursos de la suite de fase 7. Los servicios completados normalmente quedan conservados como COMPLETED; el teardown libera y cierra solamente los fixtures que quedaron a mitad del proceso. No constituye un endpoint de negocio. Fleet se reinicia incluso si el escenario de caída falla; las grúas de prueba terminan inactivas/desvinculadas y el operador no disponible.

La suite de fase 8 crea una identidad temporal adicional para verificar el 404 de otro operador y la elimina al finalizar. Esa administración usa solo las credenciales locales de prueba, sin agregarlas a las aplicaciones.

## Automatización y recuperación — fase 9

Las pruebas aisladas `AutomationStoreTest` y `AutomationConfigurationTest` usan Clock fijo y dependencias controladas: vencimiento, incertidumbre de confirmación, cinco ofertas, dos reemplazos, espera de liberación, exclusión de estados en ejecución, plazos demo y backoff. Fleet comprueba la ventana demo y la repetición de reservas persistidas antes de añadir los campos temporales.

Para la integración, iniciar desde la raíz en PowerShell:

```powershell
$env:SPRING_PROFILES_ACTIVE = "docker,demo"
docker compose up -d --build
.\api-gateway\mvnw.cmd -f api-gateway/pom.xml test "-Dauxiruta.phase9=true" "-Dtest=Phase9IntegrationTest"
```

Esperar que los servicios hayan iniciado antes de ejecutar la suite. Requiere ORS real con cuota, operador demo sin grúa vinculada y ninguna otra prueba o demostración simultánea. La suite verifica ocho escenarios:

1. Disponibilidad posterior a la solicitud: búsqueda automática, propuesta y vencimiento sin cambiar el número ni el plazo del intento.
2. Cierre automático de búsqueda sin recursos.
3. Dos reemplazos por despacho vencido y cierre al agotar el límite, sin restaurar el derecho a cancelar.
4. Confirmación remota realizada con respuesta perdida: recuperación tras reiniciar Assistance, aun vencido el plazo de búsqueda y sin repetir la aceptación.
5. Reserva incierta y cancelación: compensación automática del mismo identificador tras reiniciar.
6. Entrega durante caída de Fleet: conservación de COMPLETED y liberación tras reiniciar ambos servicios, sin repetir la entrega.
7. Cinco propuestas vencidas: cierre por cupo sin extender el plazo original.
8. Rechazo explícito: el operador queda excluido de nuevas propuestas en el segundo intento de esa solicitud.

El perfil demo utiliza búsqueda de 60 s, evaluación cada 2 s, retención de 10 s, ventana mínima de 3 s y despacho de 20 s. Las cuotas del proveedor, cupos, ubicación y estimaciones del traslado permanecen iguales. Las solicitudes conservan el snapshot con el que nacieron; cambiar el perfil afecta las nuevas solicitudes.

La prueba modifica exclusivamente timestamps e intenciones de sus propias filas para reproducir puntos de caída y límites sin largas esperas. Conserva el resultado de servicios completados y limpia las reservas de sus fixtures con la API técnica. No agrega endpoints productivos para adelantar el reloj o forzar estados. La paginación de propuestas permite repetir las suites aunque haya más de cien registros previos.

Restaurar el entorno normal antes de ejecutar las suites de fases anteriores:

```powershell
$env:SPRING_PROFILES_ACTIVE = "docker"
docker compose up -d
Remove-Item Env:SPRING_PROFILES_ACTIVE
```

Las suites de fases 7, 8 y 9 se ejecutan por separado. Las comprobaciones observan la recuperación eventual: una respuesta HTTP exitosa de entrega no significa que la dependencia remota ya haya confirmado su liberación.


## Entrega — fase 10

DeliveryArtifactsTest verifica que los 47 pedidos publicados coincidan con su definición Java. DeliveryIntegrationTest ejecuta esas solicitudes, compara los contratos OpenAPI con los controladores y comprueba el recorrido, estados y efectos. DemoDataSetupTest prepara el operador por APIs. La guía [ENTREGA.md](ENTREGA.md) documenta todos los comandos y escenarios. Verify-AuxiRuta.ps1 reúne las suites anteriores, con integración y perfil demo opcionales; no ejecutarlas simultáneamente.

## Preparación persistente de privacidad

PrivacyDemoSetupTest se habilita con -Dauxiruta.privacySeed=true y -Dtest=PrivacyDemoSetupTest en api-gateway. Crea las cuentas secundarias y fixtures persistentes, verifica privacidad por API y ejecuta las diez peticiones publicadas. No necesita ORS. También se invoca desde DemoDataSetupTest. Instrucciones en [DEMO_PRIVACIDAD.md](DEMO_PRIVACIDAD.md).
