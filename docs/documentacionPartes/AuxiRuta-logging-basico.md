# AuxiRuta — Logging básico incluido en el MVP

## Decisión y estado

Por decisión del equipo se incorporan logs básicos a Gateway, Assistance y Fleet. Es una ampliación elegida para diagnóstico, no un requisito adicional atribuido al enunciado del TPI.

El backend todavía no está implementado. Este documento define lo que se implementará al crear los servicios; no afirma que ya se estén generando logs.

Se utilizarán SLF4J y Logback mediante el logging incluido por los starters de Spring Boot. Las versiones quedan administradas por Boot. No hace falta agregar otro framework ni una base de datos.

Actuator, métricas, alertas, paneles, correlación distribuida automática y plataformas externas de logs siguen fuera del compromiso base. La auditoría persistente del negocio se conserva independientemente de los logs técnicos.

## Destino y formato

- Salida a consola, recogida por Docker. No se crean tablas de logs ni archivos propios en cada contenedor.
- Formato común: instante, nivel, nombre de servicio, clase y mensaje.
- Contexto mediante IDs internos: requestId, assignmentId y reservationId cuando existan.
- Los mensajes utilizan plantillas parametrizadas de SLF4J.
- Nivel general INFO; DEBUG habilitable por configuración para diagnóstico. SQL detallado y parámetros de Hibernate deshabilitados por defecto.
- Los logs se consultan con docker compose logs y se filtran por servicio o identificador. No se desarrolla una API para consultarlos.
- Retención de contenedores limitada mediante configuración de Docker: json-file, max-size 10m y max-file 3 por contenedor. Esta configuración básica evita crecimiento indefinido; no se incorpora un stack de observabilidad.

Los IDs del negocio permiten buscar mensajes relacionados sin implementar todavía X-Correlation-ID o tracing distribuido. Una petición rechazada antes de crear una solicitud puede registrarse únicamente con método, ruta normalizada y código HTTP.

## Niveles

| Nivel | Cuándo se usa | Ejemplo |
|---|---|---|
| INFO | Evento relevante completado o decisión del negocio | Solicitud creada, asignación confirmada, cambio de estado |
| WARN | Problema recuperable o situación operativa excepcional | Timeout remoto, reserva en conflicto, vencimiento de despacho |
| ERROR | Fallo técnico inesperado o recuperación técnica pendiente | Error de persistencia, configuración inválida, acción RETRYING |
| DEBUG | Detalle para investigar un problema | Cantidad de candidatos y tiempos de consultas |

Un rechazo previsto del negocio no es ERROR. Un timeout con reintento previsto normalmente es WARN. El mismo error no debe registrarse con stack trace en cada capa.

## Qué registra cada aplicación

| Aplicación | Eventos incluidos |
|---|---|
| Gateway | Método, ruta normalizada, código de respuesta y duración; fallos de enrutamiento y acceso sin exponer el token |
| Assistance | Creación de solicitud, resultado/motivo de evaluación, propuesta/aceptación/rechazo, transiciones, cancelaciones, reasignaciones y resultado de recuperación |
| Fleet | Reserva creada/confirmada/liberada/vencida, conflicto de exclusividad, cambio operativo de grúa, cambios administrativos relevantes de operador/vínculo y errores de persistencia |
| Cliente de rutas en Assistance | Proveedor/operación, resultado HTTP o tipo de error y duración; sin URL completa que incluya coordenadas o credenciales |

No se registra cada consulta exitosa de una tarea programada ni un mensaje INFO cada 15 segundos si no cambió nada. Se registra el resultado relevante y se usa DEBUG para detalle repetitivo.

## Ejemplos de salida esperada

```text
INFO  assistance-service Solicitud creada requestId=R123 vehicleId=V42
INFO  assistance-service Evaluación finalizada requestId=R123 result=VIABLE candidates=3
INFO  fleet-service Reserva creada assignmentId=A10 reservationId=RES7 truckId=G12
INFO  assistance-service Estado actualizado requestId=R123 from=AWAITING_ACCEPTANCE to=ASSIGNED
WARN  assistance-service Consulta de rutas agotó timeout requestId=R123 retryScheduled=true
WARN  assistance-service Plazo de despacho vencido requestId=R123 assignmentId=A10
INFO  fleet-service Reserva liberada reservationId=RES7 reason=REASSIGNMENT
ERROR assistance-service Recuperación técnica pendiente requestId=R123 actionId=ACT9
```

Un mensaje “confirmado” solo se emite tras confirmar el resultado local/remoto correspondiente. Si una respuesta se perdió se registra “resultado incierto”, y después su recuperación. El log no debe afirmar una transacción exitosa antes del commit.

## Privacidad y errores

- No registrar Authorization, JWT, contraseñas, secretos de clientes ni ORS_API_KEY.
- No volcar DTOs, entidades, cabeceras o cuerpos completos automáticamente.
- Evitar patente, teléfono, nombre completo, coordenadas exactas y descripción libre del usuario en logs. La información de negocio autorizada se consulta en su API/base.
- No registrar el cuerpo remoto sin sanitizar: puede contener datos o valores sensibles.
- Mantener la causa y stack trace de fallos técnicos para diagnóstico; devolver al cliente un ProblemDetail controlado.
- Mensajes operativos utilizan códigos/IDs y motivos clasificados, evitando inyección de saltos de línea desde textos del usuario.

## Comprobación al implementar

1. Completar un servicio y localizar creación, aceptación, transiciones y liberación por sus IDs.
2. Simular una falla de rutas y verificar WARN con reintento, sin aprobar a ciegas.
3. Producir un conflicto de reserva y verificar que no aparezcan dos mensajes de confirmación exitosa para recursos incompatibles.
4. Verificar que los logs no contengan credenciales, tokens ni datos personales completos.
5. Reiniciar aplicaciones y verificar que la verdad del negocio y acciones pendientes se recuperen desde PostgreSQL, sin depender de conservar logs.

Estos son criterios de verificación del logging elegido por el equipo; no sustituyen los escenarios funcionales de AR-MVP-5.

Las búsquedas alternativas anteriores al despacho son automáticas. Un error de infraestructura puede exigir mantenimiento técnico; los logs no habilitan al administrador a elegir estados, aprobar cancelaciones ni descartar reservas inciertas.
