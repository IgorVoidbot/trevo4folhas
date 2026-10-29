package com.trevo4folhas.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trevo4folhas.db.TestDb;

/** HTTP contracts use real PostgreSQL 16; fixtures are local and not provider evidence. */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogHttpTest {

  private static final PostgreSQLContainer<?> POSTGRES = TestDb.startPostgres16();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> TestDb.currentJdbcUrl(POSTGRES));
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("trevo.betting.min-stake-amount", () -> "5.00");
    registry.add("trevo.betting.max-stake-amount", () -> "500.00");
    registry.add("trevo.betting.max-selections", () -> "10");
    registry.add("trevo.betting.max-potential-return", () -> "10000.00");
    registry.add("trevo.betting.ticket-validity-minutes", () -> "30");
    registry.add("trevo.catalog.bank-time-zone", () -> "America/Sao_Paulo");
    registry.add("trevo.catalog.bank-whatsapp-number", () -> "5511999999999");
    registry.add("trevo.catalog.manual-price-max-age-seconds", () -> "3600");
    registry.add("trevo.catalog.manual-schedule-max-age-seconds", () -> "7200");
    registry.add("trevo.catalog.manual-status-max-age-seconds", () -> "1800");
  }

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void clean() {
    for (String table : new String[]{"ticket_items", "tickets", "market_selections", "markets", "matches"}) {
      jdbc.execute("DELETE FROM public." + table);
    }
  }

  @AfterAll
  static void stop() {
    TestDb.stopQuietly(POSTGRES);
  }

  @Test
  void publicConfigContainsOnlyPublicFieldsAndExactDecimalStrings() throws Exception {
    String body = mvc.perform(get("/api/config")).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    JsonNode json = mapper.readTree(body);
    Set<String> fields = new HashSet<>();
    json.fieldNames().forEachRemaining(fields::add);
    assertThat(fields).containsExactlyInAnyOrder("minStakeAmount", "maxStakeAmount",
        "maxSelections", "maxPotentialReturn", "ticketValidityMinutes", "bankTimeZone",
        "bankWhatsappNumber");
    assertThat(json.get("minStakeAmount").isTextual()).isTrue();
    assertThat(json.get("minStakeAmount").asText()).isEqualTo("5.00");
    assertThat(json.get("maxStakeAmount").asText()).isEqualTo("500.00");
    assertThat(json.get("maxPotentialReturn").asText()).isEqualTo("10000.00");
    assertThat(json.get("bankTimeZone").asText()).isEqualTo("America/Sao_Paulo");
    assertThat(json.get("bankWhatsappNumber").asText()).isEqualTo("5511999999999");
  }

  @Test
  void operatorCreatesManualFutureMatchWithServerIdentityAndVersion() throws Exception {
    String body = mapper.writeValueAsString(Map.of("competition", "Copa local",
        "homeTeam", "Clube D'Água", "awayTeam", "Visitante",
        "startsAt", Instant.now().plusSeconds(86400).toString()));
    JsonNode match = mapper.readTree(mvc.perform(post("/api/admin/matches")
        .with(user("operator").roles("ADMIN")).with(csrf())
        .contentType("application/json").content(body)).andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString());
    assertThat(UUID.fromString(match.get("id").asText())).isNotNull();
    assertThat(match.get("matchVersion").asLong()).isEqualTo(1);
    assertThat(match.get("sourceNamespace").asText()).isEqualTo("MANUAL");
    assertThat(match.get("homeTeam").asText()).isEqualTo("Clube D'Água");
    assertThat(match.get("manualReviewedAt").isNull()).isFalse();
  }

  @Test
  void manualMatchRejectsEqualTeamsPastKickoffAndSourceSpoofing() throws Exception {
    for (Map<String, ?> body : java.util.List.of(
        Map.of("competition", "Copa", "homeTeam", "Clube", "awayTeam", " clube ", "startsAt", Instant.now().plusSeconds(86400).toString()),
        Map.of("competition", "Copa", "homeTeam", "Casa", "awayTeam", "Fora", "startsAt", "2020-01-01T00:00:00Z"),
        Map.of("competition", "Copa", "homeTeam", "Casa", "awayTeam", "Fora", "startsAt", Instant.now().plusSeconds(86400).toString(), "sourceNamespace", "EXTERNAL"))) {
      mvc.perform(post("/api/admin/matches").with(user("operator").roles("ADMIN")).with(csrf())
          .contentType("application/json").content(mapper.writeValueAsString(body)))
          .andExpect(status().isBadRequest());
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.matches", Integer.class)).isZero();
  }

  @Test
  void manualMarketAndSelectionPreserveSemanticsCanonicalLineAndExactOdds() throws Exception {
    JsonNode match = createMatch();
    JsonNode market = create("/api/admin/matches/" + match.get("id").asText() + "/markets",
        Map.of("matchVersion", 1, "marketType", "TOTALS", "displayName", "Total de gols",
            "period", "FT", "line", "2.50", "ruleKey", "REGULATION_TIME",
            "ruleText", "Tempo regulamentar, incluindo acréscimos.", "curated", true,
            "specifiers", Map.of("z", "two", "a", "one")));
    assertThat(market.get("line").asText()).isEqualTo("2.5");
    assertThat(market.get("participant").asText()).isEqualTo("NONE");
    assertThat(market.get("marketVersion").asLong()).isEqualTo(1);
    assertThat(market.get("specifiersKey").asText()).isEqualTo("{\"a\":\"one\",\"z\":\"two\"}");
    mvc.perform(post("/api/admin/matches/" + match.get("id").asText() + "/markets")
        .with(user("operator").roles("ADMIN")).with(csrf()).contentType("application/json")
        .content(mapper.writeValueAsString(Map.of("matchVersion", 1, "marketType", "TOTALS",
            "displayName", "Outro rótulo", "period", "FT", "line", "2.500", "ruleKey", "REGULATION_TIME",
            "ruleText", "Tempo regulamentar.", "specifiers", Map.of("a", "one", "z", "two")))))
        .andExpect(status().isConflict());
    JsonNode selection = create("/api/admin/markets/" + market.get("id").asText() + "/selections",
        Map.of("matchVersion", 1, "marketVersion", 1, "selectionCode", "OVER",
            "displayName", "Mais de 2,5", "odds", "1.85", "curated", true));
    assertThat(selection.get("odds").isTextual()).isTrue();
    assertThat(selection.get("odds").asText()).isEqualTo("1.85");
    assertThat(selection.get("selectionVersion").asLong()).isEqualTo(1);
    assertThat(selection.get("matchId").asText()).isEqualTo(match.get("id").asText());
    assertThat(selection.get("sourceNamespace").asText()).isEqualTo("MANUAL");
  }

  @Test
  void manualSelectionRejectsNumericOddsPrecisionAndReservedCodesOutside1x2() throws Exception {
    JsonNode match = createMatch();
    JsonNode market = create("/api/admin/matches/" + match.get("id").asText() + "/markets",
        Map.of("matchVersion", 1, "marketType", "TOTALS", "displayName", "Total",
            "period", "FT", "ruleKey", "REGULATION_TIME", "ruleText", "Tempo regulamentar."));
    String path = "/api/admin/markets/" + market.get("id").asText() + "/selections";
    for (Object odds : java.util.List.of(1.85, "1.001", "0.99", "100.01", "NaN")) {
      mvc.perform(post(path).with(user("operator").roles("ADMIN")).with(csrf())
          .contentType("application/json").content(mapper.writeValueAsString(Map.of(
              "matchVersion", 1, "marketVersion", 1, "selectionCode", "OVER", "displayName", "Mais", "odds", odds))))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(post(path).with(user("operator").roles("ADMIN")).with(csrf())
        .contentType("application/json").content(mapper.writeValueAsString(Map.of(
            "matchVersion", 1, "marketVersion", 1, "selectionCode", "HOME", "displayName", "Casa", "odds", "2.00"))))
        .andExpect(status().isBadRequest());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.market_selections", Integer.class)).isZero();
  }

  private JsonNode createMatch() throws Exception {
    return create("/api/admin/matches", Map.of("competition", "Copa local", "homeTeam", "Casa",
        "awayTeam", "Visitante", "startsAt", Instant.now().plusSeconds(86400).toString()));
  }

  private JsonNode create(String path, Map<String, ?> body) throws Exception {
    return mapper.readTree(mvc.perform(post(path).with(user("operator").roles("ADMIN")).with(csrf())
        .contentType("application/json").content(mapper.writeValueAsString(body)))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
  }
}
