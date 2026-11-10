package com.auxiruta.gateway.config;

import com.auxiruta.commons.errors.Problems;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.web.server.*;
import org.springframework.security.web.server.*;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.*;
import reactor.core.publisher.Mono;

@Component
@Order(-2)
public class SecurityErrors
    implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler, WebExceptionHandler {
  private final ObjectMapper mapper;

  public SecurityErrors(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public Mono<Void> commence(ServerWebExchange x, AuthenticationException e) {
    return write(x, 401);
  }

  public Mono<Void> handle(ServerWebExchange x, AccessDeniedException e) {
    return write(x, 403);
  }

  public Mono<Void> handle(ServerWebExchange x, Throwable e) {
    if (x.getResponse().isCommitted()) return Mono.error(e);
    return write(
        x, e instanceof ResponseStatusException status ? status.getStatusCode().value() : 503);
  }

  private Mono<Void> write(ServerWebExchange x, int status) {
    x.getResponse().setStatusCode(HttpStatusCode.valueOf(status));
    x.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    if (status == 401) x.getResponse().getHeaders().set("WWW-Authenticate", "Bearer");
    try {
      return x.getResponse()
          .writeWith(
              Mono.just(
                  x.getResponse()
                      .bufferFactory()
                      .wrap(mapper.writeValueAsBytes(Problems.of(status)))));
    } catch (Exception e) {
      return Mono.error(e);
    }
  }
}
