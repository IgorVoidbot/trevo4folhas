package com.trevo4folhas.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import com.trevo4folhas.admin.Admin;
import com.trevo4folhas.admin.AdminRepository;
import com.trevo4folhas.match.Match;
import com.trevo4folhas.match.MatchRepository;
import com.trevo4folhas.match.MatchStatus;

/**
 * First JPA slice against real PostgreSQL 16: {@link com.trevo4folhas.shared.BaseEntity} plus one
 * entity per table family round-trip through Spring Data repositories with {@code
 * ddl-auto=validate} and database-managed audit columns.
 *
 * <p>The datasource comes from {@link TestDb} (real {@code postgres:16-alpine} through
 * Testcontainers, never H2) and every connection is pinned to UTC like the application's Hikari
 * configuration. Transactions are disabled at the test level so each repository call commits in its
 * own transaction, which is exactly what lets the audit assertions observe the {@code
 * set_updated_at} trigger firing across statements.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JpaRoundTripAuditTest {

  private static final PostgreSQLContainer<?> POSTGRES = TestDb.startPostgres16();

  static {
    TestDb.migrate(POSTGRES);
  }

  @DynamicPropertySource
  static void registerRuntimeProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", () -> TestDb.currentJdbcUrl(POSTGRES));
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET TIME ZONE 'UTC'");
    registry.add("trevo.betting.min-stake-amount", () -> "5.00");
    registry.add("trevo.betting.max-stake-amount", () -> "500.00");
    registry.add("trevo.betting.max-selections", () -> "10");
    registry.add("trevo.betting.max-potential-return", () -> "10000.00");
    registry.add("trevo.betting.ticket-validity-minutes", () -> "30");
  }

  @Autowired private AdminRepository adminRepository;
  @Autowired private MatchRepository matchRepository;
  @Autowired private javax.sql.DataSource dataSource;

  @AfterEach
  void cleanUp() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DELETE FROM public.ticket_items");
    jdbc.execute("DELETE FROM public.tickets");
    jdbc.execute("DELETE FROM public.market_selections");
    jdbc.execute("DELETE FROM public.markets");
    jdbc.execute("DELETE FROM public.matches");
    jdbc.execute("DELETE FROM public.admins");
  }

  @AfterAll
  static void stopContainer() {
    TestDb.stopQuietly(POSTGRES);
  }

  @Test
  void baseEntityRoundTripsWithDatabaseManagedAuditColumns() {
    Admin admin = new Admin("ops.roundtrip", "Operador Round Trip", "bcrypt-checkpoint-hash");
    Admin saved = adminRepository.save(admin);

    assertThat(saved.getId()).isNotNull();
    Admin reloaded = adminRepository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getUsername()).isEqualTo("ops.roundtrip");
    assertThat(reloaded.isActive()).isTrue();
    // BaseEntity never writes these columns: PostgreSQL defaults fill them on insert.
    assertThat(reloaded.getCreatedAt()).isNotNull();
    assertThat(reloaded.getUpdatedAt()).isNotNull();
  }

  @Test
  void matchRoundTripsWithAssignedUuidVersionAndBusinessFields() {
    Instant kickoff = Instant.parse("2026-11-01T21:00:00Z");
    Match match =
        new Match(
            "Brasileirão Série A",
            "Flamengo",
            "Vasco",
            kickoff,
            MatchStatus.OPEN,
            "MANUAL",
            "fixture-roundtrip-1",
            "MANUAL");
    Match saved = matchRepository.save(match);

    Match reloaded = matchRepository.findById(saved.getId()).orElseThrow();
    assertThat(reloaded.getId()).isEqualTo(match.getId());
    assertThat(reloaded.getCompetition()).isEqualTo("Brasileirão Série A");
    assertThat(reloaded.getStartsAt()).isEqualTo(kickoff);
    assertThat(reloaded.getStatus()).isEqualTo(MatchStatus.OPEN);
    assertThat(reloaded.getVersion()).isEqualTo(1L);
    assertThat(reloaded.getCreatedAt()).isNotNull();
    assertThat(reloaded.getUpdatedAt()).isNotNull();

    // Canonical external identity resolves to the same row, without duplication.
    UUID found =
        matchRepository
            .findBySourceNamespaceAndExternalFixtureId("MANUAL", "fixture-roundtrip-1")
            .orElseThrow()
            .getId();
    assertThat(found).isEqualTo(match.getId());
  }

  @Test
  void commercialChangeRenewsVersionWhileDatabaseRefreshesAudit() {
    Match match =
        new Match(
            "Brasileirão Série A",
            "Palmeiras",
            "Corinthians",
            Instant.parse("2026-11-02T21:00:00Z"),
            MatchStatus.OPEN,
            "MANUAL",
            "fixture-audit-1",
            "MANUAL");
    matchRepository.save(match);
    Match afterInsert = matchRepository.findById(match.getId()).orElseThrow();
    LocalDateTime createdAt = afterInsert.getCreatedAt();
    LocalDateTime updatedAtAfterInsert = afterInsert.getUpdatedAt();

    afterInsert.changeStatus(MatchStatus.SUSPENDED);
    matchRepository.save(afterInsert);

    Match afterUpdate = matchRepository.findById(match.getId()).orElseThrow();
    assertThat(afterUpdate.getStatus()).isEqualTo(MatchStatus.SUSPENDED);
    assertThat(afterUpdate.getVersion()).isEqualTo(2L);
    // The trigger preserves created_at and refreshes updated_at on every UPDATE.
    assertThat(afterUpdate.getCreatedAt()).isEqualTo(createdAt);
    assertThat(afterUpdate.getUpdatedAt()).isAfter(updatedAtAfterInsert);
  }

  @Test
  void operationalMetadataNeverRenewsCommercialVersion() {
    Match match =
        new Match(
            "Brasileirão Série A",
            "Santos",
            "São Paulo",
            Instant.parse("2026-11-03T21:00:00Z"),
            MatchStatus.OPEN,
            "MANUAL",
            "fixture-audit-2",
            "MANUAL");
    matchRepository.save(match);

    Instant attemptAt = Instant.parse("2026-10-25T12:00:00Z");
    assertThat(matchRepository.recordRequestAttempt(match.getId(), attemptAt)).isEqualTo(1);

    Match afterAttempt = matchRepository.findById(match.getId()).orElseThrow();
    assertThat(afterAttempt.getRequestAttemptAt()).isEqualTo(attemptAt);
    // A mere request attempt is bookkeeping: the commercial version stays untouched.
    assertThat(afterAttempt.getVersion()).isEqualTo(1L);
  }
}
