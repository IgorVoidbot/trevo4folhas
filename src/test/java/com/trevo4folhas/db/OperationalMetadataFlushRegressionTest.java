package com.trevo4folhas.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import com.trevo4folhas.market.Market;
import com.trevo4folhas.market.MarketRepository;
import com.trevo4folhas.market.MarketSelection;
import com.trevo4folhas.market.MarketSelectionRepository;
import com.trevo4folhas.match.Match;
import com.trevo4folhas.match.MatchRepository;
import com.trevo4folhas.match.MatchStatus;

import jakarta.persistence.EntityManager;

/**
 * Regression suite for the operational-metadata/commercial-flush interaction (review round 1, R1)
 * on {@link Match}, {@link Market} and {@link MarketSelection}, against real PostgreSQL 16.
 *
 * <p>Guarantee under test: operational bookkeeping ({@code recordRequestAttempt}) and commercial
 * changes must be preserved simultaneously. A commercial flush must never rewrite the operational
 * metadata columns with a stale persistence-context snapshot — not in the same transaction and not
 * when the bookkeeping committed from another transaction while a stale context was still open.
 * Bookkeeping itself must never renew the commercial {@code version}.
 *
 * <p>Documented post-bulk semantics: bulk bookkeeping bypasses the persistence context, so managed
 * instances keep their in-memory snapshot until they are re-read; the database row is
 * authoritative. Because the operational columns are mapped {@code updatable = false}, flushing a
 * stale managed instance can never clobber committed bookkeeping, in any transaction interleaving.
 *
 * <p>The datasource comes from {@link TestDb} (real {@code postgres:16-alpine} through
 * Testcontainers, Ryuk enabled, never H2) with {@code ddl-auto=validate} and UTC connections. The
 * class is non-transactional so each scenario controls its own transaction boundaries: the
 * same-transaction scenarios run inside one {@link TransactionTemplate}, and the cross-transaction
 * scenarios commit bookkeeping in a separate {@code REQUIRES_NEW} transaction while another
 * transaction still holds a pre-bookkeeping snapshot.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OperationalMetadataFlushRegressionTest {

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

  @Autowired private EntityManager em;
  @Autowired private MatchRepository matches;
  @Autowired private MarketRepository markets;
  @Autowired private MarketSelectionRepository selections;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private javax.sql.DataSource dataSource;

  private TransactionTemplate oneTransaction;
  private TransactionTemplate bookkeepingTransaction;

  @BeforeEach
  void transactionTemplates() {
    oneTransaction = new TransactionTemplate(transactionManager);
    bookkeepingTransaction = new TransactionTemplate(transactionManager);
    bookkeepingTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

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
  void matchBookkeepingSurvivesCommercialFlushInSameTransaction() {
    Instant attempt = Instant.parse("2026-10-25T12:00:00Z");
    UUID matchId =
        oneTransaction.execute(
            status -> {
              Match match = newMatch();
              em.persist(match);
              em.flush();
              assertThat(matches.recordRequestAttempt(match.getId(), attempt)).isEqualTo(1);
              match.changeStatus(MatchStatus.SUSPENDED);
              em.flush();
              em.clear();
              Match loaded = matches.findById(match.getId()).orElseThrow();
              assertThat(loaded.getVersion()).isEqualTo(2L);
              assertThat(loaded.getRequestAttemptAt())
                  .as("bulk bookkeeping must not be overwritten by the commercial flush")
                  .isEqualTo(attempt);
              return match.getId();
            });
    Match committed = matches.findById(matchId).orElseThrow();
    assertThat(committed.getVersion()).isEqualTo(2L);
    assertThat(committed.getRequestAttemptAt()).isEqualTo(attempt);
  }

  @Test
  void marketBookkeepingSurvivesCommercialFlushInSameTransaction() {
    Instant attempt = Instant.parse("2026-10-25T12:00:00Z");
    UUID marketId =
        oneTransaction.execute(
            status -> {
              Match match = newMatch();
              em.persist(match);
              Market market = newMarket(match);
              em.persist(market);
              em.flush();
              assertThat(markets.recordRequestAttempt(market.getId(), attempt)).isEqualTo(1);
              market.setCurated(true);
              em.flush();
              em.clear();
              Market loaded = markets.findById(market.getId()).orElseThrow();
              assertThat(loaded.getVersion()).isEqualTo(2L);
              assertThat(loaded.isCurated()).isTrue();
              assertThat(loaded.getRequestAttemptAt())
                  .as("bulk bookkeeping must not be overwritten by the commercial flush")
                  .isEqualTo(attempt);
              return market.getId();
            });
    Market committed = markets.findById(marketId).orElseThrow();
    assertThat(committed.getVersion()).isEqualTo(2L);
    assertThat(committed.getRequestAttemptAt()).isEqualTo(attempt);
  }

  @Test
  void selectionBookkeepingSurvivesCommercialFlushInSameTransaction() {
    Instant attempt = Instant.parse("2026-10-25T12:00:00Z");
    UUID selectionId =
        oneTransaction.execute(
            status -> {
              Match match = newMatch();
              em.persist(match);
              Market market = newMarket(match);
              em.persist(market);
              MarketSelection selection = newSelection(market);
              em.persist(selection);
              em.flush();
              assertThat(selections.recordRequestAttempt(selection.getId(), attempt)).isEqualTo(1);
              selection.updateOdds(new BigDecimal("3.50"));
              em.flush();
              em.clear();
              MarketSelection loaded = selections.findById(selection.getId()).orElseThrow();
              assertThat(loaded.getVersion()).isEqualTo(2L);
              assertThat(loaded.getOdds()).isEqualByComparingTo("3.50");
              assertThat(loaded.getRequestAttemptAt())
                  .as("bulk bookkeeping must not be overwritten by the commercial flush")
                  .isEqualTo(attempt);
              return selection.getId();
            });
    MarketSelection committed = selections.findById(selectionId).orElseThrow();
    assertThat(committed.getVersion()).isEqualTo(2L);
    assertThat(committed.getRequestAttemptAt()).isEqualTo(attempt);
  }

  @Test
  void matchBookkeepingSurvivesCommercialFlushFromAnotherTransaction() {
    Instant attempt = Instant.parse("2026-10-25T12:00:00Z");
    UUID matchId =
        oneTransaction.execute(
            status -> {
              Match match = newMatch();
              em.persist(match);
              return match.getId();
            });
    oneTransaction.execute(
        status -> {
          Match stale = em.find(Match.class, matchId);
          assertThat(stale.getRequestAttemptAt()).as("pre-bookkeeping snapshot").isNull();
          Integer updated =
              bookkeepingTransaction.execute(s -> matches.recordRequestAttempt(matchId, attempt));
          assertThat(updated).isEqualTo(1);
          stale.changeStatus(MatchStatus.SUSPENDED);
          em.flush();
          em.clear();
          return null;
        });
    Match loaded = matches.findById(matchId).orElseThrow();
    assertThat(loaded.getVersion()).isEqualTo(2L);
    assertThat(loaded.getRequestAttemptAt())
        .as("a stale snapshot from another transaction must not overwrite committed bookkeeping")
        .isEqualTo(attempt);
  }

  @Test
  void marketBookkeepingSurvivesCommercialFlushFromAnotherTransaction() {
    Instant attempt = Instant.parse("2026-10-25T12:00:00Z");
    UUID marketId =
        oneTransaction.execute(
            status -> {
              Match match = newMatch();
              em.persist(match);
              Market market = newMarket(match);
              em.persist(market);
              return market.getId();
            });
    oneTransaction.execute(
        status -> {
          Market stale = em.find(Market.class, marketId);
          assertThat(stale.getRequestAttemptAt()).as("pre-bookkeeping snapshot").isNull();
          Integer updated =
              bookkeepingTransaction.execute(s -> markets.recordRequestAttempt(marketId, attempt));
          assertThat(updated).isEqualTo(1);
          stale.setCurated(true);
          em.flush();
          em.clear();
          return null;
        });
    Market loaded = markets.findById(marketId).orElseThrow();
    assertThat(loaded.getVersion()).isEqualTo(2L);
    assertThat(loaded.isCurated()).isTrue();
    assertThat(loaded.getRequestAttemptAt())
        .as("a stale snapshot from another transaction must not overwrite committed bookkeeping")
        .isEqualTo(attempt);
  }

  @Test
  void selectionBookkeepingSurvivesCommercialFlushFromAnotherTransaction() {
    Instant attempt = Instant.parse("2026-10-25T12:00:00Z");
    UUID selectionId =
        oneTransaction.execute(
            status -> {
              Match match = newMatch();
              em.persist(match);
              Market market = newMarket(match);
              em.persist(market);
              MarketSelection selection = newSelection(market);
              em.persist(selection);
              return selection.getId();
            });
    oneTransaction.execute(
        status -> {
          MarketSelection stale = em.find(MarketSelection.class, selectionId);
          assertThat(stale.getRequestAttemptAt()).as("pre-bookkeeping snapshot").isNull();
          Integer updated =
              bookkeepingTransaction.execute(
                  s -> selections.recordRequestAttempt(selectionId, attempt));
          assertThat(updated).isEqualTo(1);
          stale.updateOdds(new BigDecimal("3.50"));
          em.flush();
          em.clear();
          return null;
        });
    MarketSelection loaded = selections.findById(selectionId).orElseThrow();
    assertThat(loaded.getVersion()).isEqualTo(2L);
    assertThat(loaded.getOdds()).isEqualByComparingTo("3.50");
    assertThat(loaded.getRequestAttemptAt())
        .as("a stale snapshot from another transaction must not overwrite committed bookkeeping")
        .isEqualTo(attempt);
  }

  private static Match newMatch() {
    return new Match(
        "Competição Teste",
        "Time A",
        "Time B",
        Instant.parse("2026-11-01T21:00:00Z"),
        MatchStatus.OPEN,
        "MANUAL",
        UUID.randomUUID().toString(),
        "MANUAL");
  }

  private static Market newMarket(Match match) {
    return new Market(
        match,
        "MANUAL",
        "MANUAL",
        "MANUAL",
        "1X2",
        "Resultado final",
        "FULL_TIME",
        "NONE",
        "NONE",
        "NONE",
        "RULE_V1",
        "Regra de teste",
        "MANUAL");
  }

  private static MarketSelection newSelection(Market market) {
    return new MarketSelection(market, "HOME", "HOME", "Casa", new BigDecimal("2.50"), "MANUAL");
  }
}
