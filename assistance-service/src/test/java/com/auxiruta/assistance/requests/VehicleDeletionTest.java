package com.auxiruta.assistance.requests;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VehicleDeletionTest {
  final VehicleRepository vehicles = mock(VehicleRepository.class);
  final RequestRepository requests = mock(RequestRepository.class);
  final RequestService service = new RequestService(vehicles, requests, null, null, null, null);
  final UUID id = UUID.randomUUID();

  @Test
  void deletesOwnedVehicleWithoutHistory() {
    Vehicle vehicle = new Vehicle();
    when(vehicles.lockOwned(id, "owner")).thenReturn(Optional.of(vehicle));
    service.deleteVehicle(id, "owner");
    verify(vehicles).delete(vehicle);
    verify(vehicles).flush();
  }

  @Test
  void hidesMissingOrForeignVehicle() {
    when(vehicles.lockOwned(id, "other")).thenReturn(Optional.empty());
    assertEquals(
        404,
        assertThrows(ResponseStatusException.class, () -> service.deleteVehicle(id, "other"))
            .getStatusCode()
            .value());
    verify(vehicles, never()).delete(any(Vehicle.class));
    verifyNoInteractions(requests);
  }

  @Test
  void preservesVehicleWithAnyRequestHistory() {
    when(vehicles.lockOwned(id, "owner")).thenReturn(Optional.of(new Vehicle()));
    when(requests.existsByVehicleId(id)).thenReturn(true);
    assertEquals(
        409,
        assertThrows(ResponseStatusException.class, () -> service.deleteVehicle(id, "owner"))
            .getStatusCode()
            .value());
    verify(vehicles, never()).delete(any(Vehicle.class));
  }
}
