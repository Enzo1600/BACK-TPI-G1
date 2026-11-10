package com.auxiruta.assistance.requests;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import static com.auxiruta.assistance.requests.ExecutionStore.Step;

@RestController
@RequestMapping("/api/v1/assistance-requests")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
  @ApiResponse(responseCode = "400", description = "Entrada invalida"),
  @ApiResponse(responseCode = "401", description = "JWT ausente o invalido"),
  @ApiResponse(responseCode = "403", description = "Rol insuficiente"),
  @ApiResponse(responseCode = "404", description = "Recurso inexistente o ajeno"),
  @ApiResponse(responseCode = "409", description = "Conflicto de estado, plazo o idempotencia"),
  @ApiResponse(
      responseCode = "503",
      description = "Dependencia temporalmente no disponible; consultar estado antes de reintentar")
})
public class ExecutionController {
  public record Completion(@NotBlank @Size(min = 10, max = 500) String note) {
    public Completion {
      if (note != null) note = note.strip();
    }
  }

  private final ExecutionService service;

  public ExecutionController(ExecutionService service) {
    this.service = service;
  }

  @PostMapping("/{id}/dispatch")
  @Operation(summary = "OPERATOR asignado: revalidar compromiso y rutas y declarar salida")
  public RequestDtos.HistoryView dispatch(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") String key) {
    return service.perform(id, jwt.getSubject(), key, Step.DISPATCH, null);
  }

  @PostMapping("/{id}/arrival")
  @Operation(summary = "OPERATOR asignado: registrar llegada al origen después del despacho")
  public RequestDtos.HistoryView arrival(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") String key) {
    return service.perform(id, jwt.getSubject(), key, Step.ARRIVAL, null);
  }

  @PostMapping("/{id}/loading")
  @Operation(summary = "OPERATOR asignado: declarar vehículo cargado después de la llegada")
  public RequestDtos.HistoryView loading(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") String key) {
    return service.perform(id, jwt.getSubject(), key, Step.LOADING, null);
  }

  @PostMapping("/{id}/transport-start")
  @Operation(summary = "OPERATOR asignado: iniciar traslado después de cargar el vehículo")
  public RequestDtos.HistoryView start(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") String key) {
    return service.perform(id, jwt.getSubject(), key, Step.TRANSPORT_START, null);
  }

  @PostMapping("/{id}/completion")
  @Operation(summary = "OPERATOR asignado: registrar entrega con nota y liberar recursos")
  public RequestDtos.HistoryView complete(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Completion body) {
    return service.perform(id, jwt.getSubject(), key, Step.COMPLETION, body.note());
  }
}
