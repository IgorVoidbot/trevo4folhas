package com.trevo4folhas.catalog;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;

/** Fixed Portuguese messages: never echo SQL, rejected values, request bodies or stack traces. */
@RestControllerAdvice(basePackages = {"com.trevo4folhas.catalog", "com.trevo4folhas.sports"})
public class CatalogExceptionHandler {

  @ExceptionHandler(com.trevo4folhas.sports.SyncFailure.class)
  ResponseEntity<ErrorResponse> sync(com.trevo4folhas.sports.SyncFailure failure) {
    return response(
        HttpStatus.CONFLICT,
        "SYNC_BLOCKED",
        "Sincronização bloqueada. Confira o estado da integração.");
  }

  @ExceptionHandler(CatalogException.class)
  ResponseEntity<ErrorResponse> catalog(CatalogException failure) {
    return response(failure.status(), failure.errorCode(), failure.getMessage());
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    HandlerMethodValidationException.class,
    ConstraintViolationException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ErrorResponse> invalid(Exception failure) {
    return response(
        HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Confira os campos e parâmetros informados.");
  }

  @ExceptionHandler({
    DataIntegrityViolationException.class,
    PessimisticLockingFailureException.class
  })
  ResponseEntity<ErrorResponse> conflict(Exception failure) {
    return response(
        HttpStatus.CONFLICT,
        "CATALOG_CONFLICT",
        "O catálogo foi alterado ou está ocupado. Atualize os dados e tente novamente.");
  }

  private ResponseEntity<ErrorResponse> response(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .body(new ErrorResponse(code, message));
  }

  public record ErrorResponse(String errorCode, String message) {}
}
