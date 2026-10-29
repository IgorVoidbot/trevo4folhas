package com.trevo4folhas.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes the project's error contract (PRD 8.5): HTTP status plus {@code errorCode} in English and
 * a generic pt-BR {@code message}, with no stack trace, credential or internal field. Responses are
 * never cached.
 */
public final class ApiErrorResponder {

  private ApiErrorResponder() {}

  public static void writeJson(
      HttpServletResponse response, int status, String errorCode, String message)
      throws IOException {
    if (response.isCommitted()) {
      return;
    }
    response.setStatus(status);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON.toString());
    response.setHeader("Cache-Control", "no-store");
    String body =
        "{\"errorCode\":\"" + escape(errorCode) + "\",\"message\":\"" + escape(message) + "\"}";
    response.getWriter().write(body);
  }

  /** Minimal JSON string escaping for the fixed literals this class writes. */
  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
