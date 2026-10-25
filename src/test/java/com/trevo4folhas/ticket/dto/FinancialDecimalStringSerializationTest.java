package com.trevo4folhas.ticket.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trevo4folhas.ticket.TicketStatus;

/**
 * Regression suite for the decimal-string JSON contract (review round 1, R2): PRD §8.4/§8.5
 * requires every financial decimal crossing the JSON boundary to be a JSON string (for example
 * {@code "10.00"}), while the Java side keeps {@link BigDecimal} with its exact precision and
 * scale. Serialization is checked with a plain {@link ObjectMapper} (no Spring configuration), so
 * the guarantee lives on the DTOs themselves and never depends on double/float or global mapper
 * tuning. Exact text is asserted, not just the node kind.
 */
class FinancialDecimalStringSerializationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

  /** Full {@code numeric(41,20)} width, as stored in {@code tickets.total_odds}. */
  private static final String WIDE_TOTAL_ODDS = "12345678901234567890.01234567890123456789";

  /** Exact product of ten two-decimal odds: scale 20, the {@code numeric(41,20)} product shape. */
  private static final String TEN_ODDS_PRODUCT = "2280.73660815000000000000";

  private static final List<String> TEN_ODDS =
      List.of("2.50", "3.10", "1.90", "1.85", "2.05", "1.75", "2.20", "1.95", "3.40", "1.60");

  @Test
  void ticketFinancialFieldsSerializeAsDecimalStrings() throws Exception {
    TicketDto dto =
        ticket(
            new BigDecimal("10.00"),
            new BigDecimal(WIDE_TOTAL_ODDS),
            new BigDecimal("25.00"),
            List.of(item("2.50")));
    JsonNode json = MAPPER.valueToTree(dto);

    for (String field : List.of("stakeAmount", "totalOdds", "potentialReturn")) {
      assertThat(json.get(field).isTextual())
          .as(field + " must be a JSON string per PRD 8.4/8.5")
          .isTrue();
    }
    assertThat(json.get("stakeAmount").textValue()).isEqualTo("10.00");
    assertThat(json.get("totalOdds").textValue()).isEqualTo(WIDE_TOTAL_ODDS);
    assertThat(json.get("potentialReturn").textValue()).isEqualTo("25.00");

    String raw = MAPPER.writeValueAsString(dto);
    assertThat(raw)
        .contains("\"stakeAmount\":\"10.00\"")
        .contains("\"totalOdds\":\"" + WIDE_TOTAL_ODDS + "\"")
        .contains("\"potentialReturn\":\"25.00\"");
  }

  @Test
  void itemSelectedOddsSerializeAsDecimalString() throws Exception {
    TicketDto dto =
        ticket(
            new BigDecimal("10.00"),
            new BigDecimal("2.50"),
            new BigDecimal("25.00"),
            List.of(item("2.50"), item("3.10")));
    JsonNode json = MAPPER.valueToTree(dto);

    JsonNode items = json.get("items");
    assertThat(items).hasSize(2);
    for (JsonNode item : items) {
      assertThat(item.get("selectedOdds").isTextual())
          .as("selectedOdds must be a JSON string per PRD 8.4/8.5")
          .isTrue();
    }
    assertThat(items.get(0).get("selectedOdds").textValue()).isEqualTo("2.50");
    assertThat(items.get(1).get("selectedOdds").textValue()).isEqualTo("3.10");
    assertThat(MAPPER.writeValueAsString(dto)).contains("\"selectedOdds\":\"2.50\"");
  }

  @Test
  void tenOddsProductSerializesExactlyAsNumeric4120String() throws Exception {
    BigDecimal product = BigDecimal.ONE;
    for (String odds : TEN_ODDS) {
      product = product.multiply(new BigDecimal(odds));
    }
    assertThat(product).isEqualTo(new BigDecimal(TEN_ODDS_PRODUCT));
    assertThat(product.scale()).isEqualTo(20);

    TicketDto dto =
        ticket(new BigDecimal("10.00"), product, new BigDecimal("25.00"), List.of(item("2.50")));
    JsonNode json = MAPPER.valueToTree(dto);

    assertThat(json.get("totalOdds").isTextual()).isTrue();
    assertThat(json.get("totalOdds").textValue())
        .as("exact text of the numeric(41,20) product")
        .isEqualTo(TEN_ODDS_PRODUCT);
    assertThat(MAPPER.writeValueAsString(dto))
        .contains("\"totalOdds\":\"" + TEN_ODDS_PRODUCT + "\"");
  }

  @Test
  void financialDecimalsRoundTripAsExactBigDecimals() throws Exception {
    TicketDto dto =
        ticket(
            new BigDecimal("10.00"),
            new BigDecimal(WIDE_TOTAL_ODDS),
            new BigDecimal("25.00"),
            List.of(item("2.50")));
    TicketDto back = MAPPER.readValue(MAPPER.writeValueAsString(dto), TicketDto.class);

    assertThat(back.stakeAmount()).isEqualTo(new BigDecimal("10.00"));
    assertThat(back.stakeAmount().scale()).isEqualTo(2);
    assertThat(back.totalOdds()).isEqualTo(new BigDecimal(WIDE_TOTAL_ODDS));
    assertThat(back.totalOdds().scale()).isEqualTo(20);
    assertThat(back.potentialReturn()).isEqualTo(new BigDecimal("25.00"));
    assertThat(back.items().get(0).selectedOdds()).isEqualTo(new BigDecimal("2.50"));
    assertThat(back.items().get(0).selectedOdds().scale()).isEqualTo(2);
  }

  private static TicketDto ticket(
      BigDecimal stakeAmount,
      BigDecimal totalOdds,
      BigDecimal potentialReturn,
      List<TicketItemSnapshotDto> items) {
    return new TicketDto(
        UUID.randomUUID(),
        "TF-REVIEW0001",
        TicketStatus.PENDING,
        stakeAmount,
        totalOdds,
        potentialReturn,
        Instant.parse("2026-11-01T21:00:00Z"),
        items);
  }

  private static TicketItemSnapshotDto item(String selectedOdds) {
    return new TicketItemSnapshotDto(
        "Competição Teste",
        "Time A",
        "Time B",
        Instant.parse("2026-11-01T21:00:00Z"),
        "Resultado final",
        "Casa",
        "HOME",
        "FULL_TIME",
        "NONE",
        "NONE",
        "Regra de teste",
        "MANUAL",
        "MANUAL",
        "fixture-review",
        "mkt-review",
        "sel-review",
        "NONE",
        new BigDecimal(selectedOdds),
        1L,
        1L,
        1L);
  }
}
