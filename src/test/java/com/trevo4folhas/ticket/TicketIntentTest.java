package com.trevo4folhas.ticket;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class TicketIntentTest {
  @Test
  void rejectsInvalidIdentityConsentAndRepeatedSelectionBeforeFingerprinting() {
    var item = new TicketRequests.Item(UUID.randomUUID(), 1L, 1L, 1L);
    var mapper = new ObjectMapper();
    for (var request :
        List.of(
            new TicketRequests.CreateTicket(
                "Pessoa\nprivada",
                "11999999999",
                new BigDecimal("10"),
                true,
                true,
                TicketRules.VERSION,
                List.of(item)),
            new TicketRequests.CreateTicket(
                "Pessoa privada",
                "telefone inválido",
                new BigDecimal("10"),
                true,
                true,
                TicketRules.VERSION,
                List.of(item)),
            new TicketRequests.CreateTicket(
                "Pessoa privada",
                "11999999999",
                new BigDecimal("10"),
                false,
                true,
                TicketRules.VERSION,
                List.of(item)),
            new TicketRequests.CreateTicket(
                "Pessoa privada",
                "11999999999",
                new BigDecimal("10"),
                true,
                true,
                TicketRules.VERSION,
                List.of(item, item))))
      assertThatThrownBy(() -> TicketIntent.normalize(request, mapper))
          .isInstanceOf(TicketException.class);
    var accepted =
        TicketIntent.normalize(
            new TicketRequests.CreateTicket(
                "Pessoa privada",
                "11999999999",
                new BigDecimal("10"),
                true,
                true,
                TicketRules.VERSION,
                List.of(item)),
            mapper);
    assertThat(accepted.toString()).doesNotContain("Pessoa", "11999999999");
  }
}
