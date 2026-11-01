package com.trevo4folhas.config;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authentication entry point split by client type (PRD 8.5 / S1.03): API calls without a valid
 * admin session receive {@code 401} JSON ({@code UNAUTHORIZED}) while page navigation is redirected
 * to the admin login screen. Neither response reveals whether a session ever existed.
 */
public class ApiAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final String API_PREFIX = "/api/";
  private static final String LOGIN_PAGE = "/admin/login";

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    if ((request.getRequestURI().startsWith(API_PREFIX)
        || request.getRequestURI().startsWith("/actuator/"))) {
      ApiErrorResponder.writeJson(
          response,
          HttpServletResponse.SC_UNAUTHORIZED,
          "UNAUTHORIZED",
          "Sessão administrativa ausente ou expirada.");
      return;
    }
    response.sendRedirect(request.getContextPath() + LOGIN_PAGE);
  }
}
