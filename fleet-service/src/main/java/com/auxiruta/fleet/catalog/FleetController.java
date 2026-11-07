package com.auxiruta.fleet.catalog;

import java.net.URI;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.*;
import static com.auxiruta.fleet.catalog.FleetDtos.*;
import static com.auxiruta.fleet.catalog.FleetTypes.*;

@RestController @RequestMapping("/api/v1")
@Tag(name="Fleet",description="Administración: ADMIN. Rutas me y assigned-to-me: OPERATOR, identidad tomada del JWT.")
@SecurityRequirement(name="bearerAuth")
@ApiResponses({@ApiResponse(responseCode="400",description="Entrada inválida"),@ApiResponse(responseCode="401",description="JWT ausente o inválido"),@ApiResponse(responseCode="403",description="Rol insuficiente"),@ApiResponse(responseCode="404",description="Recurso inexistente o ajeno"),@ApiResponse(responseCode="409",description="Duplicado o conflicto de estado")})
public class FleetController {
 private final FleetService service;
 public FleetController(FleetService service) { this.service=service; }
 @ApiResponse(responseCode="201",description="Perfil creado; Location contiene su URL")
 @PostMapping("/operators") @Operation(summary="ADMIN: registrar perfil de una identidad OPERATOR creada previamente en Keycloak")
 public ResponseEntity<OperatorView> createOperator(@Valid @RequestBody CreateOperator d) { var o=service.create(d);return ResponseEntity.created(URI.create("/api/v1/operators/"+o.id())).body(o); }
 @GetMapping("/operators") @Operation(summary="ADMIN: consultar operadores paginados y filtrar por registrationStatus")
 public PageView<OperatorView> operators(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) Registration registrationStatus) { return service.operators(page,size,registrationStatus); }
 @GetMapping("/operators/{id}") @Operation(summary="ADMIN: consultar un operador")
 public OperatorView operator(@PathVariable UUID id) { return service.getOperator(id); }
 @PatchMapping("/operators/{id}") @Operation(summary="ADMIN: actualizar nombre o teléfono; subject inmutable")
 public OperatorView editOperator(@PathVariable UUID id,@Valid @RequestBody EditOperator d) { return service.edit(id,d); }
 @PatchMapping("/operators/{id}/registration-status") @Operation(summary="ADMIN: activar o inactivar el perfil; inactivar retira su disponibilidad")
 public OperatorView registration(@PathVariable UUID id,@Valid @RequestBody RegistrationChange d) { return service.registration(id,d); }
 @PutMapping("/operators/{id}/qualification") @Operation(summary="ADMIN: registrar o renovar la habilitación LIGHT_TOWING")
 public OperatorView qualification(@PathVariable UUID id,@Valid @RequestBody QualificationChange d) { return service.qualification(id,d); }
 @GetMapping("/operators/me") @Operation(summary="OPERATOR: consultar el perfil asociado al sub del JWT")
 public OperatorView me(@AuthenticationPrincipal Jwt jwt) { return service.me(jwt.getSubject()); }
 @PatchMapping("/operators/me/availability") @Operation(summary="OPERATOR: declarar disponibilidad propia")
 public OperatorView availability(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody AvailabilityChange d) { return service.availability(jwt.getSubject(),d); }
 @ApiResponse(responseCode="201",description="Grúa creada; Location contiene su URL")
 @PostMapping("/tow-trucks") @Operation(summary="ADMIN: registrar una grúa INACTIVE, sin operador ni ubicación")
 public ResponseEntity<TruckView> createTruck(@Valid @RequestBody CreateTruck d) { var t=service.create(d);return ResponseEntity.created(URI.create("/api/v1/tow-trucks/"+t.id())).body(t); }
 @GetMapping("/tow-trucks") @Operation(summary="ADMIN: consultar grúas paginadas y filtrar por operationalStatus")
 public PageView<TruckView> trucks(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,@RequestParam(required=false) Operational operationalStatus) { return service.trucks(page,size,operationalStatus); }
 @GetMapping("/tow-trucks/{id}") @Operation(summary="ADMIN: consultar capacidades, vínculo y ubicación de una grúa")
 public TruckView truck(@PathVariable UUID id) { return service.getTruck(id); }
 @PatchMapping("/tow-trucks/{id}") @Operation(summary="ADMIN: actualizar datos y capacidades de una grúa")
 public TruckView editTruck(@PathVariable UUID id,@Valid @RequestBody EditTruck d) { return service.edit(id,d); }
 @PutMapping("/tow-trucks/{id}/operator") @Operation(summary="ADMIN: vincular un operador; rechaza sustitución implícita y vínculos duplicados")
 public TruckView link(@PathVariable UUID id,@Valid @RequestBody LinkOperator d) { return service.link(id,d); }
 @ApiResponse(responseCode="204",description="Vínculo retirado")
 @DeleteMapping("/tow-trucks/{id}/operator") @Operation(summary="ADMIN: desvincular el operador; repetición sin vínculo devuelve 204")
 public ResponseEntity<Void> unlink(@PathVariable UUID id) { service.unlink(id);return ResponseEntity.noContent().build(); }
 @PatchMapping("/tow-trucks/{id}/operational-status") @Operation(summary="ADMIN: cambiar estado con motivo e Idempotency-Key; devuelve el evento durable")
 public StatusView status(@PathVariable UUID id,@Valid @RequestBody StatusChange d,@RequestHeader("Idempotency-Key") String key,@AuthenticationPrincipal Jwt jwt) { return service.status(id,d,jwt.getSubject(),key); }
 @GetMapping("/tow-trucks/assigned-to-me") @Operation(summary="OPERATOR: consultar exclusivamente su grúa vinculada")
 public TruckView assigned(@AuthenticationPrincipal Jwt jwt) { return service.assigned(jwt.getSubject()); }
 @PutMapping("/tow-trucks/assigned-to-me/location") @Operation(summary="OPERATOR: registrar coordenadas de su grúa; el servidor fija locationUpdatedAt")
 public TruckView location(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Location d) { return service.location(jwt.getSubject(),d); }
 @GetMapping("/configuration/fleet") @Operation(summary="ADMIN: consultar límites efectivos de administración de Fleet")
 public AdministrationRules configuration() { return new AdministrationRules("AR-MVP-5",500,5000,100,Qualification.LIGHT_TOWING); }
 public record AdministrationRules(String rulesVersion,int minTruckCapacityKg,int maxTruckCapacityKg,int maxPageSize,Qualification requiredQualification) {}
}
