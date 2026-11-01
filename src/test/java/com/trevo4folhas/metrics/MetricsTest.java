package com.trevo4folhas.metrics;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import com.trevo4folhas.admin.*;
import com.trevo4folhas.db.TestDb;

import io.micrometer.core.instrument.MeterRegistry;

@SpringBootTest
@AutoConfigureMockMvc
class MetricsTest {
  static final PostgreSQLContainer<?> DB = TestDb.startPostgres16();
  static final Instant NOW = Instant.parse("2026-11-03T15:00:00Z");

  @TestConfiguration
  static class TimeConfig {
    @Bean
    @Primary
    Clock metricsClock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", () -> TestDb.currentJdbcUrl(DB));
    p.add("spring.datasource.username", DB::getUsername);
    p.add("spring.datasource.password", DB::getPassword);
    p.add("trevo.betting.min-stake-amount", () -> "5.00");
    p.add("trevo.betting.max-stake-amount", () -> "500.00");
    p.add("trevo.betting.max-selections", () -> "10");
    p.add("trevo.betting.max-potential-return", () -> "10000.00");
    p.add("trevo.betting.ticket-validity-minutes", () -> "30");
  }

  @Autowired JdbcTemplate jdbc;
  @Autowired MetricsService service;
  @Autowired MockMvc mvc;
  @Autowired AdminRepository admins;
  @Autowired AdminUserDetailsService users;
  @Autowired PasswordEncoder passwords;
  @Autowired MeterRegistry meters;
  UUID actor;
  UserDetails operator;

  @BeforeEach
  void setup() {
    actor =
        admins
            .saveAndFlush(
                new Admin(
                    "metrics-operator",
                    "Operador fictício",
                    passwords.encode("Synthetic-Operator-Password")))
            .getId();
    operator = users.loadUserByUsername("metrics-operator");
  }

  @AfterEach
  void clean() {
    for (String t :
        List.of("ticket_items", "tickets", "market_selections", "markets", "matches", "admins"))
      jdbc.execute("DELETE FROM public." + t);
  }

  @AfterAll
  static void stop() {
    TestDb.stopQuietly(DB);
  }

  UUID ticket(
      String amount,
      String status,
      String created,
      String handled,
      String expires,
      boolean clicked) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
   INSERT INTO public.tickets(id,ticket_code,request_key,request_hash,session_hash,bettor_name,bettor_phone,stake_amount,total_odds,potential_return,status,expires_at,created_at,handled_by,handled_at,cancellation_reason,whatsapp_clicked_at)
   VALUES(?,?,?,repeat('a',64),repeat('b',64),'Pessoa fictícia privada','5511999999999',?::numeric,1.8,?::numeric*1.8,?,?,?, ?,?,?,?)
   """,
        id,
        "TF-" + id.toString().substring(0, 10),
        UUID.randomUUID(),
        amount,
        amount,
        status,
        Timestamp.from(Instant.parse(expires)),
        LocalDateTime.ofInstant(Instant.parse(created), ZoneOffset.UTC),
        handled == null ? null : actor,
        handled == null ? null : Timestamp.from(Instant.parse(handled)),
        status.equals("CANCELLED") ? "Motivo privado" : null,
        clicked ? Timestamp.from(Instant.parse(created).plusSeconds(30)) : null);
    return id;
  }

  OperationalMetrics read(String from, String to) {
    return service.read(actor, LocalDate.parse(from), LocalDate.parse(to));
  }

  @Test
  void fin01And02KeepVolumeAndEmissionCohortsOnDistinctTimeAxes() {
    ticket(
        "10",
        "CONFIRMED",
        "2026-11-03T02:55:00Z",
        "2026-11-03T03:05:00Z",
        "2026-11-03T03:25:00Z",
        true);
    ticket("20", "PENDING", "2026-11-03T14:00:00Z", null, "2026-11-03T16:00:00Z", false);
    ticket(
        "30",
        "CANCELLED",
        "2026-11-03T14:00:00Z",
        "2026-11-03T14:05:00Z",
        "2026-11-03T14:30:00Z",
        false);
    var today = read("2026-11-03", "2026-11-03");
    assertThat(today.cohort().emitted()).isEqualTo(2);
    assertThat(today.cohort().confirmed()).isZero();
    assertThat(today.confirmed().count()).isEqualTo(1);
    assertThat(today.confirmed().volume()).isEqualTo("10.00");
    assertThat(today.confirmed().averageStake()).isEqualTo("10.00");
    assertThat(today.confirmed().potentialReturns()).isEqualTo("18.00");
    assertThat(today.confirmed().medianSeconds()).isEqualTo("600.000");
    var yesterday = read("2026-11-02", "2026-11-02");
    assertThat(yesterday.cohort().confirmed()).isEqualTo(1);
    assertThat(yesterday.cohort().confirmationConversionPercent()).isEqualTo("100.00");
    assertThat(yesterday.confirmed().count()).isZero();
  }

  @Test
  void fin04StatesAreExclusiveAndOpenCohortCannotShowConversion() {
    ticket("10", "PENDING", "2026-11-03T14:00:00Z", null, "2026-11-03T15:00:00Z", true);
    ticket("20", "PENDING", "2026-11-03T14:00:00Z", null, "2026-11-03T15:00:01Z", false);
    var data = read("2026-11-03", "2026-11-03");
    assertThat(data.cohort().emitted()).isEqualTo(2);
    assertThat(data.cohort().effectiveExpired()).isEqualTo(1);
    assertThat(data.cohort().validPending()).isEqualTo(1);
    assertThat(data.cohort().closed()).isFalse();
    assertThat(data.cohort().confirmationConversionPercent()).isNull();
    assertThat(data.cohort().clickConversionPercent()).isNull();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM public.tickets WHERE status='PENDING'", Long.class))
        .isEqualTo(2);
  }

  @Test
  void fin05ZeroBaseShowsNullAverageAndConversions() {
    var data = read("2026-11-03", "2026-11-03");
    assertThat(data.confirmed().volume()).isEqualTo("0.00");
    assertThat(data.confirmed().averageStake()).isNull();
    assertThat(data.confirmed().medianSeconds()).isNull();
    assertThat(data.cohort().clickConversionPercent()).isNull();
  }

  @Test
  void closedCohortAndQuantilesReportSampleAndExcludeOnlyInvalidTimings() {
    ticket(
        "10",
        "CONFIRMED",
        "2026-11-03T13:00:00Z",
        "2026-11-03T13:01:00Z",
        "2026-11-03T13:30:00Z",
        true);
    ticket(
        "20",
        "CONFIRMED",
        "2026-11-03T13:00:00Z",
        "2026-11-03T13:05:00Z",
        "2026-11-03T13:30:00Z",
        false);
    ticket(
        "30",
        "CONFIRMED",
        "2026-11-03T13:00:00Z",
        "2026-11-03T12:59:00Z",
        "2026-11-03T13:30:00Z",
        false);
    var data = read("2026-11-03", "2026-11-03");
    assertThat(data.cohort().closed()).isTrue();
    assertThat(data.cohort().clickConversionPercent()).isEqualTo("33.33");
    assertThat(data.cohort().confirmationConversionPercent()).isEqualTo("100.00");
    assertThat(data.confirmed().volume()).isEqualTo("60.00");
    assertThat(data.confirmed().timingSampleSize()).isEqualTo(2);
    assertThat(data.confirmed().medianSeconds()).isEqualTo("180.000");
    assertThat(data.confirmed().p90Seconds()).isEqualTo("276.000");
    assertThat(data.confirmed().invalidTimingRecords()).isEqualTo(1);
  }

  @Test
  void fin06CancelledMatchDoesNotReduceConfirmedVolume() {
    UUID id =
        ticket(
            "10",
            "CONFIRMED",
            "2026-11-03T13:00:00Z",
            "2026-11-03T13:05:00Z",
            "2026-11-03T13:30:00Z",
            false);
    // Attach a real snapshot using the same schema contract as issuance.
    UUID match =
        jdbc.queryForObject(
            "INSERT INTO public.matches(competition,home_team,away_team,match_date,status,source_namespace,external_fixture_id,provider_status) VALUES('TESTE','Equipe A','Equipe B',now()+interval '1 day','CANCELLED','MANUAL','metric-fixture','CANCELLED') RETURNING id",
            UUID.class);
    UUID market =
        jdbc.queryForObject(
            "INSERT INTO public.markets(match_id,source_namespace,bookmaker_id,external_market_id,market_type,display_name,period,line,participant,specifiers_key,rule_key,rule_text,provider_status) VALUES(?,'MANUAL','NONE','NONE','1X2','Resultado','FT','NONE','NONE','NONE','RULE','Regra','OPEN') RETURNING id",
            UUID.class,
            match);
    UUID selection =
        jdbc.queryForObject(
            "INSERT INTO public.market_selections(market_id,external_selection_key,selection_code,display_name,odds,provider_status) VALUES(?,'HOME','HOME','Casa',1.8,'OPEN') RETURNING id",
            UUID.class,
            market);
    jdbc.update(
        """
   INSERT INTO public.ticket_items(ticket_id,match_id,market_selection_id,selected_odds,match_version_snapshot,market_version_snapshot,selection_version_snapshot,market_snapshot,selection_snapshot,selection_code_snapshot,period_snapshot,line_snapshot,participant_snapshot,rule_snapshot,provider_snapshot,bookmaker_snapshot,external_fixture_id_snapshot,external_market_id_snapshot,external_selection_key_snapshot,specifiers_snapshot,competition_snapshot,home_team_snapshot,away_team_snapshot,match_date_snapshot)
   VALUES(?,?,?,1.8,1,1,1,'Resultado','Casa','HOME','FT','NONE','NONE','Regra','MANUAL','NONE','metric-fixture','NONE','HOME','NONE','TESTE','Equipe A','Equipe B',now()+interval '1 day')
   """,
        id,
        match,
        selection);
    var data = read("2026-11-03", "2026-11-03");
    assertThat(data.confirmed().volume()).isEqualTo("10.00");
    assertThat(data.confirmed().affectedByCancelledMatch()).isEqualTo(1);
  }

  @Test
  void httpAccessHealthAndMetricsTagsDoNotExposePrivateIdentifiers() throws Exception {
    UUID id = ticket("10", "PENDING", "2026-11-03T14:00:00Z", null, "2026-11-03T16:00:00Z", false);
    mvc.perform(get("/api/admin/metrics")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"UP\"}", true));
    var response =
        mvc.perform(get("/api/admin/metrics").with(user(operator)))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn();
    assertThat(response.getResponse().getContentAsString())
        .doesNotContain("Pessoa fictícia", "5511999999999", actor.toString());
    mvc.perform(get("/api/admin/tickets/" + id).with(user(operator))).andExpect(status().isOk());
    mvc.perform(get("/api/admin/metrics").with(user(operator)).param("from", "bad-date"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/api/admin/metrics")
                .with(user(operator))
                .param("from", "2026-11-04")
                .param("to", "2026-11-03"))
        .andExpect(status().isBadRequest());
    String tags =
        meters.getMeters().stream()
            .map(m -> m.getId().getTags().toString())
            .reduce("", String::concat);
    assertThat(tags)
        .doesNotContain(id.toString(), actor.toString(), "5511999999999", "metrics-operator");
    assertThat(meters.find("trevo.http.duration").tag("operation", "metrics").timers())
        .isNotEmpty();
  }

  @Test
  void disablingOrRotatingCredentialsRevokesPreviouslyAuthenticatedSessions() throws Exception {
    jdbc.update(
        "UPDATE public.admins SET password_hash=? WHERE id=?",
        passwords.encode("New-Synthetic-Password-2026"),
        actor);
    mvc.perform(get("/api/admin/matches").with(user(operator)))
        .andExpect(status().isUnauthorized());
    var fresh = users.loadUserByUsername("metrics-operator");
    jdbc.update("UPDATE public.admins SET active=false WHERE id=?", actor);
    for (String path :
        List.of(
            "/api/admin/metrics", "/api/admin/matches", "/api/admin/tickets", "/actuator/metrics"))
      mvc.perform(get(path).with(user(fresh))).andExpect(status().isUnauthorized());
    mvc.perform(get("/admin/metrics").with(user(fresh))).andExpect(status().is3xxRedirection());
  }
}
