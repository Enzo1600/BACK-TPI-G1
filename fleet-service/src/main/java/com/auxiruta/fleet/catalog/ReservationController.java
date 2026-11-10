package com.auxiruta.fleet.catalog;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Hidden;

@Hidden
@RestController
@RequestMapping("/internal/reservations")
public class ReservationController {
  private final ReservationService service;

  public ReservationController(ReservationService service) {
    this.service = service;
  }

  @PutMapping("/{id}")
  public ReservationService.View reserve(
      @PathVariable UUID id, @Valid @RequestBody ReservationService.Input input) {
    return service.reserve(id, input);
  }

  public record StatusQueries(
      @jakarta.validation.constraints.NotNull
          @jakarta.validation.constraints.Size(min = 1, max = 100)
          java.util.List<@jakarta.validation.constraints.NotNull UUID> assignmentAttemptIds) {}

  @PostMapping("/status-queries")
  public java.util.List<ReservationService.StatusQuery> statuses(
      @Valid @RequestBody StatusQueries input) {
    return input.assignmentAttemptIds().stream().distinct().map(service::statusQuery).toList();
  }

  @GetMapping("/{id}")
  public ReservationService.View get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PostMapping("/{id}/confirmation")
  public ReservationService.View confirm(@PathVariable UUID id) {
    return service.confirm(id);
  }

  @GetMapping("/{id}/eligibility")
  public ReservationService.Eligibility eligibility(@PathVariable UUID id) {
    return service.eligibility(id);
  }

  @PostMapping("/{id}/release")
  public ReservationService.View release(@PathVariable UUID id) {
    return service.release(id);
  }
}
