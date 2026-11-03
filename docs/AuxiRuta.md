# AuxiRuta — Coordinación de asistencia y remolque vehicular

> Revisión según los apuntes: esta es la descripción general. El contrato actual se precisa en `AuxiRuta-estados-y-transiciones.md`, `AuxiRuta-operaciones-por-rol-y-alcance-MVP.md` y `AuxiRuta-modelo-persistente-MVP.md`. Los ajustes se justifican en `AuxiRuta-revision-segun-apuntes.md`.

## Alcance

Un sistema donde los conductores solicitan asistencia para trasladar un vehículo inmovilizado. El sistema determina qué grúas pueden realizar el servicio según las características del vehículo, la capacidad del equipo, la disponibilidad del operador y el tiempo estimado de llegada.

El conductor informa la ubicación del vehículo y el destino del traslado. El sistema evalúa alternativas, reserva una grúa y acompaña el servicio hasta la entrega.

Para el MVP se contempla una única empresa de asistencia, solicitudes de atención inmediata y traslados dentro de una región definida. Cada grúa atiende un servicio a la vez.

Quedan fuera del alcance los pagos, las aseguradoras, las reparaciones mecánicas, el seguimiento GPS continuo y la atención de emergencias médicas.

## Actores y roles (Keycloak)

| Rol | Puede hacer |
|---|---|
| DRIVER | Administrar sus vehículos, solicitar asistencia, consultar sus solicitudes y cancelarlas antes del despacho |
| OPERATOR | Informar disponibilidad y ubicación de su grúa, aceptar asignaciones y registrar avances del servicio |
| COORDINATOR | Consultar todas las solicitudes, gestionar incidencias y solicitar reasignaciones |
| ADMIN | Operaciones de coordinación y administración de operadores y grúas; consulta de parámetros. Los parámetros se configuran en application.yml y variables de entorno |

La autorización necesita controles por rol y pertenencia: un conductor solo puede consultar sus solicitudes y un operador solo puede modificar los servicios asignados a él.

El coordinador puede intervenir en una asignación, pero no omitir restricciones de capacidad o compatibilidad.

## División en microservicios

**1. fleet-service (Flota y disponibilidad).** Es dueño de los operadores, sus habilitaciones, las grúas, sus capacidades, su estado operativo y las reservas de recursos.

Su regla central es responder: **“¿Esta grúa y este operador pueden atender este vehículo y están disponibles para hacerlo?”**.

También garantiza que una grúa o un operador no queden comprometidos con dos servicios simultáneamente.

**2. assistance-service (Solicitudes y asistencia).** Es dueño de los vehículos de los conductores, las solicitudes, las evaluaciones, las asignaciones y el historial del servicio.

Su regla central es responder: **“¿Qué recurso compatible puede atender esta solicitud dentro de las condiciones del servicio?”**.

La frontera es clara: Fleet determina capacidad y disponibilidad; Assistance evalúa la necesidad del conductor y coordina su resolución.

Cada servicio guarda sus datos. Assistance conserva un *snapshot* de la evaluación: características del vehículo, capacidad de la grúa seleccionada, ubicación informada, tiempo estimado de llegada y motivos de la decisión.

## Colaboración entre servicios

1. El conductor crea una solicitud mediante `POST /assistance-requests`, indicando vehículo, origen, destino y condición de inmovilización.
2. Assistance registra la solicitud en `REQUESTED`.
3. Consulta a Fleet por recursos compatibles con el tipo, peso y condición del vehículo.
4. Fleet devuelve candidatos disponibles, incluyendo su ubicación y la fecha de actualización.
5. Assistance consulta la API de rutas para estimar el tiempo de llegada de los candidatos.
6. Ordena las alternativas viables y solicita a Fleet una reserva temporal del recurso seleccionado.
7. Fleet verifica nuevamente la disponibilidad y reserva grúa y operador en una operación atómica.
8. La solicitud pasa a `AWAITING_ACCEPTANCE`. El operador acepta dentro del plazo establecido.
9. Al confirmar la reserva en Fleet, Assistance registra el servicio como `ASSIGNED`.

**Si el operador rechaza o vence el plazo:** se libera la reserva y se evalúa el siguiente candidato.

**Si otro servicio reservó el recurso primero:** Fleet responde con un conflicto y Assistance continúa con otra alternativa.

**Si Fleet no responde:** timeout corto y reintentos limitados mediante evaluación programada. La solicitud queda en `PENDING_EVALUATION`. El circuit breaker con Resilience4j es una mejora opcional, introducida conceptualmente en el apunte de clientes HTTP.

Las operaciones de reserva, confirmación y liberación son idempotentes y utilizan el identificador de la solicitud. Si se pierde una respuesta, Assistance consulta el resultado antes de repetir la operación.

## APIs externas

**OpenRouteService:** proporciona distancias y duraciones estimadas de recorridos. La integración requiere una clave y debe respetar los límites del servicio.

Se utiliza para calcular:

- El recorrido desde la ubicación informada de la grúa hasta el vehículo.
- El recorrido desde el vehículo hasta el destino solicitado.

La información interviene directamente en la selección. La grúa más cercana en línea recta puede tardar más en llegar por la disposición de las calles y los accesos.

Por ejemplo: una grúa compatible tiene una llegada estimada de 18 minutos y otra de 32. Si ambas cumplen las condiciones, se ofrece primero el servicio a la de menor tiempo estimado.

La duración es una estimación de recorrido; el sistema no presupone información de tránsito en tiempo real ni utiliza esa respuesta para certificar que una grúa pesada pueda circular por cualquier camino.

**Si la API falla:** la solicitud queda pendiente y se reintenta. Si responde correctamente que no existe una ruta para un candidato, ese candidato se descarta.

Documentación: [servicios de OpenRouteService](https://openrouteservice.org/services/) y [autenticación y ejemplos](https://openrouteservice.org/dev/).

## Reglas de negocio

- El conductor solo puede solicitar asistencia para un vehículo registrado a su nombre dentro del sistema.
- Un vehículo no puede tener dos solicitudes activas simultáneamente.
- El origen y el destino deben estar dentro de la región atendida.
- El peso declarado del vehículo no puede superar la capacidad de carga de la grúa.
- La grúa debe admitir el tipo de vehículo solicitado.
- Si el vehículo no puede rodar, debe asignarse un equipo compatible con esa condición.
- Grúa y operador deben estar activos y disponibles.
- La habilitación del operador debe permanecer vigente durante el período estimado del servicio.
- La ubicación informada de la grúa debe ser suficientemente reciente; si está vencida, el operador debe actualizarla antes de participar en la asignación.
- El tiempo estimado de llegada no debe superar el máximo configurado para el servicio.
- Entre los candidatos compatibles se prioriza el menor tiempo estimado de llegada. Los empates se resuelven por mayor tiempo disponible sin asignaciones.
- Una reserva temporal impide ofrecer la misma grúa u operador a otra solicitud.
- El operador debe aceptar antes del vencimiento de la reserva.
- Solo puede iniciarse el traslado después de registrar la llegada y la carga del vehículo.
- Después del despacho, el conductor debe solicitar la cancelación al coordinador.
- Después de cargar el vehículo, una interrupción se registra como incidencia y requiere intervención del coordinador.
- Toda transición registra fecha, responsable y motivo.

Los valores de antigüedad máxima de ubicación, plazo de aceptación y tiempo máximo de llegada se configuran en application.yml/variables de entorno. No se incluye edición dinámica ni tablas de configuración versionada. Las evaluaciones conservan los límites utilizados.

## Ciclo de vida de la solicitud

```text
REQUESTED
    ├→ PENDING_EVALUATION
    ├→ REJECTED
    ├→ CANCELLED
    └→ AWAITING_ACCEPTANCE
            ├→ SEARCHING_ALTERNATIVE
            │       ├→ AWAITING_ACCEPTANCE
            │       └→ UNFULFILLED
            ├→ CANCELLED
            └→ ASSIGNED
                    ├→ SEARCHING_ALTERNATIVE
                    ├→ CANCELLED
                    └→ DISPATCHED
                            ├→ ARRIVED
                            │       ├→ VEHICLE_LOADED
                            │       │       └→ IN_TRANSIT
                            │       │               ├→ COMPLETED
                            │       │               └→ SUSPENDED
                            │       └→ SUSPENDED
                            └→ SUSPENDED
```

`PENDING_EVALUATION` vuelve a evaluación cuando se recupera una dependencia. Si vence el plazo de búsqueda, pasa a `UNFULFILLED`.

- `REJECTED`: la solicitud incumple una condición del servicio, como un destino fuera de cobertura.
- `UNFULFILLED`: la solicitud es válida, pero no se consiguió un recurso dentro del plazo.
- `SEARCHING_ALTERNATIVE`: un operador rechazó la propuesta o se perdió la disponibilidad antes del despacho.
- `SUSPENDED`: una incidencia bloqueante impide continuar; se conserva la etapa previa. La incidencia tiene estado OPEN/RESOLVED independiente. Su resolución permite continuar, reasignar antes de cargar, cancelar antes de cargar o cerrar en CLOSED_UNRESOLVED con constancia de custodia.

Fleet vence sus reservas localmente. Assistance consulta a Fleet mediante RestClient para reintentar y reconciliar sus solicitudes; Fleet no llama a Assistance. Las tareas programadas son una ampliación acotada del proyecto, no un requisito técnico literal de los apuntes.

Antes del despacho se vuelven a verificar las condiciones del recurso. La selección, las reservas exclusivas, los vencimientos y las reasignaciones aportan el comportamiento principal del backend.

## Seguridad

- **Gateway con Spring Cloud Gateway:** valida JWT y aplica un Security Filter con restricciones por ruta y rol.
- **Keycloak:** administra identidad, autenticación mediante OpenID Connect y roles.
- **Microservicios:** funcionan como resource servers con Spring Security y validan sus propios permisos.
- **Control de pertenencia:** Assistance verifica conductor y operador asignado; Fleet verifica qué operador puede actualizar cada recurso.
- **Comunicación Assistance → Fleet:** utiliza `client_credentials` con permisos específicos para consultar, reservar, confirmar y liberar recursos.
- **Procesos programados:** utilizan una identidad técnica con permisos limitados.

La zona de confianza comprende la red interna de Docker y la validación de identidad y permisos en cada servicio.

El Gateway es el punto de entrada a las APIs del negocio. Keycloak permanece accesible para la autenticación; las bases y los microservicios no publican puertos al exterior.

## JAR común (auxiruta-commons)

Un artefacto Maven independiente, instalado en `.m2` y utilizado como dependencia por ambos microservicios.

Puede incluir:

- Conversión de roles de Keycloak a authorities de Spring Security.
- Formato estándar de errores con `ProblemDetail`.
- Manejo común de errores de validación.
- Utilidades para obtener la identidad autenticada.
- Identificadores de correlación para seguir solicitudes entre servicios.

Las entidades y los DTO de negocio permanecen dentro de cada microservicio. Las reglas de compatibilidad pertenecen a Fleet y las reglas de selección pertenecen a Assistance.

## Persistencia

Un contenedor PostgreSQL con bases y credenciales separadas:

- `fleet_db`: operadores, habilitaciones, grúas y reservas.
- `assistance_db`: vehículos, solicitudes, evaluaciones, asignaciones e historial.
- `keycloak_db`: datos administrados por Keycloak.

Cada servicio accede exclusivamente a su base.

Fleet garantiza la exclusividad de grúa y operador mediante transacciones locales y control de concurrencia. Assistance conserva el historial de intentos para explicar por qué se seleccionó o descartó cada alternativa.

Cuando una finalización o cancelación requiere liberar recursos, Assistance registra la acción pendiente y la reintenta hasta confirmar su aplicación en Fleet. Mientras tanto, el recurso permanece ocupado.

## Borrador de C1 y C2

**C1:** Conductor, Operador, Coordinador y Administrador utilizan AuxiRuta. El sistema se integra con Keycloak para autenticación y con OpenRouteService para estimar recorridos utilizados en la asignación.

**C2:**

- API Gateway.
- `fleet-service`.
- `assistance-service`.
- Keycloak.
- PostgreSQL con bases separadas.
- OpenRouteService como sistema externo.

Relaciones principales:

```text
Usuarios → Keycloak: autenticación
Usuarios → Gateway: solicitudes con JWT
Gateway → Fleet / Assistance: enrutamiento
Assistance → Fleet: compatibilidad y gestión de recursos
Assistance → OpenRouteService: estimación de recorridos
Gateway y microservicios → Keycloak: claves para validar JWT
Cada servicio → su base de datos
Keycloak → su base de datos
```

El JAR común se documenta como dependencia Maven, sin representarlo como un contenedor ejecutable.

## Documentación y despliegue

Los microservicios exponen APIs REST con JSON, validaciones, códigos HTTP coherentes y documentación OpenAPI/Swagger.

Docker Compose permite iniciar Gateway, microservicios, Keycloak y PostgreSQL, incluyendo configuración inicial de bases, realm, roles y clientes.

Los microservicios funcionales utilizan Spring MVC, RestClient y Spring Data JPA/Hibernate con JpaRepository. El Gateway utiliza la variante WebFlux mostrada en clase y seguridad reactiva; permanece separado de los servicios MVC.

Logs técnicos básicos para diagnóstico pueden utilizarse durante el desarrollo, sin declararlos como una funcionalidad evaluable adicional. Actuator, correlación, rotación de logs y plataformas ELK/Loki/Grafana quedan como mejoras opcionales fuera del compromiso del MVP. La auditoría funcional de estados y decisiones en la base sí pertenece al dominio comprometido.

La clave de la API externa se configura mediante variables de entorno y no se incorpora al repositorio.

## Alcance comprometido del MVP

El proceso demostrable será:

**Un conductor solicita un traslado → el sistema busca grúas compatibles → calcula tiempos de llegada → reserva una alternativa → el operador acepta → realiza el retiro → confirma la entrega → se libera el recurso.**

También se demostrarán:

- Rechazo por incompatibilidad de capacidad.
- Dos solicitudes que compiten por la misma grúa.
- Rechazo o vencimiento de una propuesta al operador.
- Reintento ante una dependencia temporalmente indisponible.
- Cancelación con liberación del recurso.

El seguimiento GPS, las notificaciones, los pagos y la integración con aseguradoras quedan fuera del compromiso inicial.
