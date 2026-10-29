package com.trevo4folhas.catalog;

import org.springframework.http.HttpStatus;

public final class CatalogException extends RuntimeException {
  private final HttpStatus status;
  private final String errorCode;

  public CatalogException(HttpStatus status, String errorCode, String message) {
    super(message);
    this.status = status;
    this.errorCode = errorCode;
  }

  public HttpStatus status() { return status; }
  public String errorCode() { return errorCode; }
}
