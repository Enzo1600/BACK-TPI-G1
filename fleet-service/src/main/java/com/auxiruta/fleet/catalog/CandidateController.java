package com.auxiruta.fleet.catalog;

import java.util.List;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.Hidden;

@RestController
@Validated
@Hidden
public class CandidateController {
  private final CandidateService service;

  public CandidateController(CandidateService service) {
    this.service = service;
  }

  @GetMapping("/internal/fleet/candidates")
  public List<CandidateService.Candidate> candidates(
      @RequestParam @DecimalMin("-90") @DecimalMax("90") @Digits(integer = 3, fraction = 6)
          BigDecimal latitude,
      @RequestParam @DecimalMin("-180") @DecimalMax("180") @Digits(integer = 3, fraction = 6)
          BigDecimal longitude,
      @RequestParam FleetTypes.VehicleType vehicleType,
      @RequestParam @DecimalMin("500") @DecimalMax("3000") @Digits(integer = 4, fraction = 2)
          BigDecimal weightKg,
      @RequestParam boolean nonRollable) {
    return service.find(latitude, longitude, vehicleType, weightKg, nonRollable);
  }
}
