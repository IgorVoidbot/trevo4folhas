package com.trevo4folhas.ticket;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;

import com.fasterxml.jackson.databind.*;
import com.trevo4folhas.catalog.*;
import com.trevo4folhas.config.BettingProperties;
import com.trevo4folhas.db.TestDb;

@SpringBootTest
@AutoConfigureMockMvc
class TicketIssuanceTest {
  static final PostgreSQLContainer<?> POSTGRES = TestDb.startPostgres16();
  static final AtomicReference<Instant> NOW = new AtomicReference<>(Instant.now());

  @TestConfiguration
  static class TimeConfiguration {
    @Bean
    @Primary
    Clock issuanceClock() {
      return new Clock() {
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
          return this;
        }

        public Instant instant() {
          return NOW.get();
        }
      };
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> TestDb.currentJdbcUrl(POSTGRES));
    r.add("spring.datasource.username", POSTGRES::getUsername);
    r.add("spring.datasource.password", POSTGRES::getPassword);
    r.add("trevo.betting.min-stake-amount", () -> "5.00");
    r.add("trevo.betting.max-stake-amount", () -> "500.00");
    r.add("trevo.betting.max-selections", () -> "10");
    r.add("trevo.betting.max-potential-return", () -> "10000.00");
    r.add("trevo.betting.ticket-validity-minutes", () -> "30");
    r.add("trevo.catalog.manual-price-max-age-seconds", () -> "86400");
    r.add("trevo.catalog.manual-schedule-max-age-seconds", () -> "86400");
    r.add("trevo.catalog.manual-status-max-age-seconds", () -> "86400");
    r.add("trevo.catalog.bank-whatsapp-number", () -> "5511999999999");
    r.add("trevo.security.ticket-rate-limit.max-attempts", () -> "1000");
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired CatalogService catalog;
  @Autowired BettingProperties limits;
  @Autowired TicketRateLimiter limiter;
  @MockitoBean TicketCodeGenerator codes;
  MockHttpSession session;

  @BeforeEach
  void setup() {
    NOW.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    session = new MockHttpSession();
    limits.setMinStakeAmount(new BigDecimal("5.00"));
    limits.setMaxStakeAmount(new BigDecimal("500.00"));
    limits.setMaxSelections(10);
    limits.setMaxPotentialReturn(new BigDecimal("10000.00"));
    var generator = new TicketCodeGenerator();
    when(codes.next()).thenAnswer(invocation -> generator.next());
  }

  @AfterEach
  void clean() {
    jdbc.execute("DROP TRIGGER IF EXISTS fail_ticket_snapshot ON public.ticket_items");
    jdbc.execute("DROP FUNCTION IF EXISTS public.fail_ticket_snapshot()");
    for (String table :
        List.of("ticket_items", "tickets", "market_selections", "markets", "matches"))
      jdbc.execute("DELETE FROM public." + table);
  }

  @AfterAll
  static void stop() {
    TestDb.stopQuietly(POSTGRES);
  }

  CatalogDtos.SelectionView offer(String code, String price, Instant start) {
    var match =
        catalog.createMatch(
            new CatalogRequests.CreateMatch(
                "TESTE fictício", "Equipe fictícia A", "Equipe fictícia B", start));
    var market =
        catalog.createMarket(
            match.id(),
            new CatalogRequests.CreateMarket(
                match.matchVersion(),
                "1X2",
                "Resultado da partida",
                "FT",
                null,
                null,
                "REGULATION_TIME",
                "Tempo regulamentar, sem prorrogação.",
                null,
                true));
    return catalog.createSelection(
        market.id(),
        new CatalogRequests.CreateSelection(
            match.matchVersion(),
            market.marketVersion(),
            code,
            "HOME".equals(code) ? "Casa" : "Visitante",
            new BigDecimal(price),
            true));
  }

  Map<String, Object> body(CatalogDtos.SelectionView... selections) {
    return new HashMap<>(
        Map.of(
            "bettorName",
            "Pessoa D'Água",
            "bettorPhone",
            "(11) 99999-9999",
            "stakeAmount",
            "10.00",
            "rulesAccepted",
            true,
            "adultDeclared",
            true,
            "rulesVersion",
            TicketRules.VERSION,
            "items",
            Arrays.stream(selections)
                .map(
                    s ->
                        Map.of(
                            "selectionId",
                            s.id(),
                            "matchVersion",
                            s.matchVersion(),
                            "marketVersion",
                            s.marketVersion(),
                            "selectionVersion",
                            s.selectionVersion()))
                .toList()));
  }

  MvcResult issue(UUID key, MockHttpSession issuer, Map<String, Object> body, int expected)
      throws Exception {
    return mvc.perform(
            post("/api/tickets")
                .session(issuer)
                .with(csrf())
                .header("Idempotency-Key", key)
                .contentType("application/json")
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().is(expected))
        .andReturn();
  }

  JsonNode json(MvcResult result) throws Exception {
    return mapper.readTree(result.getResponse().getContentAsString());
  }

  @Test
  void createsAtomicPendingTicketWithExactFinancialsCompleteSnapshotsAndNoStore() throws Exception {
    var a = offer("HOME", "1.80", NOW.get().plusSeconds(600));
    var b = offer("AWAY", "2.50", NOW.get().plusSeconds(3600));
    var result = issue(UUID.randomUUID(), session, body(a, b), 201);
    var receipt = json(result);
    assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(receipt.path("code").asText()).matches("TF-[A-HJ-NP-Z2-9]{10}");
    assertThat(receipt.path("status").asText()).isEqualTo("PENDING");
    assertThat(receipt.path("totalOdds").asText()).isEqualTo("4.5");
    assertThat(receipt.path("potentialReturn").asText()).isEqualTo("45.00");
    assertThat(receipt.path("bettorPhone").asText()).isEqualTo("5511999999999");
    assertThat(receipt.path("items").size()).isEqualTo(2);
    assertThat(receipt.path("items").get(0).path("selectedOdds").isTextual()).isTrue();
    assertThat(receipt.path("items").get(0).path("ruleSnapshot").asText())
        .contains("Tempo regulamentar");
    assertThat(receipt.path("expiresAt").asText())
        .isEqualTo(catalog.match(a.matchId(), true).startsAt().toString());
    assertThat(receipt.path("summary").asText())
        .contains("Pendente", "Resultado da partida", "Tempo regulamentar", "Código:", "45,00");
    assertThat(receipt.toString())
        .doesNotContain("sessionHash", "requestHash", "ticketSessionNonce", "password");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.tickets", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.ticket_items", Integer.class))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT rules_version FROM public.tickets", String.class))
        .isEqualTo(TicketRules.VERSION);
  }

  @Test
  void normalizedReplayIgnoresItemOrderAndValidityUsesDatabaseCreationTime() throws Exception {
    var a = offer("HOME", "1.80", NOW.get().plusSeconds(7200));
    var b = offer("HOME", "2.50", NOW.get().plusSeconds(7200));
    var key = UUID.randomUUID();
    var receipt = json(issue(key, session, body(a, b), 201));
    assertThat(Instant.parse(receipt.path("expiresAt").asText()))
        .isEqualTo(Instant.parse(receipt.path("createdAt").asText()).plusSeconds(1800));
    var normalized = body(b, a);
    normalized.put("bettorName", "  Pessoa D'Água  ");
    normalized.put("bettorPhone", "5511999999999");
    normalized.put("stakeAmount", "10");
    assertThat(json(issue(key, session, normalized, 200)).path("id")).isEqualTo(receipt.path("id"));
  }

  @Test
  void rejectsStartedUncuratedSuspendedAndMissingPriceEvidenceWithoutCreatingTickets()
      throws Exception {
    var a = offer("HOME", "1.80", NOW.get().plusSeconds(3600));
    jdbc.update("UPDATE public.markets SET curated=false");
    issue(UUID.randomUUID(), session, body(a), 409);
    jdbc.update("UPDATE public.markets SET curated=true,provider_status='SUSPENDED'");
    issue(UUID.randomUUID(), session, body(a), 409);
    jdbc.update("UPDATE public.markets SET provider_status='OPEN'");
    jdbc.update("UPDATE public.market_selections SET manual_reviewed_at=NULL");
    issue(UUID.randomUUID(), session, body(a), 409);
    jdbc.update(
        "UPDATE public.market_selections SET manual_reviewed_at=?", Timestamp.from(NOW.get()));
    NOW.set(NOW.get().plusSeconds(3600));
    issue(UUID.randomUUID(), session, body(a), 409);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.tickets", Integer.class)).isZero();
  }

  @Test
  void oversizedSummaryRetainsFullRulesAndOffersExplicitShortMessage() throws Exception {
    var a = offer("HOME", "1.80", NOW.get().plusSeconds(7200));
    jdbc.update(
        "UPDATE public.markets SET rule_text=?",
        "Regra fictícia com acentos e quebra de linha.\n".repeat(15));
    var receipt = json(issue(UUID.randomUUID(), session, body(a), 201));
    assertThat(receipt.path("summary").asText()).contains("acentos e quebra de linha.\n");
    // Force the explicit long-message path with two complete snapshots.
    var b = offer("HOME", "2.50", NOW.get().plusSeconds(7200));
    jdbc.update(
        "UPDATE public.markets SET rule_text=?",
        "Regra fictícia com acentos e quebra de linha.\n".repeat(15));
    receipt = json(issue(UUID.randomUUID(), session, body(a, b), 201));
    assertThat(
            receipt
                .path("summary")
                .asText()
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
                .length)
        .isGreaterThan(1500);
    assertThat(receipt.path("whatsappMessage").asText())
        .contains(receipt.path("code").asText(), "Mensagem curta", "resumo completo");
    assertThat(receipt.path("whatsappMessage").asText()).doesNotContain("5511999999999");
  }

  @Test
  void replayIsResolvedBeforeChangedCatalogueAndLimitsAndRejectsOtherSessionOrContent()
      throws Exception {
    var a = offer("HOME", "1.80", NOW.get().plusSeconds(7200));
    var key = UUID.randomUUID();
    var input = body(a);
    var original = json(issue(key, session, input, 201));
    jdbc.update("UPDATE public.matches SET status='CANCELLED',version=version+1");
    jdbc.update("UPDATE public.market_selections SET odds=2.00,version=version+1,curated=false");
    limits.setMaxSelections(1);
    limits.setMaxStakeAmount(new BigDecimal("5.00"));
    var replay = json(issue(key, session, input, 200));
    assertThat(replay.path("id")).isEqualTo(original.path("id"));
    assertThat(replay.path("potentialReturn").asText()).isEqualTo("18.00");
    var stolen = issue(key, new MockHttpSession(), input, 409);
    assertThat(stolen.getResponse().getContentAsString())
        .doesNotContain("Pessoa", "5511999999999", original.path("code").asText());
    input.put("stakeAmount", "20.00");
    issue(key, session, input, 409);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.tickets", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void concurrentIdenticalIntentCreatesExactlyOneTicket() throws Exception {
    var a = offer("HOME", "1.80", NOW.get().plusSeconds(3600));
    var input = body(a);
    var key = UUID.randomUUID();
    var executor = Executors.newFixedThreadPool(2);
    try {
      Callable<MvcResult> call =
          () ->
              mvc.perform(
                      post("/api/tickets")
                          .session(session)
                          .with(csrf())
                          .header("Idempotency-Key", key)
                          .contentType("application/json")
                          .content(mapper.writeValueAsString(input)))
                  .andReturn();
      var results = executor.invokeAll(List.of(call, call));
      assertThat(
              List.of(
                  results.get(0).get().getResponse().getStatus(),
                  results.get(1).get().getResponse().getStatus()))
          .containsExactlyInAnyOrder(201, 200);
      assertThat(json(results.get(0).get()).path("id"))
          .isEqualTo(json(results.get(1).get()).path("id"));
      assertThat(jdbc.queryForObject("SELECT count(*) FROM public.tickets", Integer.class))
          .isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void codeCollisionsRetryWithRollbackAndRemainBounded() throws Exception {
    var a = offer("HOME", "1.80", NOW.get().plusSeconds(3600));
    when(codes.next()).thenReturn("TF-AAAAAAAAAA");
    issue(UUID.randomUUID(), session, body(a), 201);
    issue(UUID.randomUUID(), session, body(a), 503);
    verify(codes, times(4)).next();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.tickets", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.ticket_items", Integer.class))
        .isEqualTo(1);
    when(codes.next()).thenReturn("TF-BBBBBBBBBB");
    issue(UUID.randomUUID(), session, body(a), 201);
  }

  @Test
  void aSnapshotFailureRollsBackTicketAndItemsTogether() throws Exception {
    var a = offer("HOME", "1.80", NOW.get().plusSeconds(3600));
    jdbc.execute(
        "CREATE FUNCTION public.fail_ticket_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Synthetic snapshot rejection'; END $$");
    jdbc.execute(
        "CREATE TRIGGER fail_ticket_snapshot AFTER INSERT ON public.ticket_items FOR EACH ROW EXECUTE FUNCTION public.fail_ticket_snapshot()");
    issue(UUID.randomUUID(), session, body(a), 503);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.tickets", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.ticket_items", Integer.class))
        .isZero();
  }

  @Test
  void rejectsDuplicateMatchVersionsStalenessUnknownFieldsAndOldRules() throws Exception {
    var a = offer("HOME", "1.80", NOW.get().plusSeconds(3600));
    var second =
        catalog.createSelection(
            a.marketId(),
            new CatalogRequests.CreateSelection(
                a.matchVersion(),
                a.marketVersion(),
                "AWAY",
                "Visitante",
                new BigDecimal("2.50"),
                true));
    issue(UUID.randomUUID(), session, body(a, second), 400);
    for (String field : List.of("odds", "potentialReturn", "status", "operator")) {
      var input = body(a);
      input.put(field, "spoof");
      issue(UUID.randomUUID(), session, input, 400);
    }
    var old = body(a);
    old.put("rulesVersion", "OLD");
    issue(UUID.randomUUID(), session, old, 409);
    var bad = body(a);
    bad.put("stakeAmount", 10.0);
    issue(UUID.randomUUID(), session, bad, 400);
    bad = body(a);
    bad.put("stakeAmount", "10.001");
    issue(UUID.randomUUID(), session, bad, 400);
    bad = body(a);
    bad.put("bettorName", "x".repeat(101));
    issue(UUID.randomUUID(), session, bad, 400);
    bad = body(a);
    bad.put("adultDeclared", false);
    issue(UUID.randomUUID(), session, bad, 400);
    jdbc.update("UPDATE public.market_selections SET version=version+1");
    issue(UUID.randomUUID(), session, body(a), 409);
    jdbc.update(
        "UPDATE public.market_selections SET version=1,manual_reviewed_at=now()-interval '2 days'");
    issue(UUID.randomUUID(), session, body(a), 409);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.tickets", Integer.class)).isZero();
  }

  @Test
  void csrfIsRequiredAndNoPublicTicketLookupExists() throws Exception {
    var a = offer("HOME", "1.80", NOW.get().plusSeconds(3600));
    mvc.perform(
            post("/api/tickets")
                .session(session)
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json")
                .content(mapper.writeValueAsString(body(a))))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/tickets").session(session).param("code", "TF-ANY"))
        .andExpect(status().is4xxClientError());
    mvc.perform(get("/api/session").session(session))
        .andExpect(status().isOk())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string("Cache-Control", "no-store"));
  }

  @Test
  void clickIsSessionBoundIdempotentAndNeverConfirmsPayment() throws Exception {
    var a = offer("HOME", "1.80", NOW.get().plusSeconds(3600));
    var key = UUID.randomUUID();
    issue(key, session, body(a), 201);
    for (int i = 0; i < 2; i++)
      mvc.perform(
              post("/api/tickets/whatsapp-click")
                  .session(session)
                  .with(csrf())
                  .contentType("application/json")
                  .content("{\"requestKey\":\"" + key + "\"}"))
          .andExpect(status().isNoContent());
    var timestamp =
        jdbc.queryForObject("SELECT whatsapp_clicked_at FROM public.tickets", Timestamp.class);
    assertThat(timestamp).isNotNull();
    assertThat(jdbc.queryForObject("SELECT status FROM public.tickets", String.class))
        .isEqualTo("PENDING");
    assertThat(jdbc.queryForObject("SELECT version FROM public.tickets", Long.class)).isEqualTo(1);
    mvc.perform(
            post("/api/tickets/whatsapp-click")
                .with(csrf())
                .contentType("application/json")
                .content("{\"requestKey\":\"" + key + "\"}"))
        .andExpect(status().isConflict());
    NOW.set(NOW.get().plusSeconds(7200));
    assertThat(json(issue(key, session, body(a), 200)).path("status").asText())
        .isEqualTo("EXPIRED");
    assertThat(
            jdbc.queryForObject("SELECT whatsapp_clicked_at FROM public.tickets", Timestamp.class))
        .isEqualTo(timestamp);
  }

  @Test
  void limitReturns429AndRetryAfterWithoutTrustingForwardedAddress() throws Exception {
    var a = offer("HOME", "1.80", NOW.get().plusSeconds(3600));
    for (int i = 0; i < 1000; i++) limiter.check("198.51.100.50", "test-session");
    mvc.perform(
            post("/api/tickets")
                .with(
                    request -> {
                      request.setRemoteAddr("198.51.100.50");
                      return request;
                    })
                .header("X-Forwarded-For", "203.0.113.1")
                .session(session)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json")
                .content(mapper.writeValueAsString(body(a))))
        .andExpect(status().isTooManyRequests())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .exists("Retry-After"));
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"suspension", "price", "kickoff"})
  void changesDuringIssuanceWaitAreObservedUnderLocks(String change) throws Exception {
    var a = offer("HOME", "1.80", NOW.get().plusSeconds(3600));
    try (Connection connection =
        DriverManager.getConnection(
            TestDb.currentJdbcUrl(POSTGRES), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement("SELECT id FROM public.matches WHERE id=? FOR UPDATE")) {
        statement.setObject(1, a.matchId());
        statement.executeQuery();
      }
      var executor = Executors.newSingleThreadExecutor();
      try {
        Future<MvcResult> request =
            executor.submit(() -> issue(UUID.randomUUID(), session, body(a), 409));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < deadline
            && jdbc.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE 'SELECT id FROM public.matches%'",
                    Integer.class)
                == 0) Thread.sleep(10);
        assertThat(
                jdbc.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE 'SELECT id FROM public.matches%'",
                    Integer.class))
            .isGreaterThan(0);
        if (change.equals("kickoff")) NOW.set(NOW.get().plusSeconds(3600));
        else
          try (var statement =
              connection.prepareStatement(
                  change.equals("price")
                      ? "UPDATE public.market_selections SET odds=2.00,version=version+1 WHERE id=?"
                      : "UPDATE public.matches SET status='SUSPENDED',version=version+1 WHERE id=?")) {
            statement.setObject(1, change.equals("price") ? a.id() : a.matchId());
            statement.executeUpdate();
          }
        connection.commit();
        request.get(5, TimeUnit.SECONDS);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM public.tickets", Integer.class))
            .isZero();
      } finally {
        executor.shutdownNow();
      }
    }
  }
}
