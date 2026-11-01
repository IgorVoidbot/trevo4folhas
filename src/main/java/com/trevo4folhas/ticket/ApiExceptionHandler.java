package com.trevo4folhas.ticket;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;

/** Fixed messages only: never echo bodies, SQL, rejected values, hashes or stack traces. */
@RestControllerAdvice(basePackageClasses = TicketController.class)
public class ApiExceptionHandler {
  record Error(String errorCode, String message) {}

  @ExceptionHandler(TicketException.class)
  ResponseEntity<Error> ticket(TicketException failure) {
    var response = ResponseEntity.status(failure.status()).cacheControl(CacheControl.noStore());
    if (failure.retryAfter() > 0)
      response.header("Retry-After", Long.toString(failure.retryAfter()));
    return response.body(new Error(failure.errorCode(), failure.getMessage()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ServletRequestBindingException.class,
    HandlerMethodValidationException.class,
    ConstraintViolationException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<Error> invalid(Exception failure) {
    return ticket(
        TicketException.invalid("Confira os campos, as declarações e as versões informadas."));
  }

  @ExceptionHandler({PessimisticLockingFailureException.class, TransactionTimedOutException.class})
  ResponseEntity<Error> busy(Exception failure) {
    return ticket(
        new TicketException(
            HttpStatus.CONFLICT,
            "TICKET_BUSY",
            "Esta tentativa está em processamento. Aguarde e tente novamente com os mesmos dados."));
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<Error> storage(Exception failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause())
      if (cause instanceof java.sql.SQLException sql
          && sql.getSQLState() != null
          && java.util.Set.of("55P03", "40P01", "40001", "57014").contains(sql.getSQLState()))
        return busy(failure);
    return ticket(
        new TicketException(
            HttpStatus.SERVICE_UNAVAILABLE,
            "STORAGE_UNAVAILABLE",
            "Não foi possível concluir o pedido. Preserve os dados e tente novamente com a mesma tentativa."));
  }
}
