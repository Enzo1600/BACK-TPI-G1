# Contrato privado Assistance → Fleet

Base interna: `http://fleet-service:8081`. Ninguna ruta `/internal/**` está expuesta por el Gateway. Las llamadas requieren Bearer JWT técnico, audiencia `auxiruta-fleet` y permiso `FLEET_SERVICE`, obtenidos con el cliente `assistance-service` y `client_credentials`. Un token humano no autoriza estas operaciones.

Los identificadores son UUID, fechas ISO-8601 UTC, distancias en metros, peso en kg y coordenadas WGS84. Las respuestas exitosas usan JSON y HTTP 200. Errores: 400 entrada inválida, 401 token ausente/inválido, 403 permiso insuficiente, 404 intento inexistente, 409 conflicto de contenido o estado. Los errores usan ProblemDetail con `status` y `code`. La respuesta EXPIRED es un resultado durable del negocio y no un fallo HTTP.

## Operaciones

| Método y ruta | Entrada | Resultado y efecto |
|---|---|---|
| GET `/internal/fleet/candidates` | Query: `latitude`, `longitude`, `vehicleType`, `weightKg`, `nonRollable` | Hasta cinco candidatos disponibles y compatibles, ordenados por distancia directa y UUID; no reserva recursos |
| PUT `/internal/reservations/{id}` | `ReservationInput` | Retiene grúa y operador exclusivos; repetir mismo UUID y contenido recupera el resultado; distinto contenido devuelve 409 |
| GET `/internal/reservations/{id}` | UUID del intento | Consulta el resultado durable; materializa vencimiento si sigue HELD y su plazo terminó |
| POST `/internal/reservations/{id}/confirmation` | Sin cuerpo | Confirma una retención vigente después de revalidar; repeticiones conservan `confirmedAt` |
| GET `/internal/reservations/{id}/eligibility` | UUID del intento | Revalida el compromiso para despacho sin autorizar una transición en Assistance |
| POST `/internal/reservations/{id}/release` | Sin cuerpo | Libera el compromiso; repetir no reactiva ni duplica liberación |
| POST `/internal/reservations/status-queries` | `{"assignmentAttemptIds":["UUID"]}`; 1–100 UUID no nulos | Consulta por lotes; IDs duplicados se consolidan; cada resultado tiene `assignmentAttemptId`, `status` y `resource` |

## Entrada de reserva

```json
{
  "requestId": "00000000-0000-4000-8000-000000000001",
  "towTruckId": "00000000-0000-4000-8000-000000000002",
  "operatorId": "00000000-0000-4000-8000-000000000003",
  "vehicleType": "CAR",
  "weightKg": 1000,
  "nonRollable": false,
  "searchDeadline": "2026-11-09T15:10:00Z",
  "estimatedServiceEndAt": "2026-11-09T16:00:00Z",
  "holdSeconds": 60,
  "minimumOfferSeconds": 15
}
```

Las fechas son ilustrativas; deben calcularse para el intento vigente. Los IDs de recursos deben existir. `vehicleType` admite CAR, SUV o PICKUP; peso 500–3000 kg. El par temporal admitido es 60/15 en modo normal o 10/3 para solicitudes demo. Omitir los dos campos temporales mantiene 60/15 por compatibilidad con intentos anteriores. La ventana termina en el menor entre ahora + retención y el fin de búsqueda; si no alcanza la ventana mínima no se publica una retención útil.

## Estructuras de salida

**Reserva:** `assignmentAttemptId`, `requestId`, `towTruckId`, `operatorId`, `status`, `expiresAt`, `confirmedAt`, `releasedAt`, `createdHold`. Fechas no aplicables pueden ser null. Estados: HELD, CONFIRMED, RELEASED y EXPIRED. `createdHold` distingue una retención efectivamente creada de un intento denegado. Una reserva terminal no revive con un PUT tardío.

**Elegibilidad:** `assignmentAttemptId`, `requestId`, `towTruckId`, `operatorId`, `operatorSubject`, `eligible`, `reason`, `latitude`, `longitude`, `locationUpdatedAt`, `qualificationValidUntil`, `checkedAt`. Requiere compromiso confirmado, recursos habilitados y ubicación apta para despacho. Assistance vuelve a comprobar identidad del intento, ruta, tiempos y vigencia antes de declarar salida.

**Candidato:** `towTruckId`, `operatorId`, `operatorSubject`, `capacityKg`, `supportedTypes`, `supportsNonRollable`, `qualificationCategory`, `qualificationValidFrom`, `qualificationValidUntil`, `availableSince`, `latitude`, `longitude`, `locationUpdatedAt`, `straightDistanceM`. La distancia directa permite preseleccionar; no sustituye el recorrido vial de ORS ni constituye una reserva.

**Consulta por lotes:** `status` puede ser MISSING además de los estados de reserva; `resource` contiene la estructura de elegibilidad solo si el compromiso está CONFIRMED. MISSING expresa inconsistencia o ausencia; no demuestra que un PUT incierto nunca vaya a producir efectos.

## Recuperación y exclusividad

Cada servicio administra su propia transacción y base. Assistance persiste la intención antes de llamar y conserva el mismo identificador y payload en reintentos. Si una reserva queda incierta, repite el PUT original y, si la solicitud terminó, libera ese mismo intento. Una confirmación incierta se consulta antes de repetirse y bloquea la cancelación hasta resolver el resultado. Las reservas CONFIRMED no vencen por el plazo de su antigua retención.

El flujo se verifica mediante Phase7IntegrationTest, Phase8IntegrationTest y Phase9IntegrationTest. La colección pública no exige credenciales técnicas ni publica estas rutas. Este documento describe el contrato privado complementario a los OpenAPI públicos; los records de ReservationService, CandidateService y FleetClient son sus DTOs implementados.
