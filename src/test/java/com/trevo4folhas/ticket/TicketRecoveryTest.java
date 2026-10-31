package com.trevo4folhas.ticket;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.fasterxml.jackson.databind.ObjectMapper;

class TicketRecoveryTest {
  @Test
  void requestKeyCollisionRecoversThroughSeparateWriterMethodAfterFailure() {
    TicketWriter writer = mock(TicketWriter.class);
    var key = UUID.randomUUID();
    var request =
        new TicketRequests.CreateTicket(
            "Pessoa fictícia",
            "11999999999",
            new BigDecimal("10.00"),
            true,
            true,
            TicketRules.VERSION,
            List.of(new TicketRequests.Item(UUID.randomUUID(), 1L, 1L, 1L)));
    when(writer.issue(eq(key), eq("session"), any()))
        .thenThrow(
            new DataIntegrityViolationException(
                "synthetic",
                new SQLException(
                    "duplicate key violates unique constraint \"uq_tickets_request_key\"",
                    "23505")));
    var recovered = new TicketWriter.Result(null, false);
    when(writer.recover(eq(key), eq("session"), anyString())).thenReturn(recovered);
    assertThat(new TicketService(writer, new ObjectMapper()).create(key, "session", request))
        .isSameAs(recovered);
    var order = inOrder(writer);
    order.verify(writer).issue(eq(key), eq("session"), any());
    order.verify(writer).recover(eq(key), eq("session"), anyString());
  }
}
