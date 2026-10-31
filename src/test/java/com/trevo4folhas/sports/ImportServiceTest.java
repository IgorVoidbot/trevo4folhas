package com.trevo4folhas.sports;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import com.trevo4folhas.catalog.CatalogRequests;
import com.trevo4folhas.catalog.CatalogService;
import com.trevo4folhas.db.TestDb;
import com.trevo4folhas.sports.ProviderMapping.*;

/** Synthetic fixtures on PostgreSQL 16. No authenticated provider requests. */
@SpringBootTest
class ImportServiceTest {
  static final PostgreSQLContainer<?> POSTGRES = TestDb.startPostgres16();

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
    r.add("trevo.sports.max-price-age-seconds", () -> "3600");
    r.add("trevo.sports.max-schedule-age-seconds", () -> "7200");
    r.add("trevo.sports.max-status-age-seconds", () -> "1800");
  }

  @Autowired ImportService importer;
  @Autowired CatalogService catalog;
  @Autowired JdbcTemplate jdbc;
  private final ProviderMapping mapping =
      new ProviderMapping(SportsTestSupport.properties(Path.of("/tmp/unused")));

  @AfterAll
  static void stop() {
    TestDb.stopQuietly(POSTGRES);
  }

  @AfterEach
  void clean() {
    for (String table :
        List.of("ticket_items", "tickets", "market_selections", "markets", "matches"))
      jdbc.execute("DELETE FROM public." + table);
  }

  private Fixture fixture() throws Exception {
    return mapping
        .fixtures(SportsTestSupport.sample("fixtures", SportsTestSupport.mapper()).path("response"))
        .get(0);
  }

  private Offer offer() throws Exception {
    var rows = SportsTestSupport.sample("odds", SportsTestSupport.mapper()).path("response");
    ((com.fasterxml.jackson.databind.node.ObjectNode) rows.get(0))
        .put("update", Instant.now().minusSeconds(60).toString());
    return mapping.offers(rows).get(0);
  }

  private Offer change(Offer o, String bookmaker, BigDecimal price, Instant update) {
    return new Offer(
        o.fixtureId(),
        bookmaker,
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
        price,
        update);
  }

  @Test
  void duplicatePayloadDoesNotDuplicateOrRenewPriceAndNewOffersRequireCurators() throws Exception {
    Fixture f = fixture();
    Offer o = offer();
    importer.fixture(f);
    assertThat(importer.offer(o)).isTrue();
    UUID match = jdbc.queryForObject("SELECT id FROM public.matches", UUID.class);
    assertThat(catalog.matches(false, null, null, null, 0, 20).totalElements()).isZero();
    jdbc.update("UPDATE public.markets SET curated=true");
    jdbc.update("UPDATE public.market_selections SET curated=true");
    assertThat(catalog.match(match, false)).isNotNull();
    var before =
        jdbc.queryForMap("SELECT version,provider_updated_at FROM public.market_selections");
    importer.fixture(f);
    importer.offer(o);
    assertThat(jdbc.queryForMap("SELECT version,provider_updated_at FROM public.market_selections"))
        .isEqualTo(before);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.matches", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.markets", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.market_selections", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void subMicrosecondReplayCannotRenewOrChangeThePersistedPrice() throws Exception {
    importer.fixture(fixture());
    Offer original = offer();
    Instant precision =
        Instant.now()
            .minusSeconds(60)
            .truncatedTo(java.time.temporal.ChronoUnit.MICROS)
            .plusNanos(999);
    Offer first = change(original, original.bookmakerId(), original.odds(), precision);
    assertThat(importer.offer(first)).isTrue();
    var before =
        jdbc.queryForMap("SELECT version,provider_updated_at FROM public.market_selections");
    assertThat(importer.offer(first)).isTrue();
    assertThat(jdbc.queryForMap("SELECT version,provider_updated_at FROM public.market_selections"))
        .isEqualTo(before);
    assertThat(
            importer.offer(change(first, first.bookmakerId(), new BigDecimal("2.00"), precision)))
        .isFalse();
    assertThat(jdbc.queryForObject("SELECT odds FROM public.market_selections", BigDecimal.class))
        .isEqualByComparingTo(first.odds());
  }

  @Test
  void futureSyncEvidenceFailsClosedAtEveryLevel() throws Exception {
    importer.fixture(fixture());
    importer.offer(offer());
    jdbc.update("UPDATE public.markets SET curated=true");
    jdbc.update("UPDATE public.market_selections SET curated=true");
    assertThat(catalog.matches(false, null, null, null, 0, 20).totalElements()).isEqualTo(1);
    for (String table : List.of("matches", "markets", "market_selections")) {
      jdbc.update("UPDATE public." + table + " SET last_successful_sync_at=now()+interval '1 day'");
      assertThat(catalog.matches(false, null, null, null, 0, 20).totalElements()).isZero();
      jdbc.update("UPDATE public." + table + " SET last_successful_sync_at=now()");
    }
  }

  @Test
  void bookmakerNamespacesAreDistinctAndManualSuspensionSurvivesSync() throws Exception {
    importer.fixture(fixture());
    Offer o = offer();
    importer.offer(o);
    importer.offer(change(o, "100", o.odds(), o.updatedAt()));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.markets", Integer.class))
        .isEqualTo(2);
    jdbc.update("UPDATE public.matches SET admin_suspended=true");
    jdbc.update("UPDATE public.markets SET admin_suspended=true,curated=true");
    jdbc.update("UPDATE public.market_selections SET curated=true");
    importer.fixture(fixture());
    importer.offer(o);
    assertThat(jdbc.queryForObject("SELECT admin_suspended FROM public.matches", Boolean.class))
        .isTrue();
    assertThat(catalog.matches(false, null, null, null, 0, 20).totalElements()).isZero();
    UUID match = jdbc.queryForObject("SELECT id FROM public.matches", UUID.class);
    assertThatThrownBy(
            () ->
                catalog.createMarket(
                    match,
                    new CatalogRequests.CreateMarket(
                        2L, "TOTALS", "Total", "FT", "2.5", null, "RULE", "Regra", null, true)))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void regressingMissingOrReplayedChangedPriceIsBlockedWithoutOverwritingPrice() throws Exception {
    importer.fixture(fixture());
    Offer o = offer();
    importer.offer(o);
    assertThat(
            importer.offer(
                change(o, o.bookmakerId(), new BigDecimal("2.00"), o.updatedAt().minusSeconds(1))))
        .isFalse();
    assertThat(jdbc.queryForObject("SELECT odds FROM public.market_selections", BigDecimal.class))
        .isEqualByComparingTo("1.85");
    assertThat(
            jdbc.queryForObject(
                "SELECT provider_status FROM public.market_selections", String.class))
        .isEqualTo("INVALID");
    assertThat(importer.offer(change(o, o.bookmakerId(), new BigDecimal("2.00"), o.updatedAt())))
        .isFalse();
    assertThat(importer.offer(change(o, o.bookmakerId(), null, null))).isFalse();
    assertThat(
            importer.offer(
                change(o, o.bookmakerId(), new BigDecimal("2.00"), Instant.now().minusSeconds(1))))
        .isTrue();
  }

  @Test
  void newInvalidPriceIsNotInsertedAndOldPriceCannotBecomeFreshOnReplay() throws Exception {
    importer.fixture(fixture());
    Offer o = offer();
    assertThat(importer.offer(change(o, o.bookmakerId(), null, null))).isFalse();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.market_selections", Integer.class))
        .isZero();
    Offer stale = change(o, o.bookmakerId(), o.odds(), Instant.now().minusSeconds(7200));
    importer.offer(stale);
    jdbc.update("UPDATE public.markets SET curated=true");
    jdbc.update("UPDATE public.market_selections SET curated=true");
    importer.offer(stale);
    assertThat(catalog.matches(false, null, null, null, 0, 20).totalElements()).isZero();
  }

  @Test
  void referencedFixtureRescheduleIsBlockedAndPreservesKickoffAndSnapshots() throws Exception {
    Fixture f = fixture();
    importer.fixture(f);
    importer.offer(offer());
    reference();
    importer.fixture(
        new Fixture(
            f.externalId(),
            f.competition(),
            f.home(),
            f.away(),
            f.startsAt().plusSeconds(86400),
            "OPEN"));
    assertThat(jdbc.queryForObject("SELECT structural_conflict FROM public.matches", Boolean.class))
        .isTrue();
    assertThat(
            jdbc.queryForObject("SELECT match_date FROM public.matches", java.sql.Timestamp.class)
                .toInstant())
        .isEqualTo(f.startsAt());
    assertThat(
            jdbc.queryForObject("SELECT selected_odds FROM public.ticket_items", BigDecimal.class))
        .isEqualByComparingTo("1.85");
    importer.fixture(f);
    assertThat(jdbc.queryForObject("SELECT structural_conflict FROM public.matches", Boolean.class))
        .isTrue();
  }

  @Test
  void cancellationIsNotReopenedBySyncOrAdmin() throws Exception {
    Fixture f = fixture();
    importer.fixture(f);
    importer.fixture(
        new Fixture(
            f.externalId(), f.competition(), f.home(), f.away(), f.startsAt(), "CANCELLED"));
    importer.fixture(f);
    assertThat(jdbc.queryForObject("SELECT status FROM public.matches", String.class))
        .isEqualTo("CANCELLED");
    assertThat(jdbc.queryForObject("SELECT provider_status FROM public.matches", String.class))
        .isEqualTo("CANCELLED");
  }

  @Test
  void concurrentImportAndAdminSuspensionPreserveBothChanges() throws Exception {
    importer.fixture(fixture());
    Offer o = offer();
    importer.offer(o);
    UUID matchId = jdbc.queryForObject("SELECT id FROM public.matches", UUID.class);
    var match = catalog.match(matchId, true);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      var jobs =
          pool.invokeAll(
              List.<java.util.concurrent.Callable<Void>>of(
                  () -> {
                    importer.offer(
                        change(
                            o,
                            o.bookmakerId(),
                            new BigDecimal("2.00"),
                            Instant.now().minusSeconds(1)));
                    return null;
                  },
                  () -> {
                    catalog.updateMatch(
                        matchId,
                        new CatalogRequests.UpdateMatch(
                            match.matchVersion(),
                            match.competition(),
                            match.homeTeam(),
                            match.awayTeam(),
                            match.startsAt(),
                            match.status(),
                            true));
                    return null;
                  }));
      for (var job : jobs) job.get();
    } finally {
      pool.shutdownNow();
    }
    assertThat(jdbc.queryForObject("SELECT admin_suspended FROM public.matches", Boolean.class))
        .isTrue();
    assertThat(jdbc.queryForObject("SELECT odds FROM public.market_selections", BigDecimal.class))
        .isEqualByComparingTo("2.00");
    assertThat(catalog.matches(false, null, null, null, 0, 20).totalElements()).isZero();
  }

  private void reference() {
    jdbc.update(
        "INSERT INTO public.tickets(ticket_code,request_key,request_hash,session_hash,bettor_name,bettor_phone,stake_amount,total_odds,potential_return,status,expires_at) VALUES('TF-SYNTHETIC',gen_random_uuid(),repeat('a',64),repeat('b',64),'Pessoa fictícia','5511999999999',10,1.85,18.50,'PENDING',now()+interval '30 minutes')");
    jdbc.update(
        """
        INSERT INTO public.ticket_items(ticket_id,match_id,market_selection_id,selected_odds,match_version_snapshot,market_version_snapshot,selection_version_snapshot,
        market_snapshot,selection_snapshot,selection_code_snapshot,period_snapshot,line_snapshot,participant_snapshot,provider_snapshot,bookmaker_snapshot,external_fixture_id_snapshot,external_market_id_snapshot,external_selection_key_snapshot,specifiers_snapshot,competition_snapshot,home_team_snapshot,away_team_snapshot,match_date_snapshot)
        SELECT t.id,m.id,s.id,s.odds,m.version,k.version,s.version,k.display_name,s.display_name,s.selection_code,k.period,k.line,k.participant,k.source_namespace,k.bookmaker_id,m.external_fixture_id,k.external_market_id,s.external_selection_key,k.specifiers_key,m.competition,m.home_team,m.away_team,m.match_date
        FROM public.tickets t CROSS JOIN public.matches m JOIN public.markets k ON k.match_id=m.id JOIN public.market_selections s ON s.market_id=k.id
        """);
  }
}
