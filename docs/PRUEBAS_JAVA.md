# Verificación con Java

A partir de la fase 5 todas las pruebas mantenidas del backend se implementan en Java. Las referencias a scripts Python en el historial de cambios describen ejecuciones anteriores y no son instrucciones vigentes.

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

La operación Java asocia basic a ambos clientes, verifica el resultado y conserva usuarios/datos. Es optativa e idempotente. Sustituye al antiguo ensure_basic_scope.py. Las instalaciones nuevas ya importan el scope corregido.

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
