package com.trevo4folhas.ticket;

import org.springframework.http.HttpStatus;

public class TicketException extends RuntimeException {
  private final HttpStatus status;
  private final String errorCode;
  private final long retryAfter;

  public TicketException(HttpStatus status, String code, String message) {
    this(status, code, message, 0);
  }

  public TicketException(HttpStatus status, String code, String message, long retryAfter) {
    super(message);
    this.status = status;
    this.errorCode = code;
    this.retryAfter = retryAfter;
  }

  public HttpStatus status() {
    return status;
  }

  public String errorCode() {
    return errorCode;
  }

  public long retryAfter() {
    return retryAfter;
  }

  public static TicketException conflict() {
    return new TicketException(
        HttpStatus.CONFLICT,
        "IDEMPOTENCY_CONFLICT",
        "Esta tentativa não corresponde à sessão ou aos dados originais. Nenhum dado de outro pedido foi recuperado.");
  }

  public static TicketException unavailable() {
    return new TicketException(
        HttpStatus.CONFLICT,
        "CATALOG_CHANGED",
        "Uma oferta mudou ou está indisponível. Atualize o catálogo e revise seus palpites.");
  }

  public static TicketException invalid(String message) {
    return new TicketException(HttpStatus.BAD_REQUEST, "INVALID_TICKET", message);
  }
}
