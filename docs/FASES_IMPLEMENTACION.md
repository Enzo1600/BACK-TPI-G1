# Plan de implementación de AuxiRuta

Plan acordado por el equipo. Cada fase verifica la integridad de la anterior y registra construcción, correcciones, pruebas y explicación del código en [cambios.md](cambios.md). La línea base funcional permanece en [DECISIONES_ARQUITECTONICAS.md](DECISIONES_ARQUITECTONICAS.md).

| Fase | Qué se construye | Resultado verificable |
|---|---|---|
| 1. Estructura y compilación | Cuatro proyectos Maven: commons, Fleet, Assistance y Gateway; Wrapper, versiones, dependencias y configuración inicial. | Compilan y ambos microservicios consumen el JAR común instalado en .m2. |
| 2. Infraestructura e identidad | Dockerfiles, Compose, PostgreSQL con tres bases, Keycloak, realm, roles, clientes y variables de entorno. | Arranque y emisión de tokens humanos/técnicos con audiencias previstas. |
| 3. Seguridad, Gateway y elementos comunes | Rutas, validación JWT, roles, identidad, errores comunes y logging básico. | Accesos permitidos/rechazados y protección de rutas internas. |
| 4. Administración de flota | Entidades, SQL, repositorios, DTOs; operadores, habilitaciones, grúas, vínculos, disponibilidad y ubicación. | ADMIN prepara recursos; OPERATOR consulta su perfil/equipo y declara disponibilidad/ubicación; restricciones administrativas verificadas. |
| 5. Vehículos y solicitudes | Vehículos propios, creación/consulta de solicitudes, historial, snapshots, idempotencia y cancelación previa a aceptación. | Solicitud persistente; propiedad, duplicados y exclusividad por vehículo. |
| 6. Evaluación y API externa | OpenRouteService, cobertura, recorridos, tiempos, candidatos, compatibilidad, ranking, errores y cuotas. | Evaluación justificable con rutas reales; distinción entre rechazo de dominio y falla temporal. |
| 7. Reserva, propuestas y aceptación | Reservas exclusivas, propuestas, aceptación/rechazo, confirmación durable y recuperación pendiente. | ASSIGNED; exclusividad de grúa/operador; carrera aceptación/cancelación. |
| 8. Ejecución y finalización | Revalidación y secuencia DISPATCHED → ARRIVED → VEHICLE_LOADED → IN_TRANSIT → COMPLETED, historial y liberación. | Proceso principal completo y recursos libres. |
| 9. Automatización y recuperación completa | Búsquedas periódicas, vencimientos, cupos, reemplazos previos al despacho, reconciliación y perfil demo. | Resolución automática, límites y recuperación tras reinicios. |
| 10. Verificación y entrega | Colección Postman, entornos, datos reproducibles, casos positivos/negativos, integración, OpenAPI y guía final. | Otro integrante puede ejecutar y verificar el compromiso. |

Las restricciones administrativas de la fase 4 incluyen validación, unicidad, exclusividad del vínculo, control de identidad y consistencia local. El rechazo de modificaciones por reservas HELD/CONFIRMED se conecta en fase 7, cuando se incorporan dichas reservas; no se simula una ocupación persistente en la fase 4.

Desde fase 5, todas las pruebas automatizadas mantenidas se escriben en Java (JUnit/Mockito e integración HTTP/JPA). Las pruebas anteriores en Python se reemplazaron. La colección Postman de fase 10 complementa estas pruebas. Véase [PRUEBAS_JAVA.md](PRUEBAS_JAVA.md).

## Avance al 8 de noviembre de 2026

Fases 1 a 6 implementadas. La fase 6 ejecuta una evaluación inicial sincrónica después de persistir la solicitud y guarda resultados/candidatos. VIABLE es resultado de evaluación, no un estado de asignación: la solicitud permanece PENDING_EVALUATION hasta incorporar propuestas/reservas en fase 7. Los reintentos, cierres por plazo y recuperación automática se integran en fase 9.
