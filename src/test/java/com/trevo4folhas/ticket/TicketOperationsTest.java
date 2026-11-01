package com.trevo4folhas.ticket;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;

import com.fasterxml.jackson.databind.*;
import com.trevo4folhas.admin.*;
import com.trevo4folhas.catalog.*;
import com.trevo4folhas.db.TestDb;
import com.trevo4folhas.sports.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@SpringBootTest
@AutoConfigureMockMvc
class TicketOperationsTest {
  static final PostgreSQLContainer<?> POSTGRES = TestDb.startPostgres16();
  static final AtomicReference<Instant> NOW = new AtomicReference<>(Instant.now());

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    Clock operationsClock() {
      return new Clock() {
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId z) {
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
    for (String kind : List.of("price", "schedule", "status")) {
      r.add("trevo.catalog.manual-" + kind + "-max-age-seconds", () -> "86400");
      r.add(
          "trevo.sports.max-" + kind + "-age-seconds", () -> kind.equals("price") ? "60" : "86400");
    }
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired CatalogService catalog;
  @Autowired CatalogRepository catalogRepository;
  @Autowired TicketService issuer;
  @Autowired TicketOperationsService service;
  @Autowired TicketExpirationService expiration;
  @Autowired AdminRepository admins;
  @Autowired AdminUserDetailsService users;
  @Autowired ImportService importer;
  UUID actor, secondActor;
  UserDetails operator, other;

  @BeforeEach
  void setup() {
    NOW.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    actor =
        admins.saveAndFlush(new Admin("first", "Operador fictício", "unused-test-hash")).getId();
    secondActor =
        admins.saveAndFlush(new Admin("second", "Segundo fictício", "unused-test-hash")).getId();
    operator = users.loadUserByUsername("first");
    other = users.loadUserByUsername("second");
  }

  @AfterEach
  void clean() {
    jdbc.execute("DROP TRIGGER IF EXISTS reject_ticket_decision ON public.tickets");
    jdbc.execute("DROP FUNCTION IF EXISTS public.reject_ticket_decision()");
    for (String t :
        List.of("ticket_items", "tickets", "market_selections", "markets", "matches", "admins"))
      jdbc.execute("DELETE FROM public." + t);
  }

  @AfterAll
  static void stop() {
    TestDb.stopQuietly(POSTGRES);
  }

  CatalogDtos.SelectionView offer() {
    var m =
        catalog.createMatch(
            new CatalogRequests.CreateMatch(
                "TESTE fictício",
                "Equipe fictícia A",
                "Equipe fictícia B",
                NOW.get().plusSeconds(7200)));
    var k =
        catalog.createMarket(
            m.id(),
            new CatalogRequests.CreateMarket(
                m.matchVersion(),
                "1X2",
                "Resultado da partida",
                "FT",
                null,
                null,
                "REGULATION_TIME",
                "Tempo regulamentar fictício.",
                null,
                true));
    return catalog.createSelection(
        k.id(),
        new CatalogRequests.CreateSelection(
            m.matchVersion(),
            k.marketVersion(),
            "HOME",
            "Vitória da casa",
            new BigDecimal("1.80"),
            true));
  }

  TicketReceipt issue(CatalogDtos.SelectionView s) {
    return issuer
        .create(
            UUID.randomUUID(),
            TicketIntent.hash("anonymous"),
            new TicketRequests.CreateTicket(
                "Pessoa D'Água <teste>",
                "11999999999",
                new BigDecimal("10.00"),
                true,
                true,
                TicketRules.VERSION,
                List.of(
                    new TicketRequests.Item(
                        s.id(), s.matchVersion(), s.marketVersion(), s.selectionVersion()))))
        .receipt();
  }

  TicketOperationsRequests.Confirm confirm(long version) {
    return new TicketOperationsRequests.Confirm(version, true, "Conferência fictícia");
  }

  TicketOperationsRequests.Cancel cancel(long version) {
    return new TicketOperationsRequests.Cancel(version, "Cancelamento fictício", null);
  }

  MvcResult command(UUID id, String action, UserDetails user, Object body, int expected)
      throws Exception {
    return mvc.perform(
            post("/api/admin/tickets/" + id + "/" + action)
                .with(user(user))
                .with(csrf())
                .contentType("application/json")
                .content(mapper.writeValueAsString(body)))
        .andExpect(status().is(expected))
        .andReturn();
  }

  JsonNode json(MvcResult result) throws Exception {
    return mapper.readTree(result.getResponse().getContentAsString());
  }

  @Test
  void confirmsWithOperatorFromSessionAndPreservesSnapshotsAfterPriceChanges() throws Exception {
    var s = offer();
    var t = issue(s);
    var original = service.detail(t.id(), actor);
    jdbc.update("UPDATE public.market_selections SET odds=2.50,version=version+1");
    var response = command(t.id(), "confirm", operator, confirm(1), 200);
    var result = json(response).path("current");
    assertThat(result.path("ticket").path("status").asText()).isEqualTo("CONFIRMED");
    assertThat(result.path("handledBy").asText()).isEqualTo(actor.toString());
    assertThat(result.path("handledByName").asText()).isEqualTo("Operador fictício");
    assertThat(result.path("ticketVersion").asLong()).isEqualTo(2);
    assertThat(result.path("ticket").path("potentialReturn").asText()).isEqualTo("18.00");
    assertThat(service.detail(t.id(), actor).ticket().items()).isEqualTo(original.ticket().items());
    assertThat(response.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(result.toString()).doesNotContain("requestHash", "sessionHash", "unused-test-hash");
    command(t.id(), "confirm", other, confirm(1), 409);
    command(t.id(), "cancel", other, cancel(2), 409);
    assertThat(service.detail(t.id(), actor).handledBy()).isEqualTo(actor);
  }

  @Test
  void requiresPixCsrfAuthenticatedActiveOperatorAndStrictCommandFields() throws Exception {
    var t = issue(offer());
    mvc.perform(get("/api/admin/tickets")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/admin/tickets/" + t.id())).andExpect(status().isUnauthorized());
    mvc.perform(get("/admin/tickets")).andExpect(status().is3xxRedirection());
    for (String action : List.of("confirm", "cancel")) {
      mvc.perform(
              post("/api/admin/tickets/" + t.id() + "/" + action)
                  .with(csrf())
                  .contentType("application/json")
                  .content("{}"))
          .andExpect(status().isUnauthorized());
      mvc.perform(
              post("/api/admin/tickets/" + t.id() + "/" + action)
                  .with(user(operator))
                  .contentType("application/json")
                  .content("{}"))
          .andExpect(status().isForbidden());
    }
    command(
        t.id(), "confirm", operator, new TicketOperationsRequests.Confirm(1L, false, null), 400);
    for (String field : List.of("status", "operator", "handledBy", "potentialReturn")) {
      var body = new HashMap<String, Object>(Map.of("ticketVersion", 1, "pixReceiptChecked", true));
      body.put(field, "spoof");
      command(t.id(), "confirm", operator, body, 400);
    }
    command(t.id(), "cancel", operator, new TicketOperationsRequests.Cancel(1L, " ", null), 400);
    command(
        t.id(),
        "cancel",
        operator,
        new TicketOperationsRequests.Cancel(1L, "x".repeat(251), null),
        400);
    command(
        t.id(),
        "cancel",
        operator,
        new TicketOperationsRequests.Cancel(1L, "Motivo", "x".repeat(501)),
        400);
    jdbc.update("UPDATE public.admins SET active=false WHERE id=?", actor);
    command(t.id(), "confirm", operator, confirm(1), 403);
    mvc.perform(get("/api/admin/tickets").with(user(operator))).andExpect(status().isForbidden());
    assertThat(jdbc.queryForObject("SELECT status FROM public.tickets", String.class))
        .isEqualTo("PENDING");
  }

  @Test
  void cancellationRecordsReasonNoteAndCurrentStateWithoutChangingFinancials() throws Exception {
    var t = issue(offer());
    var result =
        json(command(
                t.id(),
                "cancel",
                other,
                new TicketOperationsRequests.Cancel(
                    1L, "  Atendimento D'Água <texto>  ", "Observação\nsegunda linha"),
                200))
            .path("current");
    assertThat(result.path("ticket").path("status").asText()).isEqualTo("CANCELLED");
    assertThat(result.path("cancellationReason").asText()).isEqualTo("Atendimento D'Água <texto>");
    assertThat(result.path("operatorNote").asText()).contains("\n");
    assertThat(result.path("handledBy").asText()).isEqualTo(secondActor.toString());
    var conflict = json(command(t.id(), "confirm", operator, confirm(1), 409));
    assertThat(conflict.path("current").path("ticket").path("status").asText())
        .isEqualTo("CANCELLED");
    assertThat(conflict.path("current").path("ticketVersion").asLong()).isEqualTo(2);
    assertThat(service.detail(t.id(), actor).ticket().potentialReturn()).isEqualTo("18.00");
  }

  @Test
  void effectiveExpiryControlsQueriesCountsAndCommandsBeforeTheJob() throws Exception {
    var t = issue(offer());
    NOW.set(t.expiresAt());
    var q = service.list(actor, null, TicketStatus.EXPIRED, null, null, 0, 20);
    assertThat(q.counts().get(TicketStatus.EXPIRED)).isEqualTo(1);
    assertThat(q.tickets().content().get(0).status()).isEqualTo(TicketStatus.EXPIRED);
    assertThat(
            service
                .list(actor, null, TicketStatus.PENDING, null, null, 0, 20)
                .tickets()
                .totalElements())
        .isZero();
    assertThat(jdbc.queryForObject("SELECT status FROM public.tickets", String.class))
        .isEqualTo("PENDING");
    var result = command(t.id(), "confirm", operator, confirm(1), 409);
    assertThat(json(result).path("errorCode").asText()).isEqualTo("TICKET_EXPIRED");
    assertThat(json(result).path("current").path("ticketVersion").asInt()).isEqualTo(2);
    command(t.id(), "cancel", other, cancel(2), 409);
    assertThat(jdbc.queryForObject("SELECT handled_by FROM public.tickets", UUID.class)).isNull();
  }

  @Test
  void confirmsImmediatelyBeforeDeadlineButRefusesStartedMatches() throws Exception {
    var t = issue(offer());
    NOW.set(t.expiresAt().minusNanos(1000));
    assertThat(service.confirm(t.id(), actor, confirm(1)).changed()).isTrue();
    NOW.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    var s = offer();
    var next = issue(s);
    jdbc.update(
        "UPDATE public.matches SET match_date=? WHERE id=?",
        Timestamp.from(NOW.get()),
        s.matchId());
    assertThat(service.confirm(next.id(), actor, confirm(1)).errorCode())
        .isEqualTo("OFFER_UNAVAILABLE");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"suspension", "curation", "stale", "future", "missing", "cancelled", "closed"})
  void failsClosedForCurrentOfferConditionsButAllowsCancellation(String condition) {
    var s = offer();
    var t = issue(s);
    switch (condition) {
      case "suspension" -> jdbc.update("UPDATE public.market_selections SET admin_suspended=true");
      case "curation" -> jdbc.update("UPDATE public.markets SET curated=false");
      case "stale" ->
          jdbc.update(
              "UPDATE public.market_selections SET manual_reviewed_at=?",
              Timestamp.from(NOW.get().minusSeconds(86400)));
      case "future" ->
          jdbc.update(
              "UPDATE public.matches SET manual_reviewed_at=?",
              Timestamp.from(NOW.get().plusSeconds(60)));
      case "missing" -> jdbc.update("UPDATE public.markets SET manual_reviewed_at=NULL");
      case "cancelled" -> jdbc.update("UPDATE public.matches SET status='CANCELLED'");
      case "closed" -> jdbc.update("UPDATE public.matches SET status='CLOSED'");
    }
    assertThat(service.confirm(t.id(), actor, confirm(1)).changed()).isFalse();
    assertThat(service.detail(t.id(), actor).ticket().status()).isEqualTo(TicketStatus.PENDING);
    assertThat(service.cancel(t.id(), actor, cancel(1)).changed()).isTrue();
  }

  @Test
  void cancelledMatchesFlagConfirmedTicketsWithoutReversingDecision() {
    var s = offer();
    var confirmed = issue(s);
    var pending = issue(s);
    assertThat(service.confirm(confirmed.id(), actor, confirm(1)).changed()).isTrue();
    var m = catalog.match(s.matchId(), true);
    catalog.updateMatch(
        m.id(),
        new CatalogRequests.UpdateMatch(
            m.matchVersion(),
            m.competition(),
            m.homeTeam(),
            m.awayTeam(),
            m.startsAt(),
            com.trevo4folhas.match.MatchStatus.CANCELLED,
            false));
    var detail = service.detail(confirmed.id(), actor);
    assertThat(detail.affectedByCancelledMatch()).isTrue();
    assertThat(detail.ticket().status()).isEqualTo(TicketStatus.CONFIRMED);
    assertThat(
            service
                .list(actor, confirmed.code(), null, null, null, 0, 20)
                .tickets()
                .content()
                .get(0)
                .affectedByCancelledMatch())
        .isTrue();
    assertThat(service.confirm(pending.id(), actor, confirm(1)).changed()).isFalse();
    assertThat(service.cancel(pending.id(), actor, cancel(1)).changed()).isTrue();
  }

  @Test
  void exactCodeDatePaginationAndStatusCountsUseBankTimezone() throws Exception {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    for (var entry : Map.of(a, "2026-10-31T02:59:59", b, "2026-10-31T03:00:00").entrySet())
      jdbc.update(
          """
        INSERT INTO public.tickets(id,ticket_code,request_key,request_hash,session_hash,bettor_name,bettor_phone,stake_amount,total_odds,potential_return,status,expires_at,created_at)
        VALUES(?,?,?,repeat('a',64),repeat('b',64),'Pessoa fictícia','5511999999999',10,1.8,18,'PENDING',?,?)
        """,
          entry.getKey(),
          entry.getKey().equals(a) ? "TF-FILTER-A" : "TF-FILTER-B",
          UUID.randomUUID(),
          Timestamp.from(NOW.get().plusSeconds(1800)),
          LocalDateTime.parse(entry.getValue()));
    assertThat(
            service
                .list(
                    actor,
                    null,
                    null,
                    LocalDate.of(2026, 10, 31),
                    LocalDate.of(2026, 10, 31),
                    0,
                    20)
                .tickets()
                .content())
        .extracting(TicketOperationsDtos.Row::id)
        .containsExactly(b);
    assertThat(
            service
                .list(actor, "  tf-filter-a  ", null, null, null, 0, 20)
                .tickets()
                .totalElements())
        .isEqualTo(1);
    assertThat(service.list(actor, "TF-FILTER", null, null, null, 0, 20).tickets().totalElements())
        .isZero();
    var first = service.list(actor, null, null, null, null, 0, 1);
    var second = service.list(actor, null, null, null, null, 1, 1);
    assertThat(first.tickets().totalPages()).isEqualTo(2);
    assertThat(first.counts().get(TicketStatus.PENDING)).isEqualTo(2);
    assertThat(first.tickets().content().get(0).id())
        .isNotEqualTo(second.tickets().content().get(0).id());
    mvc.perform(get("/api/admin/tickets").with(user(operator)).param("status", "INVALID"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/admin/tickets").with(user(operator)).param("page", "-1"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/admin/tickets").with(user(operator)).param("from", "bad-date"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/admin/tickets/" + UUID.randomUUID()).with(user(operator)))
        .andExpect(status().isNotFound());
    assertThatThrownBy(
            () ->
                service.list(
                    actor, null, null, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1), 0, 20))
        .isInstanceOf(TicketException.class);
  }

  @Test
  void concurrentConfirmCancelHasExactlyOneWinner() throws Exception {
    var t = issue(offer());
    var executor = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    try {
      var a =
          executor.submit(
              () -> {
                start.await();
                return service.confirm(t.id(), actor, confirm(1));
              });
      var b =
          executor.submit(
              () -> {
                start.await();
                return service.cancel(t.id(), secondActor, cancel(1));
              });
      start.countDown();
      assertThat(
              List.of(a.get(5, TimeUnit.SECONDS).changed(), b.get(5, TimeUnit.SECONDS).changed()))
          .containsExactlyInAnyOrder(true, false);
      assertThat(service.detail(t.id(), actor).ticketVersion()).isEqualTo(2);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void expirationRacePreservesDecidedRecordsAndAuditWithOneTransition() throws Exception {
    var first = issue(offer());
    var second = issue(offer());
    service.confirm(second.id(), actor, confirm(1));
    var created =
        jdbc.queryForObject(
            "SELECT created_at FROM public.tickets WHERE id=?", LocalDateTime.class, first.id());
    NOW.set(first.expiresAt().plusSeconds(1));
    var executor = Executors.newFixedThreadPool(2);
    try {
      var a = executor.submit(() -> service.confirm(first.id(), actor, confirm(1)));
      var b = executor.submit(() -> expiration.expireBatch());
      a.get(5, TimeUnit.SECONDS);
      b.get(5, TimeUnit.SECONDS);
      assertThat(service.detail(first.id(), actor).ticket().status())
          .isEqualTo(TicketStatus.EXPIRED);
      assertThat(service.detail(first.id(), actor).ticketVersion()).isEqualTo(2);
      assertThat(service.detail(second.id(), actor).ticket().status())
          .isEqualTo(TicketStatus.CONFIRMED);
      new TicketExpirationScheduler(expiration).expire();
      assertThat(expiration.expireBatch()).isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT created_at FROM public.tickets WHERE id=?",
                  LocalDateTime.class,
                  first.id()))
          .isEqualTo(created);
      assertThat(
              jdbc.queryForObject(
                  "SELECT updated_at>=created_at FROM public.tickets WHERE id=?",
                  Boolean.class,
                  first.id()))
          .isTrue();
    } finally {
      executor.shutdownNow();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"suspension", "price", "kickoff", "expiry"})
  void rechecksClockAndAvailabilityAfterWaitingForCatalogLock(String change) throws Exception {
    var s = offer();
    var t = issue(s);
    try (Connection connection =
        DriverManager.getConnection(
            TestDb.currentJdbcUrl(POSTGRES), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement("SELECT id FROM public.matches WHERE id=? FOR UPDATE")) {
        statement.setObject(1, s.matchId());
        statement.executeQuery();
      }
      var executor = Executors.newSingleThreadExecutor();
      try {
        var future = executor.submit(() -> service.confirm(t.id(), actor, confirm(1)));
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (System.nanoTime() < limit
            && jdbc.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE 'SELECT id FROM public.matches%'",
                    Integer.class)
                == 0) Thread.sleep(10);
        assertThat(
                jdbc.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE 'SELECT id FROM public.matches%'",
                    Integer.class))
            .isGreaterThan(0);
        if (change.equals("expiry")) NOW.set(t.expiresAt());
        else if (change.equals("kickoff")) NOW.set(NOW.get().plusSeconds(7200));
        else
          try (var st =
              connection.prepareStatement(
                  change.equals("price")
                      ? "UPDATE public.market_selections SET odds=2.50,version=version+1 WHERE id=?"
                      : "UPDATE public.matches SET admin_suspended=true WHERE id=?")) {
            st.setObject(1, change.equals("price") ? s.id() : s.matchId());
            st.executeUpdate();
          }
        connection.commit();
        var result = future.get(5, TimeUnit.SECONDS);
        assertThat(result.changed()).isEqualTo(change.equals("price"));
        assertThat(service.detail(t.id(), actor).ticket().potentialReturn()).isEqualTo("18.00");
      } finally {
        executor.shutdownNow();
      }
    }
  }

  @Test
  void lockTimeoutReturnsRecoverableConflictAndDoesNotDecide() throws Exception {
    var t = issue(offer());
    try (Connection c =
        DriverManager.getConnection(
            TestDb.currentJdbcUrl(POSTGRES), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      c.setAutoCommit(false);
      try (var st = c.prepareStatement("SELECT id FROM public.tickets WHERE id=? FOR UPDATE")) {
        st.setObject(1, t.id());
        st.executeQuery();
      }
      assertThat(
              json(command(t.id(), "confirm", operator, confirm(1), 409))
                  .path("errorCode")
                  .asText())
          .isEqualTo("TICKET_BUSY");
      c.rollback();
    }
    assertThat(service.detail(t.id(), actor).ticketVersion()).isEqualTo(1);
  }

  @Test
  void auditIsAfterCommitRedactedAndRolledBackDecisionsLeaveNoEvent() throws Exception {
    var t = issue(offer());
    Logger logger = (Logger) LoggerFactory.getLogger(TicketOperationAudit.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      jdbc.execute(
          "CREATE FUNCTION public.reject_ticket_decision() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Synthetic rollback'; END $$");
      jdbc.execute(
          "CREATE TRIGGER reject_ticket_decision BEFORE UPDATE ON public.tickets FOR EACH ROW EXECUTE FUNCTION public.reject_ticket_decision()");
      command(t.id(), "confirm", operator, confirm(1), 503);
      assertThat(appender.list).isEmpty();
      assertThat(service.detail(t.id(), actor).ticketVersion()).isEqualTo(1);
      jdbc.execute("DROP TRIGGER reject_ticket_decision ON public.tickets");
      service.cancel(
          t.id(),
          actor,
          new TicketOperationsRequests.Cancel(1L, "Razão privada fictícia", "Observação privada"));
      var logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
      assertThat(logs).hasSize(1);
      assertThat(logs.get(0))
          .contains("action=CANCEL", "status=CANCELLED", "version=2")
          .doesNotContain("Pessoa", t.code(), "5511999999999", "Razão", "Observação");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void catalogEditBeforeFirstTicketReferenceInvalidatesTheWaitingIssuance() throws Exception {
    var s = offer();
    var match = catalog.match(s.matchId(), true);
    var edited = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var edit =
          executor.submit(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(transactions)
                      .execute(
                          status -> {
                            catalog.updateMatch(
                                match.id(),
                                new CatalogRequests.UpdateMatch(
                                    match.matchVersion(),
                                    "TESTE editado",
                                    match.homeTeam(),
                                    match.awayTeam(),
                                    match.startsAt(),
                                    match.status(),
                                    false));
                            edited.countDown();
                            try {
                              if (!release.await(3, TimeUnit.SECONDS))
                                throw new IllegalStateException("Test release timeout");
                            } catch (InterruptedException failure) {
                              Thread.currentThread().interrupt();
                              throw new IllegalStateException("Interrupted test");
                            }
                            return true;
                          }));
      assertThat(edited.await(2, TimeUnit.SECONDS)).isTrue();
      var creation =
          executor.submit(
              () -> {
                try {
                  issue(s);
                  return true;
                } catch (TicketException conflict) {
                  return false;
                }
              });
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
      release.countDown();
      edit.get(5, TimeUnit.SECONDS);
      assertThat(creation.get(5, TimeUnit.SECONDS)).isFalse();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM public.tickets", Integer.class))
          .isZero();
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void expiryBatchesAreBoundedAndSkipLockedTickets() throws Exception {
    var t = issue(offer());
    NOW.set(t.expiresAt());
    try (Connection c =
        DriverManager.getConnection(
            TestDb.currentJdbcUrl(POSTGRES), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      c.setAutoCommit(false);
      try (var statement =
          c.prepareStatement("SELECT id FROM public.tickets WHERE id=? FOR UPDATE")) {
        statement.setObject(1, t.id());
        statement.executeQuery();
      }
      assertThat(expiration.expireBatch()).isZero();
      c.rollback();
    }
    assertThat(expiration.expireBatch()).isEqualTo(1);
    jdbc.update(
        """
      INSERT INTO public.tickets(ticket_code,request_key,request_hash,session_hash,bettor_name,bettor_phone,stake_amount,total_odds,potential_return,status,expires_at)
      SELECT 'TF-BATCH-'||i,gen_random_uuid(),repeat('a',64),repeat('b',64),'Pessoa fictícia','5511999999999',10,1.8,18,'PENDING',? FROM generate_series(1,205) i
      """,
        Timestamp.from(NOW.get().minusSeconds(1)));
    assertThat(expiration.expireBatch()).isEqualTo(200);
    assertThat(expiration.expireBatch()).isEqualTo(5);
    assertThat(expiration.expireBatch()).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.tickets WHERE version=2 AND status='EXPIRED'",
                Integer.class))
        .isEqualTo(206);
  }

  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

  @Test
  void concurrentProviderSuspensionCommitsBeforeConfirmationCanProceed() throws Exception {
    var f =
        new ProviderMapping.Fixture(
            "race-fixture",
            "TESTE fornecedor",
            "Equipe API A",
            "Equipe API B",
            NOW.get().plusSeconds(7200),
            "OPEN");
    importer.fixture(f);
    var o =
        new ProviderMapping.Offer(
            f.externalId(),
            "8",
            "1",
            "1X2",
            "Resultado API",
            "FT",
            "NONE",
            "NONE",
            "REGULATION_TIME",
            "Regra fictícia",
            "NONE",
            "HOME",
            "HOME",
            "Casa",
            new BigDecimal("1.80"),
            NOW.get().minusSeconds(1));
    importer.offer(o);
    jdbc.update("UPDATE public.markets SET curated=true");
    jdbc.update("UPDATE public.market_selections SET curated=true");
    var id = jdbc.queryForObject("SELECT id FROM public.market_selections", UUID.class);
    var t = issue(catalogRepository.selection(id, false));
    var changed = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);
    try {
      var sync =
          executor.submit(
              () ->
                  new org.springframework.transaction.support.TransactionTemplate(transactions)
                      .execute(
                          status -> {
                            importer.fixture(
                                new ProviderMapping.Fixture(
                                    f.externalId(),
                                    f.competition(),
                                    f.home(),
                                    f.away(),
                                    f.startsAt(),
                                    "SUSPENDED"));
                            changed.countDown();
                            try {
                              if (!release.await(3, TimeUnit.SECONDS))
                                throw new IllegalStateException("Test release timeout");
                            } catch (InterruptedException failure) {
                              Thread.currentThread().interrupt();
                              throw new IllegalStateException("Interrupted test");
                            }
                            return true;
                          }));
      assertThat(changed.await(2, TimeUnit.SECONDS)).isTrue();
      var confirm = executor.submit(() -> service.confirm(t.id(), actor, confirm(1)));
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
      release.countDown();
      sync.get(5, TimeUnit.SECONDS);
      assertThat(confirm.get(5, TimeUnit.SECONDS).errorCode()).isEqualTo("OFFER_UNAVAILABLE");
      assertThat(service.detail(t.id(), actor).ticket().status()).isEqualTo(TicketStatus.PENDING);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void providerReplayRegressionAndPartialFailureCannotMakeConfirmationEligible() {
    var f =
        new ProviderMapping.Fixture(
            "test-fixture",
            "TESTE fornecedor",
            "Equipe API A",
            "Equipe API B",
            NOW.get().plusSeconds(7200),
            "OPEN");
    importer.fixture(f);
    var o =
        new ProviderMapping.Offer(
            f.externalId(),
            "8",
            "1",
            "1X2",
            "Resultado API",
            "FT",
            "NONE",
            "NONE",
            "REGULATION_TIME",
            "Regra fictícia",
            "NONE",
            "HOME",
            "HOME",
            "Casa",
            new BigDecimal("1.80"),
            NOW.get().minusSeconds(30));
    assertThat(importer.offer(o)).isTrue();
    jdbc.update("UPDATE public.markets SET curated=true");
    jdbc.update("UPDATE public.market_selections SET curated=true");
    var id = jdbc.queryForObject("SELECT id FROM public.market_selections", UUID.class);
    var selection = catalogRepository.selection(id, false);
    var t = issue(selection);
    NOW.set(NOW.get().plusSeconds(60));
    importer.fixture(f);
    assertThat(importer.offer(o)).isTrue();
    assertThat(service.confirm(t.id(), actor, confirm(1)).changed()).isFalse();
    // New independently valid ticket for regression/quarantine and structural conflict.
    NOW.set(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
    var fresh =
        new ProviderMapping.Offer(
            o.fixtureId(),
            o.bookmakerId(),
            o.marketId(),
            o.marketType(),
            o.marketName(),
            o.period(),
            o.line(),
            o.participant(),
            o.ruleKey(),
            o.ruleText(),
            o.specifiersKey(),
            o.selectionKey(),
            o.selectionCode(),
            o.selectionName(),
            o.odds(),
            NOW.get().minusSeconds(1));
    assertThat(importer.offer(fresh)).isTrue();
    importer.fixture(f);
    var next = issue(catalogRepository.selection(id, false));
    var regression =
        new ProviderMapping.Offer(
            o.fixtureId(),
            o.bookmakerId(),
            o.marketId(),
            o.marketType(),
            o.marketName(),
            o.period(),
            o.line(),
            o.participant(),
            o.ruleKey(),
            o.ruleText(),
            o.specifiersKey(),
            o.selectionKey(),
            o.selectionCode(),
            o.selectionName(),
            new BigDecimal("2.00"),
            o.updatedAt().minusSeconds(1));
    assertThat(importer.offer(regression)).isFalse();
    assertThat(service.confirm(next.id(), actor, confirm(1)).changed()).isFalse();
    assertThat(importer.offer(fresh)).isTrue();
    var absent =
        new ProviderMapping.Offer(
            o.fixtureId(),
            o.bookmakerId(),
            o.marketId(),
            o.marketType(),
            o.marketName(),
            o.period(),
            o.line(),
            o.participant(),
            o.ruleKey(),
            o.ruleText(),
            o.specifiersKey(),
            o.selectionKey(),
            o.selectionCode(),
            o.selectionName(),
            null,
            null);
    assertThat(importer.offer(absent)).isFalse();
    assertThat(service.confirm(next.id(), actor, confirm(1)).changed()).isFalse();
    assertThat(importer.offer(fresh)).isTrue();
    importer.fixture(
        new ProviderMapping.Fixture(
            f.externalId(),
            f.competition(),
            f.home(),
            f.away(),
            f.startsAt().plusSeconds(3600),
            "OPEN"));
    assertThat(service.confirm(next.id(), actor, confirm(1)).changed()).isFalse();
    assertThat(service.detail(next.id(), actor).ticket().items().get(0).matchDateSnapshot())
        .isEqualTo(f.startsAt());
    assertThat(service.detail(next.id(), actor).ticket().potentialReturn()).isEqualTo("18.00");
  }
}
