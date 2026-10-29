package com.trevo4folhas.config;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Access-denied handling split by client type (PRD 8.5 / S1.03). API calls receive {@code 403} JSON
 * ({@code CSRF_INVALID} for CSRF failures, {@code FORBIDDEN} otherwise); page flows are redirected
 * to the login screen with a clear pt-BR reason, which is the project's documented treatment for
 * expired sessions and tokens. CSRF failures always reject the request — no mutable route is ever
 * opened to fix a frontend token problem.
 */
public class ApiAwareAccessDeniedHandler implements AccessDeniedHandler {

  private static final String API_PREFIX = "/api/";
  private static final String LOGIN_PAGE = "/admin/login";

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException {
    boolean csrfFailure = isCsrfFailure(accessDeniedException);
    if (request.getRequestURI().startsWith(API_PREFIX)) {
      if (csrfFailure) {
        ApiErrorResponder.writeJson(
            response,
            HttpServletResponse.SC_FORBIDDEN,
            "CSRF_INVALID",
            "Sessão expirada ou token de segurança inválido. Recarregue a página e tente novamente.");
      } else {
        ApiErrorResponder.writeJson(
            response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Acesso negado.");
      }
      return;
    }
    if (csrfFailure) {
      response.sendRedirect(request.getContextPath() + LOGIN_PAGE + "?csrfError");
      return;
    }
    response.sendRedirect(request.getContextPath() + LOGIN_PAGE);
  }

  private static boolean isCsrfFailure(AccessDeniedException exception) {
    return exception instanceof MissingCsrfTokenException
        || exception instanceof InvalidCsrfTokenException;
  }
}
