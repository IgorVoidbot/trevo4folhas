package com.trevo4folhas.ticket;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Component;

import com.trevo4folhas.catalog.CatalogProperties;
import com.trevo4folhas.ticket.dto.TicketItemSnapshotDto;

@Component
public class TicketSummary {
  private final CatalogProperties config;
  private final Clock clock;

  public TicketSummary(CatalogProperties config, Clock clock) {
    this.config = config;
    this.clock = clock;
  }

  public TicketReceipt receipt(TicketStore.Stored ticket, List<TicketItemSnapshotDto> items) {
    TicketStatus status =
        ticket.status() == TicketStatus.PENDING && !clock.instant().isBefore(ticket.expiresAt())
            ? TicketStatus.EXPIRED
            : ticket.status();
    String expiry =
        DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")
            .withZone(ZoneId.of(config.bankTimeZone()))
            .format(ticket.expiresAt());
    String amount = ticket.amount().toPlainString(),
        total = ticket.odds().stripTrailingZeros().toPlainString(),
        potential = ticket.potentialReturn().toPlainString();
    String footer =
        "Valor: R$ "
            + amount.replace('.', ',')
            + "\nCotação combinada: "
            + total.replace('.', ',')
            + "\nRetorno potencial: R$ "
            + potential.replace('.', ',')
            + "\nValidade: "
            + expiry
            + " ("
            + config.bankTimeZone()
            + ")\nSituação: "
            + (status == TicketStatus.PENDING
                ? "Pendente — válido somente após confirmação da banca."
                : "Prazo encerrado ou pedido já decidido. Consulte a banca.");
    StringBuilder full =
        new StringBuilder("Olá! Quero validar meu pré-bilhete TREVO4FOLHAS.com.\nCódigo: ")
            .append(ticket.code())
            .append("\nNome: ")
            .append(ticket.name())
            .append("\nPalpites:\n");
    for (var item : items)
      full.append(item.homeTeamSnapshot())
          .append(" × ")
          .append(item.awayTeamSnapshot())
          .append(" — ")
          .append(item.marketSnapshot())
          .append(" — ")
          .append(item.selectionSnapshot())
          .append(" — cotação ")
          .append(item.selectedOdds().toPlainString().replace('.', ','))
          .append("\nPeríodo: ")
          .append("FT".equals(item.periodSnapshot()) ? "Tempo regulamentar" : item.periodSnapshot())
          .append("; linha: ")
          .append("NONE".equals(item.lineSnapshot()) ? "não se aplica" : item.lineSnapshot())
          .append("; participante: ")
          .append(
              "NONE".equals(item.participantSnapshot())
                  ? "não se aplica"
                  : item.participantSnapshot())
          .append("\nRegra: ")
          .append(item.ruleSnapshot())
          .append('\n');
    full.append(footer);
    String summary = full.toString();
    String message =
        summary.getBytes(StandardCharsets.UTF_8).length <= 1500
            ? summary
            : "Olá! Quero validar meu pré-bilhete TREVO4FOLHAS.com.\nCódigo: "
                + ticket.code()
                + "\n"
                + footer
                + "\nMensagem curta. Copie também o resumo completo exibido no pré-bilhete para conferir todos os palpites e regras.";
    return new TicketReceipt(
        ticket.id(),
        ticket.code(),
        status,
        ticket.createdAt(),
        ticket.expiresAt(),
        amount,
        total,
        potential,
        ticket.name(),
        ticket.phone(),
        ticket.rulesVersion(),
        items,
        summary,
        message,
        config.bankWhatsappNumber().isEmpty() ? null : config.bankWhatsappNumber(),
        ticket.clickedAt());
  }
}
