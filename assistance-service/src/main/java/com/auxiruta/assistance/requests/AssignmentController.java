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

@RestController
@RequestMapping("/api/v1")
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
public class AssignmentController {
  public record Rejection(@NotBlank @Size(min = 10, max = 500) String reason) {}

  private final AssignmentStore store;
  private final AssignmentCoordinator coordinator;

  public AssignmentController(AssignmentStore store, AssignmentCoordinator coordinator) {
    this.store = store;
    this.coordinator = coordinator;
  }

  @GetMapping("/operator/assignments")
  @Operation(summary = "OPERATOR: consultar propuestas y servicios propios")
  public RequestDtos.PageView<AssignmentStore.View> own(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return store.own(jwt.getSubject(), page, size);
  }

  @GetMapping("/assignments/{id}")
  @Operation(summary = "Consultar asignación propia; ADMIN puede consultar globalmente")
  public AssignmentStore.View get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return store.visible(id, jwt);
  }

  @PostMapping("/assignments/{id}/acceptance")
  @Operation(summary = "OPERATOR destinatario: confirmar una propuesta vigente con Idempotency-Key")
  public AssignmentStore.View accept(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") String key) {
    coordinator.execute(store.respond(id, jwt.getSubject(), key, true, null));
    return store.visible(id, jwt);
  }

  @PostMapping("/assignments/{id}/rejection")
  @Operation(
      summary = "OPERATOR destinatario: rechazar con motivo y liberar antes de otra propuesta")
  public AssignmentStore.View reject(
      @PathVariable UUID id,
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody Rejection body) {
    coordinator.execute(store.respond(id, jwt.getSubject(), key, false, body.reason()));
    var result = store.visible(id, jwt);
    coordinator.propose(result.requestId());
    return result;
  }
}
