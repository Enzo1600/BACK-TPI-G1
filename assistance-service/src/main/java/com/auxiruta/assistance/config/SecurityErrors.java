package com.auxiruta.assistance.config;

import com.auxiruta.commons.errors.Problems;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

@Component
public class SecurityErrors implements AuthenticationEntryPoint, AccessDeniedHandler {
  private final ObjectMapper mapper;

  public SecurityErrors(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException e)
      throws IOException {
    write(res, 401);
  }

  public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException e)
      throws IOException {
    write(res, 403);
  }

  private void write(HttpServletResponse response, int status) throws IOException {
    response.setStatus(status);
    response.setContentType("application/problem+json");
    if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
    mapper.writeValue(response.getOutputStream(), Problems.of(status));
  }
}
