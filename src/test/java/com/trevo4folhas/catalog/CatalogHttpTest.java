package com.trevo4folhas.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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
    for (String table :
        new String[] {"ticket_items", "tickets", "market_selections", "markets", "matches"}) {
      jdbc.execute("DELETE FROM public." + table);
    }
  }

  @AfterAll
  static void stop() {
    TestDb.stopQuietly(POSTGRES);
  }

  @Test
  void publicConfigContainsOnlyPublicFieldsAndExactDecimalStrings() throws Exception {
    String body =
        mvc.perform(get("/api/config"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode json = mapper.readTree(body);
    Set<String> fields = new HashSet<>();
    json.fieldNames().forEachRemaining(fields::add);
    assertThat(fields)
        .containsExactlyInAnyOrder(
            "minStakeAmount",
            "maxStakeAmount",
            "maxSelections",
            "maxPotentialReturn",
            "ticketValidityMinutes",
            "bankTimeZone",
            "bankWhatsappNumber",
            "rulesVersion");
    assertThat(json.get("minStakeAmount").isTextual()).isTrue();
    assertThat(json.get("minStakeAmount").asText()).isEqualTo("5.00");
    assertThat(json.get("maxStakeAmount").asText()).isEqualTo("500.00");
    assertThat(json.get("maxPotentialReturn").asText()).isEqualTo("10000.00");
    assertThat(json.get("bankTimeZone").asText()).isEqualTo("America/Sao_Paulo");
    assertThat(json.get("bankWhatsappNumber").asText()).isEqualTo("5511999999999");
  }

  @Test
  void operatorCreatesManualFutureMatchWithServerIdentityAndVersion() throws Exception {
    String body =
        mapper.writeValueAsString(
            Map.of(
                "competition",
                "Copa local",
                "homeTeam",
                "Clube D'Água",
                "awayTeam",
                "Visitante",
                "startsAt",
                Instant.now().plusSeconds(86400).toString()));
    JsonNode match =
        mapper.readTree(
            mvc.perform(
                    post("/api/admin/matches")
                        .with(user("operator").roles("ADMIN"))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(UUID.fromString(match.get("id").asText())).isNotNull();
    assertThat(match.get("matchVersion").asLong()).isEqualTo(1);
    assertThat(match.get("sourceNamespace").asText()).isEqualTo("MANUAL");
    assertThat(match.get("homeTeam").asText()).isEqualTo("Clube D'Água");
    assertThat(match.get("manualReviewedAt").isNull()).isFalse();
  }

  @Test
  void manualMatchRejectsEqualTeamsPastKickoffAndSourceSpoofing() throws Exception {
    for (Map<String, ?> body :
        java.util.List.of(
            Map.of(
                "competition",
                "Copa",
                "homeTeam",
                "Clube",
                "awayTeam",
                " clube ",
                "startsAt",
                Instant.now().plusSeconds(86400).toString()),
            Map.of(
                "competition",
                "Copa",
                "homeTeam",
                "Casa",
                "awayTeam",
                "Fora",
                "startsAt",
                "2020-01-01T00:00:00Z"),
            Map.of(
                "competition",
                "Copa",
                "homeTeam",
                "Casa",
                "awayTeam",
                "Fora",
                "startsAt",
                Instant.now().plusSeconds(86400).toString(),
                "sourceNamespace",
                "EXTERNAL"))) {
      mvc.perform(
              post("/api/admin/matches")
                  .with(user("operator").roles("ADMIN"))
                  .with(csrf())
                  .contentType("application/json")
                  .content(mapper.writeValueAsString(body)))
          .andExpect(status().isBadRequest());
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.matches", Integer.class)).isZero();
  }

  @Test
  void manualMarketAndSelectionPreserveSemanticsCanonicalLineAndExactOdds() throws Exception {
    JsonNode match = createMatch();
    JsonNode market =
        create(
            "/api/admin/matches/" + match.get("id").asText() + "/markets",
            Map.of(
                "matchVersion",
                1,
                "marketType",
                "TOTALS",
                "displayName",
                "Total de gols",
                "period",
                "FT",
                "line",
                "2.50",
                "ruleKey",
                "REGULATION_TIME",
                "ruleText",
                "Tempo regulamentar, incluindo acréscimos.",
                "curated",
                true,
                "specifiers",
                Map.of("z", "two", "a", "one")));
    assertThat(market.get("line").asText()).isEqualTo("2.5");
    assertThat(market.get("participant").asText()).isEqualTo("NONE");
    assertThat(market.get("marketVersion").asLong()).isEqualTo(1);
    assertThat(market.get("specifiersKey").asText()).isEqualTo("{\"a\":\"one\",\"z\":\"two\"}");
    mvc.perform(
            post("/api/admin/matches/" + match.get("id").asText() + "/markets")
                .with(user("operator").roles("ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    mapper.writeValueAsString(
                        Map.of(
                            "matchVersion",
                            1,
                            "marketType",
                            "TOTALS",
                            "displayName",
                            "Outro rótulo",
                            "period",
                            "FT",
                            "line",
                            "2.500",
                            "ruleKey",
                            "REGULATION_TIME",
                            "ruleText",
                            "Tempo regulamentar.",
                            "specifiers",
                            Map.of("a", "one", "z", "two")))))
        .andExpect(status().isConflict());
    JsonNode selection =
        create(
            "/api/admin/markets/" + market.get("id").asText() + "/selections",
            Map.of(
                "matchVersion",
                1,
                "marketVersion",
                1,
                "selectionCode",
                "OVER",
                "displayName",
                "Mais de 2,5",
                "odds",
                "1.85",
                "curated",
                true));
    assertThat(selection.get("odds").isTextual()).isTrue();
    assertThat(selection.get("odds").asText()).isEqualTo("1.85");
    assertThat(selection.get("selectionVersion").asLong()).isEqualTo(1);
    assertThat(selection.get("matchId").asText()).isEqualTo(match.get("id").asText());
    assertThat(selection.get("sourceNamespace").asText()).isEqualTo("MANUAL");
  }

  @Test
  void manualSelectionRejectsNumericOddsPrecisionAndReservedCodesOutside1x2() throws Exception {
    JsonNode match = createMatch();
    JsonNode market =
        create(
            "/api/admin/matches/" + match.get("id").asText() + "/markets",
            Map.of(
                "matchVersion",
                1,
                "marketType",
                "TOTALS",
                "displayName",
                "Total",
                "period",
                "FT",
                "ruleKey",
                "REGULATION_TIME",
                "ruleText",
                "Tempo regulamentar."));
    String path = "/api/admin/markets/" + market.get("id").asText() + "/selections";
    for (Object odds : java.util.List.of(1.85, "1.001", "0.99", "100.01", "NaN")) {
      mvc.perform(
              post(path)
                  .with(user("operator").roles("ADMIN"))
                  .with(csrf())
                  .contentType("application/json")
                  .content(
                      mapper.writeValueAsString(
                          Map.of(
                              "matchVersion",
                              1,
                              "marketVersion",
                              1,
                              "selectionCode",
                              "OVER",
                              "displayName",
                              "Mais",
                              "odds",
                              odds))))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(
            post(path)
                .with(user("operator").roles("ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    mapper.writeValueAsString(
                        Map.of(
                            "matchVersion",
                            1,
                            "marketVersion",
                            1,
                            "selectionCode",
                            "HOME",
                            "displayName",
                            "Casa",
                            "odds",
                            "2.00"))))
        .andExpect(status().isBadRequest());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.market_selections", Integer.class))
        .isZero();
  }

  @Test
  void publicCatalogRequiresCompleteCuratedFreshHierarchyAndBoundsPagination() throws Exception {
    JsonNode[] offer = offer();
    assertThat(read("/api/matches").get("totalElements").asInt()).isEqualTo(1);
    assertThat(
            read("/api/matches/" + offer[0].get("id").asText() + "/markets")
                .get("totalElements")
                .asInt())
        .isEqualTo(1);
    assertThat(
            read("/api/markets/" + offer[1].get("id").asText() + "/selections")
                .get("content")
                .get(0)
                .get("odds")
                .asText())
        .isEqualTo("1.85");
    for (String table : java.util.List.of("matches", "markets", "market_selections")) {
      jdbc.update("UPDATE public." + table + " SET manual_reviewed_at=now()-interval '1 day'");
      assertThat(read("/api/matches").get("totalElements").asInt()).isZero();
      jdbc.update("UPDATE public." + table + " SET manual_reviewed_at=now()");
    }
    jdbc.update("UPDATE public.market_selections SET curated=false");
    assertThat(read("/api/matches").get("totalElements").asInt()).isZero();
    mvc.perform(get("/api/matches").param("size", "101")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/matches").param("page", "-1")).andExpect(status().isBadRequest());
    mvc.perform(get("/api/matches").param("date", "invalid")).andExpect(status().isBadRequest());
  }

  @Test
  void dayFilterUsesSaoPauloMidnightAndPaginationReportsFullCount() throws Exception {
    String date =
        java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(3).toString();
    for (String time : java.util.List.of("T02:59:00Z", "T03:00:00Z", "T23:59:00Z")) {
      create(
          "/api/admin/matches",
          Map.of(
              "competition",
              "Copa",
              "homeTeam",
              "Casa",
              "awayTeam",
              "Fora",
              "startsAt",
              date + time));
    }
    JsonNode page = adminRead("/api/admin/matches?date=" + date + "&size=1");
    assertThat(page.get("totalElements").asInt()).isEqualTo(2);
    assertThat(page.get("totalPages").asInt()).isEqualTo(2);
    assertThat(page.get("content").size()).isEqualTo(1);
    assertThat(adminRead("/api/admin/matches?competition=Outra").get("totalElements").asInt())
        .isZero();
  }

  @Test
  void editChecksVersionAndCancellationIsTerminal() throws Exception {
    JsonNode match = createMatch();
    String path = "/api/admin/matches/" + match.get("id").asText();
    var body = updateBody(match, "SUSPENDED");
    JsonNode suspended = patch(path, body, 200);
    assertThat(suspended.get("matchVersion").asInt()).isEqualTo(2);
    patch(path, body, 409);
    var cancel = updateBody(suspended, "CANCELLED");
    JsonNode cancelled = patch(path, cancel, 200);
    patch(path, updateBody(cancelled, "OPEN"), 409);
  }

  @Test
  void referencedMatchAndTermsAreImmutableButNewPricePreservesSnapshot() throws Exception {
    JsonNode[] items = offer();
    reference(items);
    var body = updateBody(items[0], "OPEN");
    body.put("homeTeam", "Outro clube");
    patch("/api/admin/matches/" + items[0].get("id").asText(), body, 409);
    var marketBody =
        Map.of(
            "matchVersion",
            1,
            "marketVersion",
            1,
            "displayName",
            "Total",
            "ruleText",
            "Outra regra",
            "curated",
            true,
            "adminSuspended",
            false);
    patch("/api/admin/markets/" + items[1].get("id").asText(), marketBody, 409);
    var selectionBody =
        Map.of(
            "matchVersion",
            1,
            "marketVersion",
            1,
            "selectionVersion",
            1,
            "displayName",
            "Mais de 2,5",
            "odds",
            "2.00",
            "curated",
            true,
            "adminSuspended",
            false);
    patch("/api/admin/selections/" + items[2].get("id").asText(), selectionBody, 200);
    assertThat(
            jdbc.queryForObject(
                "SELECT selected_odds FROM public.ticket_items", java.math.BigDecimal.class))
        .isEqualByComparingTo("1.85");
    assertThat(jdbc.queryForObject("SELECT home_team FROM public.matches", String.class))
        .isEqualTo(items[0].get("homeTeam").asText());
  }

  @Test
  void suspensionsExpiredKickoffAndCancelledMatchBlockPublicOffersAndCreation() throws Exception {
    JsonNode[] items = offer();
    String marketId = items[1].get("id").asText();
    jdbc.update("UPDATE public.matches SET admin_suspended=true");
    assertThat(read("/api/matches").get("totalElements").asInt()).isZero();
    mvc.perform(
            post("/api/admin/markets/" + marketId + "/selections")
                .with(user("operator").roles("ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content(
                    mapper.writeValueAsString(
                        Map.of(
                            "matchVersion",
                            1,
                            "marketVersion",
                            1,
                            "selectionCode",
                            "UNDER",
                            "displayName",
                            "Menos",
                            "odds",
                            "2.00"))))
        .andExpect(status().isConflict());
    jdbc.update(
        "UPDATE public.matches SET admin_suspended=false,match_date=now()-interval '1 second'");
    assertThat(read("/api/matches").get("totalElements").asInt()).isZero();
    var expiredBody = updateBody(items[0], "OPEN");
    expiredBody.put("startsAt", Instant.now().minusSeconds(3600).toString());
    patch("/api/admin/matches/" + items[0].get("id").asText(), expiredBody, 400);
  }

  @Test
  void administrativeReadWriteAndSyncRequireRoleAndCsrf() throws Exception {
    for (String path : java.util.List.of("/api/admin/matches", "/api/admin/catalog-sync/status")) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
      mvc.perform(get(path).with(user("visitor").roles("OTHER"))).andExpect(status().isForbidden());
    }
    mvc.perform(
            post("/api/admin/catalog-sync")
                .with(user("operator").roles("ADMIN"))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            post("/api/admin/catalog-sync")
                .with(user("operator").roles("ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content("{\"host\":\"http://127.0.0.1\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/admin/catalog-sync")
                .with(user("operator").roles("ADMIN"))
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isConflict());
    String statusBody =
        mvc.perform(get("/api/admin/catalog-sync/status").with(user("operator").roles("ADMIN")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(statusBody).contains("DISABLED").doesNotContain("apiKey");
    mvc.perform(get("/admin/matches").with(user("operator").roles("ADMIN")))
        .andExpect(status().isOk());
  }

  @Test
  void concurrentEditsHaveExactlyOneWinner() throws Exception {
    JsonNode match = createMatch();
    var body = updateBody(match, "SUSPENDED");
    String payload = mapper.writeValueAsString(body);
    var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      java.util.concurrent.Callable<Integer> edit =
          () ->
              mvc.perform(
                      org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                              "/api/admin/matches/" + match.get("id").asText())
                          .with(user("operator").roles("ADMIN"))
                          .with(csrf())
                          .contentType("application/json")
                          .content(payload))
                  .andReturn()
                  .getResponse()
                  .getStatus();
      var results = executor.invokeAll(java.util.List.of(edit, edit));
      assertThat(java.util.List.of(results.get(0).get(), results.get(1).get()))
          .containsExactlyInAnyOrder(200, 409);
    } finally {
      executor.shutdownNow();
    }
  }

  private JsonNode[] offer() throws Exception {
    JsonNode match = createMatch();
    JsonNode market =
        create(
            "/api/admin/matches/" + match.get("id").asText() + "/markets",
            Map.of(
                "matchVersion",
                1,
                "marketType",
                "TOTALS",
                "displayName",
                "Total",
                "period",
                "FT",
                "line",
                "2.5",
                "ruleKey",
                "REGULATION_TIME",
                "ruleText",
                "Tempo regulamentar.",
                "curated",
                true));
    JsonNode selection =
        create(
            "/api/admin/markets/" + market.get("id").asText() + "/selections",
            Map.of(
                "matchVersion",
                1,
                "marketVersion",
                1,
                "selectionCode",
                "OVER",
                "displayName",
                "Mais de 2,5",
                "odds",
                "1.85",
                "curated",
                true));
    return new JsonNode[] {match, market, selection};
  }

  private JsonNode read(String path) throws Exception {
    return mapper.readTree(
        mvc.perform(get(path))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode adminRead(String path) throws Exception {
    return mapper.readTree(
        mvc.perform(get(path).with(user("operator").roles("ADMIN")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private JsonNode patch(String path, Map<String, ?> body, int expected) throws Exception {
    return mapper.readTree(
        mvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(path)
                    .with(user("operator").roles("ADMIN"))
                    .with(csrf())
                    .contentType("application/json")
                    .content(mapper.writeValueAsString(body)))
            .andExpect(status().is(expected))
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  private java.util.HashMap<String, Object> updateBody(JsonNode match, String status) {
    var result = new java.util.HashMap<String, Object>();
    for (String key : java.util.List.of("competition", "homeTeam", "awayTeam", "startsAt"))
      result.put(key, match.get(key).asText());
    result.put("matchVersion", match.get("matchVersion").asLong());
    result.put("status", status);
    result.put("adminSuspended", false);
    return result;
  }

  private void reference(JsonNode[] items) {
    UUID ticket =
        jdbc.queryForObject(
            "INSERT INTO public.tickets(ticket_code,request_key,request_hash,session_hash,bettor_name,bettor_phone,stake_amount,total_odds,potential_return,status,expires_at) VALUES('TF-TEST',gen_random_uuid(),repeat('a',64),repeat('b',64),'Pessoa fictícia','5511999999999',10,1.85,18.50,'PENDING',now()+interval '30 minutes') RETURNING id",
            UUID.class);
    jdbc.update(
        """
        INSERT INTO public.ticket_items(ticket_id,match_id,market_selection_id,selected_odds,match_version_snapshot,market_version_snapshot,selection_version_snapshot,
          market_snapshot,selection_snapshot,selection_code_snapshot,period_snapshot,line_snapshot,participant_snapshot,rule_snapshot,provider_snapshot,bookmaker_snapshot,
          external_fixture_id_snapshot,external_market_id_snapshot,external_selection_key_snapshot,specifiers_snapshot,competition_snapshot,home_team_snapshot,away_team_snapshot,match_date_snapshot)
        VALUES(?,?,?,1.85,1,1,1,'Total','Mais de 2,5','OVER','FT','2.5','NONE','Tempo regulamentar.','MANUAL','NONE','1','TOTALS','OVER','NONE','Copa','Casa','Fora',now()+interval '1 day')
        """,
        ticket,
        UUID.fromString(items[0].get("id").asText()),
        UUID.fromString(items[2].get("id").asText()));
  }

  private JsonNode createMatch() throws Exception {
    return create(
        "/api/admin/matches",
        Map.of(
            "competition",
            "Copa local",
            "homeTeam",
            "Casa",
            "awayTeam",
            "Visitante",
            "startsAt",
            Instant.now().plusSeconds(86400).toString()));
  }

  private JsonNode create(String path, Map<String, ?> body) throws Exception {
    return mapper.readTree(
        mvc.perform(
                post(path)
                    .with(user("operator").roles("ADMIN"))
                    .with(csrf())
                    .contentType("application/json")
                    .content(mapper.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }
}
