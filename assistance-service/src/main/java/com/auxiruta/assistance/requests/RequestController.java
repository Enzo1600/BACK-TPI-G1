package com.auxiruta.assistance.requests;
import java.util.UUID;
import java.net.URI;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import static com.auxiruta.assistance.requests.RequestDtos.*;
import static com.auxiruta.assistance.requests.DomainTypes.*;
@RestController @RequestMapping("/api/v1") @SecurityRequirement(name="bearerAuth")
@ApiResponses({@ApiResponse(responseCode="400",description="Entrada inválida"),@ApiResponse(responseCode="401",description="Autenticación requerida"),@ApiResponse(responseCode="403",description="Rol insuficiente"),@ApiResponse(responseCode="404",description="Recurso inexistente o ajeno"),@ApiResponse(responseCode="409",description="Conflicto de estado, exclusividad o idempotencia")})
public class RequestController {
 private final RequestService service;private final RulesConfiguration rules;private final EvaluationService evaluation;
 public RequestController(RequestService service,RulesConfiguration rules,EvaluationService evaluation) { this.service=service;this.rules=rules;this.evaluation=evaluation; }
 @ApiResponse(responseCode="201",description="Vehículo creado con Location")
 @PostMapping("/vehicles") @Operation(summary="DRIVER: registrar vehículo propio ACTIVE")
 public ResponseEntity<VehicleView> createVehicle(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CreateVehicle d) { var v=service.createVehicle(jwt.getSubject(),d);return ResponseEntity.created(URI.create("/api/v1/vehicles/"+v.id())).body(v); }
 @GetMapping("/vehicles") @Operation(summary="DRIVER: listar exclusivamente vehículos propios")
 public PageView<VehicleView> vehicles(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) Registration registrationStatus) { return service.vehicles(jwt.getSubject(),page,size,registrationStatus); }
 @GetMapping("/vehicles/{id}") @Operation(summary="DRIVER: consultar vehículo propio; ajeno e inexistente devuelven 404")
 public VehicleView vehicle(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) { return service.vehicle(id,jwt.getSubject()); }
 @PatchMapping("/vehicles/{id}") @Operation(summary="DRIVER: editar vehículo propio sin solicitud activa")
 public VehicleView edit(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody EditVehicle d) { return service.editVehicle(id,jwt.getSubject(),d); }
 @PatchMapping("/vehicles/{id}/registration-status") @Operation(summary="DRIVER: activar/inactivar vehículo sin solicitud activa")
 public VehicleView registration(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody RegistrationChange d) { return service.registration(id,jwt.getSubject(),d); }
 @ApiResponses({@ApiResponse(responseCode="201",description="Solicitud creada con Location"),@ApiResponse(responseCode="200",description="Solicitud recuperada por Idempotency-Key")})
 @PostMapping("/assistance-requests") @Operation(summary="DRIVER: crear y evaluar solicitud con snapshot; repetir Idempotency-Key y cuerpo recupera la solicitud")
 public ResponseEntity<RequestView> create(@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody CreateRequest d) {
  var result=service.createRequest(jwt.getSubject(),key,d);if(result.created())evaluation.evaluate(result.request().id());return ResponseEntity.status(result.created()?201:200).location(URI.create("/api/v1/assistance-requests/"+result.request().id())).body(service.request(result.request().id(),jwt));
 }
 @GetMapping("/assistance-requests") @Operation(summary="DRIVER: solicitudes propias; ADMIN: globales; OPERATOR: sin asignaciones hasta fase 7")
 public PageView<RequestView> requests(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) Status status) { return service.requests(jwt,page,size,status); }
 @GetMapping("/assistance-requests/{id}") @Operation(summary="Consultar solicitud autorizada; fuera del ámbito devuelve 404")
 public RequestView request(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt) { return service.request(id,jwt); }
 @GetMapping("/assistance-requests/{id}/history") @Operation(summary="Consultar historial paginado dentro del mismo ámbito de la solicitud")
 public PageView<HistoryView> history(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.history(id,jwt,page,size); }
 @PostMapping("/assistance-requests/{id}/cancellations") @Operation(summary="DRIVER propietario: cancelar antes de primera aceptación; Idempotency-Key identifica el evento durable")
 public HistoryView cancel(@PathVariable UUID id,@AuthenticationPrincipal Jwt jwt,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Cancellation d) { return service.cancel(id,jwt.getSubject(),key,d); }
 @GetMapping("/configuration/assistance") @Operation(summary="ADMIN: consultar reglas efectivas sin secretos")
 public java.util.Map<String,Object> configuration() { return rules.effective(); }
}
