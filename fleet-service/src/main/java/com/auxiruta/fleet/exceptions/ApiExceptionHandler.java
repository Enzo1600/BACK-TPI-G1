package com.auxiruta.fleet.exceptions;

import com.auxiruta.commons.errors.Problems;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.AccessDeniedException;
import org.slf4j.LoggerFactory;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return new ResponseEntity<>(Problems.of(status.value()), headers, status);
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ProblemDetail> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode()).body(Problems.of(e.getStatusCode().value()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ProblemDetail> forbidden(AccessDeniedException e) {
    return ResponseEntity.status(403).body(Problems.of(403));
  }

  @ExceptionHandler({
    org.springframework.dao.DataIntegrityViolationException.class,
    org.springframework.dao.ConcurrencyFailureException.class
  })
  ResponseEntity<ProblemDetail> conflict(Exception e) {
    return ResponseEntity.status(409).body(Problems.of(409));
  }

  @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
  ResponseEntity<ProblemDetail> invalidConstraint(Exception e) {
    return ResponseEntity.badRequest().body(Problems.of(400));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e) {
    LoggerFactory.getLogger(getClass())
        .error("Error inesperado tipo={}", e.getClass().getSimpleName());
    return ResponseEntity.status(500).body(Problems.of(500));
  }
}
