package com.trevo4folhas.ticket;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Retry only after the writer proxy has completed rollback; never reuse a failed transaction. */
@Service
public class TicketService {
  private final TicketWriter writer;
  private final ObjectMapper mapper;

  public TicketService(TicketWriter writer, ObjectMapper mapper) {
    this.writer = writer;
    this.mapper = mapper;
  }

  public TicketWriter.Result create(
      UUID key, String sessionHash, TicketRequests.CreateTicket request) {
    var intent = TicketIntent.normalize(request, mapper);
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        return writer.issue(key, sessionHash, intent);
      } catch (DataIntegrityViolationException failure) {
        String constraint = constraint(failure);
        if ("uq_tickets_request_key".equals(constraint))
          return writer.recover(key, sessionHash, intent.requestHash());
        if (!"uq_tickets_ticket_code".equals(constraint)) throw failure;
      }
    }
    throw new TicketException(
        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
        "CODE_UNAVAILABLE",
        "Não foi possível gerar um código. Tente novamente com esta mesma tentativa.");
  }

  private String constraint(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause())
      if (cause instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) {
        String message = sql.getMessage();
        if (message != null)
          for (String name : new String[] {"uq_tickets_request_key", "uq_tickets_ticket_code"})
            if (message.contains('"' + name + '"')) return name;
      }
    return null;
  }
}
