package com.auxiruta.commons.errors;

import org.springframework.http.*;

public final class Problems {
  private Problems() {}

  public static ProblemDetail of(int status) {
    HttpStatus http = HttpStatus.resolve(status);
    ProblemDetail p =
        ProblemDetail.forStatusAndDetail(
            HttpStatusCode.valueOf(status),
            switch (status) {
              case 400 -> "Entrada invalida";
              case 401 -> "Autenticacion requerida o token invalido";
              case 403 -> "Permiso insuficiente";
              case 404 -> "Recurso no encontrado";
              case 409 -> "Conflicto con el estado del recurso";
              case 503 -> "Servicio temporalmente no disponible";
              default -> status >= 500 ? "Error interno" : "La solicitud no pudo procesarse";
            });
    p.setTitle(http == null ? "Error" : http.getReasonPhrase());
    p.setProperty("code", "HTTP_" + status);
    return p;
  }
}
