package com.trevo4folhas.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import com.trevo4folhas.market.Market;
import com.trevo4folhas.market.MarketRepository;
import com.trevo4folhas.market.MarketSelection;
import com.trevo4folhas.market.MarketSelectionRepository;
import com.trevo4folhas.market.dto.CatalogSelectionDto;
import com.trevo4folhas.match.Match;
import com.trevo4folhas.match.MatchRepository;
import com.trevo4folhas.match.MatchStatus;
import com.trevo4folhas.ticket.Ticket;
import com.trevo4folhas.ticket.TicketItem;
import com.trevo4folhas.ticket.TicketItemRepository;
import com.trevo4folhas.ticket.TicketRepository;

/**
 * Catalog and ticket persistence invariants against real PostgreSQL 16: the Match → Market →
 * MarketSelection path, composite identity without duplication (labels excluded, {@code NONE}
 * sentinel distinct from zero), one selection per match inside a ticket, immutable snapshots,
 * {@code @Version} renewal rules and {@code BigDecimal} precision.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CatalogTicketPersistenceTest {

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

  @Autowired private MatchRepository matchRepository;
  @Autowired private MarketRepository marketRepository;
  @Autowired private MarketSelectionRepository marketSelectionRepository;
  @Autowired private TicketRepository ticketRepository;
  @Autowired private TicketItemRepository ticketItemRepository;
  @Autowired private TestEntityManager entityManager;
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
  @Transactional
  void catalogGraphAndDtosRoundTripThroughCanonicalRelations() {
    String suffix = shortId();
    Match match = newMatch(suffix);
    Market market = newMarket(match, "mkt-1x2-" + suffix, "NONE");
    MarketSelection selection = newSelection(market, "sel-home-" + suffix, "HOME", "2.50");
    matchRepository.save(match);
    marketRepository.save(market);
    marketSelectionRepository.save(selection);

    // Force a real round-trip to the database before asserting.
    entityManager.flush();
    entityManager.clear();

    MarketSelection reloaded =
        marketSelectionRepository
            .findByMarketIdAndExternalSelectionKey(market.getId(), "sel-home-" + suffix)
            .orElseThrow();
    assertThat(reloaded.getMarket().getMatch().getId()).isEqualTo(match.getId());
    assertThat(reloaded.getMarket().getLine()).isEqualTo("NONE");
    assertThat(reloaded.getMarket().getSpecifiersKey()).isEqualTo("NONE");

    CatalogSelectionDto dto = CatalogSelectionDto.from(reloaded);
    assertThat(dto.matchId()).isEqualTo(match.getId());
    assertThat(dto.marketId()).isEqualTo(market.getId());
    assertThat(dto.selectionId()).isEqualTo(selection.getId());
    assertThat(dto.matchVersion()).isEqualTo(1L);
    assertThat(dto.marketVersion()).isEqualTo(1L);
    assertThat(dto.selectionVersion()).isEqualTo(1L);
    assertThat(dto.marketType()).isEqualTo("1X2");
    assertThat(dto.selectionCode()).isEqualTo("HOME");
    assertThat(dto.period()).isEqualTo("FT");
    assertThat(dto.line()).isEqualTo("NONE");
    assertThat(dto.participant()).isEqualTo("NONE");
    assertThat(dto.odds()).isEqualTo("2.50");
  }

  @Test
  void duplicateMarketIdentityIsRejectedEvenWithDifferentDisplayLabels() {
    String suffix = shortId();
    Match match = newMatch(suffix);
    matchRepository.save(match);
    Market first = newMarket(match, "mkt-dup-" + suffix, "NONE");
    first.setDisplayName("Resultado Final");
    marketRepository.save(first);

    Market duplicate = newMarket(match, "mkt-dup-" + suffix, "NONE");
    duplicate.setDisplayName("Resultado Final (rótulo diferente)");
    assertThatThrownBy(() -> marketRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void sentinelNoneStaysDistinctFromZeroLine() {
    String suffix = shortId();
    Match match = newMatch(suffix);
    matchRepository.save(match);
    Market withNone = newMarket(match, "mkt-line-" + suffix, "NONE");
    Market withZero = newMarket(match, "mkt-line-" + suffix, "0");
    marketRepository.save(withNone);
    marketRepository.save(withZero);

    assertThat(marketRepository.findByMatchId(match.getId())).hasSize(2);
  }

  @Test
  void oneSelectionPerMatchInsideATicket() {
    String suffix = shortId();
    Match match = newMatch(suffix);
    Market market = newMarket(match, "mkt-two-" + suffix, "NONE");
    MarketSelection home = newSelection(market, "sel-a-" + suffix, "HOME", "2.50");
    MarketSelection draw = newSelection(market, "sel-b-" + suffix, "DRAW", "3.10");
    matchRepository.save(match);
    marketRepository.save(market);
    marketSelectionRepository.save(home);
    marketSelectionRepository.save(draw);
    Ticket ticket = newTicket(suffix);
    ticketRepository.save(ticket);

    TicketItem first = newTicketItem(ticket, match, home);
    ticketItemRepository.save(first);
    TicketItem second = newTicketItem(ticket, match, draw);
    assertThatThrownBy(() -> ticketItemRepository.saveAndFlush(second))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void snapshotsStayImmutableWhenTheCatalogMoves() {
    String suffix = shortId();
    Match match = newMatch(suffix);
    Market market = newMarket(match, "mkt-snap-" + suffix, "NONE");
    MarketSelection selection = newSelection(market, "sel-snap-" + suffix, "HOME", "2.50");
    matchRepository.save(match);
    marketRepository.save(market);
    marketSelectionRepository.save(selection);
    Ticket ticket = newTicket(suffix);
    ticketRepository.save(ticket);
    TicketItem item = newTicketItem(ticket, match, selection);
    ticketItemRepository.save(item);

    // Commercial move after issuance: price change renews the selection version.
    selection.updateOdds(new BigDecimal("3.50"));
    marketSelectionRepository.save(selection);
    MarketSelection moved = marketSelectionRepository.findById(selection.getId()).orElseThrow();
    assertThat(moved.getVersion()).isEqualTo(2L);
    assertThat(moved.getOdds()).isEqualByComparingTo("3.50");

    TicketItem reloaded = ticketItemRepository.findById(item.getId()).orElseThrow();
    assertThat(reloaded.getSelectedOdds()).isEqualByComparingTo("2.50");
    assertThat(reloaded.getSelectionVersionSnapshot()).isEqualTo(1L);
    assertThat(reloaded.getMatchVersionSnapshot()).isEqualTo(1L);
    assertThat(reloaded.getMarketVersionSnapshot()).isEqualTo(1L);
    assertThat(reloaded.getMarketSnapshot()).isEqualTo("Resultado Final");
    assertThat(reloaded.getSelectionSnapshot()).isEqualTo("Casa");
    assertThat(reloaded.getSelectionCodeSnapshot()).isEqualTo("HOME");
    assertThat(reloaded.getPeriodSnapshot()).isEqualTo("FT");
    assertThat(reloaded.getLineSnapshot()).isEqualTo("NONE");
    assertThat(reloaded.getParticipantSnapshot()).isEqualTo("NONE");
    assertThat(reloaded.getRuleSnapshot()).isEqualTo("Regra padrão");
    assertThat(reloaded.getProviderSnapshot()).isEqualTo("MANUAL");
    assertThat(reloaded.getBookmakerSnapshot()).isEqualTo("MANUAL");
    assertThat(reloaded.getExternalFixtureIdSnapshot()).isEqualTo("fixture-" + suffix);
    assertThat(reloaded.getExternalMarketIdSnapshot()).isEqualTo("mkt-snap-" + suffix);
    assertThat(reloaded.getExternalSelectionKeySnapshot()).isEqualTo("sel-snap-" + suffix);
    assertThat(reloaded.getSpecifiersSnapshot()).isEqualTo("NONE");
    assertThat(reloaded.getCompetitionSnapshot()).isEqualTo("Brasileirão Série A");
    assertThat(reloaded.getHomeTeamSnapshot()).isEqualTo("Flamengo");
    assertThat(reloaded.getAwayTeamSnapshot()).isEqualTo("Vasco");
    assertThat(reloaded.getMatchDateSnapshot()).isEqualTo(Instant.parse("2026-11-01T21:00:00Z"));
  }

  @Test
  void ticketItemParentsCannotBeMovedEvenByDirectSql() {
    String suffix = shortId();
    Match match = newMatch(suffix);
    Market market = newMarket(match, "mkt-parent-" + suffix, "NONE");
    MarketSelection selection = newSelection(market, "sel-parent-" + suffix, "HOME", "2.50");
    matchRepository.save(match);
    marketRepository.save(market);
    marketSelectionRepository.save(selection);
    Ticket ticket = newTicket(suffix);
    ticketRepository.save(ticket);
    TicketItem item = newTicketItem(ticket, match, selection);
    ticketItemRepository.save(item);

    Ticket other = newTicket("X" + suffix);
    ticketRepository.save(other);
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.ticket_items SET ticket_id = ? WHERE id = ?",
                    other.getId(),
                    item.getId()))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void operationalMetadataNeverRenewsSelectionVersion() {
    String suffix = shortId();
    Match match = newMatch(suffix);
    Market market = newMarket(match, "mkt-op-" + suffix, "NONE");
    MarketSelection selection = newSelection(market, "sel-op-" + suffix, "HOME", "2.50");
    matchRepository.save(match);
    marketRepository.save(market);
    marketSelectionRepository.save(selection);

    Instant attemptAt = Instant.parse("2026-10-25T12:30:00Z");
    assertThat(marketSelectionRepository.recordRequestAttempt(selection.getId(), attemptAt))
        .isEqualTo(1);

    MarketSelection afterAttempt =
        marketSelectionRepository.findById(selection.getId()).orElseThrow();
    assertThat(afterAttempt.getRequestAttemptAt()).isEqualTo(attemptAt);
    assertThat(afterAttempt.getVersion()).isEqualTo(1L);
  }

  @Test
  void decimalPrecisionSurvivesRoundTrip() {
    String suffix = shortId();
    Match match = newMatch(suffix);
    Market market = newMarket(match, "mkt-dec-" + suffix, "NONE");
    MarketSelection selection = newSelection(market, "sel-dec-" + suffix, "HOME", "2.50");
    matchRepository.save(match);
    marketRepository.save(market);
    marketSelectionRepository.save(selection);

    String totalOddsLiteral = "12345678901234567890.01234567890123456789";
    Ticket ticket = newTicket(suffix);
    Ticket wideTicket =
        new Ticket(
            ticket.getCode(),
            ticket.getRequestKey(),
            "ab".repeat(32),
            "cd".repeat(32),
            "Apostador Teste",
            "5511999999999",
            new BigDecimal("10.00"),
            new BigDecimal(totalOddsLiteral),
            new BigDecimal("25.00"),
            Instant.parse("2026-11-01T22:00:00Z"));
    ticketRepository.save(wideTicket);

    MarketSelection reloaded = marketSelectionRepository.findById(selection.getId()).orElseThrow();
    assertThat(reloaded.getOdds()).isEqualByComparingTo("2.50");
    assertThat(reloaded.getOdds().scale()).isEqualTo(2);

    Ticket reloadedTicket = ticketRepository.findByCode(wideTicket.getCode()).orElseThrow();
    assertThat(reloadedTicket.getTotalOdds()).isEqualByComparingTo(totalOddsLiteral);
    assertThat(reloadedTicket.getTotalOdds().scale()).isEqualTo(20);
    assertThat(reloadedTicket.getStakeAmount()).isEqualByComparingTo("10.00");
    assertThat(reloadedTicket.getPotentialReturn()).isEqualByComparingTo("25.00");
  }

  @Test
  void ticketCodeAndRequestKeyStayUnique() {
    String suffix = shortId();
    Ticket ticket = newTicket(suffix);
    ticketRepository.save(ticket);

    Ticket duplicatedCode =
        new Ticket(
            ticket.getCode(),
            UUID.randomUUID(),
            "ab".repeat(32),
            "cd".repeat(32),
            "Apostador Teste",
            "5511999999999",
            new BigDecimal("10.00"),
            new BigDecimal("2.50"),
            new BigDecimal("25.00"),
            Instant.parse("2026-11-01T22:00:00Z"));
    assertThatThrownBy(() -> ticketRepository.saveAndFlush(duplicatedCode))
        .isInstanceOf(DataIntegrityViolationException.class);

    Ticket duplicatedRequestKey =
        new Ticket(
            "T4F-" + shortId(),
            ticket.getRequestKey(),
            "ab".repeat(32),
            "cd".repeat(32),
            "Apostador Teste",
            "5511999999999",
            new BigDecimal("10.00"),
            new BigDecimal("2.50"),
            new BigDecimal("25.00"),
            Instant.parse("2026-11-01T22:00:00Z"));
    assertThatThrownBy(() -> ticketRepository.saveAndFlush(duplicatedRequestKey))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void auditTimestampsAreStoredAsUtc() {
    String suffix = shortId();
    Match match = newMatch(suffix);
    matchRepository.save(match);

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT created_at, updated_at FROM public.matches WHERE id = ?", match.getId());
    Timestamp stored = (Timestamp) row.get("created_at");
    assertThat(stored.toLocalDateTime())
        .isEqualTo(matchRepository.findById(match.getId()).orElseThrow().getCreatedAt());
    assertThat(jdbc.queryForObject("SHOW TIME ZONE", String.class)).isEqualTo("UTC");
  }

  private static String shortId() {
    return UUID.randomUUID().toString().substring(0, 8);
  }

  private static Match newMatch(String suffix) {
    return new Match(
        "Brasileirão Série A",
        "Flamengo",
        "Vasco",
        Instant.parse("2026-11-01T21:00:00Z"),
        MatchStatus.OPEN,
        "MANUAL",
        "fixture-" + suffix,
        "MANUAL");
  }

  private static Market newMarket(Match match, String externalMarketId, String line) {
    return new Market(
        match,
        "MANUAL",
        "MANUAL",
        externalMarketId,
        "1X2",
        "Resultado Final",
        "FT",
        line,
        "NONE",
        "NONE",
        "standard",
        "Regra padrão",
        "MANUAL");
  }

  private static MarketSelection newSelection(
      Market market, String externalSelectionKey, String selectionCode, String odds) {
    return new MarketSelection(
        market, externalSelectionKey, selectionCode, "Casa", new BigDecimal(odds), "MANUAL");
  }

  private static Ticket newTicket(String suffix) {
    return new Ticket(
        "T4F-" + suffix,
        UUID.randomUUID(),
        "ab".repeat(32),
        "cd".repeat(32),
        "Apostador Teste",
        "5511999999999",
        new BigDecimal("10.00"),
        new BigDecimal("2.50"),
        new BigDecimal("25.00"),
        Instant.parse("2026-11-01T22:00:00Z"));
  }

  private static TicketItem newTicketItem(Ticket ticket, Match match, MarketSelection selection) {
    return new TicketItem(
        ticket,
        match,
        selection,
        selection.getOdds(),
        match.getVersion(),
        selection.getMarket().getVersion(),
        selection.getVersion(),
        selection.getMarket().getDisplayName(),
        selection.getDisplayName(),
        selection.getSelectionCode(),
        selection.getMarket().getPeriod(),
        selection.getMarket().getLine(),
        selection.getMarket().getParticipant(),
        selection.getMarket().getRuleText(),
        selection.getMarket().getProviderStatus(),
        selection.getMarket().getBookmakerId(),
        match.getExternalFixtureId(),
        selection.getMarket().getExternalMarketId(),
        selection.getExternalSelectionKey(),
        selection.getMarket().getSpecifiersKey(),
        match.getCompetition(),
        match.getHomeTeam(),
        match.getAwayTeam(),
        match.getStartsAt());
  }
}
