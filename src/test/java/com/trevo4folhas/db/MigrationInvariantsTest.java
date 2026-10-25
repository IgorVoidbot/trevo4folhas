package com.trevo4folhas.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * SQL-level behaviour of the migrations on a real PostgreSQL 16 container: identity rules, numeric
 * bounds, versions, snapshots, audit timestamps, selection-to-match correspondence and forbidden
 * parent moves/deletes.
 */
class MigrationInvariantsTest {

  private static PostgreSQLContainer<?> postgres;
  private static JdbcTemplate jdbc;

  @BeforeAll
  static void setUp() {
    postgres = TestDb.startPostgres16();
    TestDb.migrate(postgres);
    jdbc = TestDb.jdbc(postgres);
  }

  @AfterAll
  static void tearDown() {
    TestDb.stopQuietly(postgres);
  }

  // ---------------------------------------------------------------- catalog

  @Test
  void completeCatalogAndTicketInsertWithAutomaticAuditTimestamps() {
    UUID matchId = insertMatch(ns(), fx());
    UUID marketId = insertMarket(matchId);
    UUID selectionId = insertSelection(marketId, "SEL-" + shortId());
    UUID ticketId = insertTicket(ticketCode(), "PENDING", null, null);
    insertItem(ticketId, matchId, selectionId);

    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT created_at, updated_at,"
                + " EXTRACT(EPOCH FROM (created_at - (now() AT TIME ZONE 'UTC'))) AS drift"
                + " FROM public.matches WHERE id = ?",
            matchId);
    assertThat(row.get("created_at")).isNotNull();
    assertThat(row.get("updated_at")).isNotNull();
    assertThat(Math.abs(((Number) row.get("drift")).doubleValue())).isLessThan(5.0);
  }

  @Test
  void databaseConnectionsRunInUtc() {
    assertThat(jdbc.queryForObject("SHOW TIME ZONE", String.class)).isEqualTo("UTC");
  }

  // ------------------------------------------------------------- identities

  @Test
  void duplicateTicketCodeAndRequestKeyAreRejected() {
    String code = ticketCode();
    UUID ticketId = insertTicket(code, "PENDING", null, null);
    assertThatThrownBy(() -> insertTicket(code, "PENDING", null, null))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.tickets (ticket_code, request_key, request_hash, session_hash,"
                        + " bettor_name, bettor_phone, stake_amount, total_odds, potential_return, status, expires_at)"
                        + " VALUES (?, ?, ?, ?, 'Apostador', '11999999999', 10.00, 2.00, 20.00, 'PENDING', now())",
                    ticketCode(),
                    jdbc.queryForObject(
                        "SELECT request_key FROM public.tickets WHERE id = ?",
                        UUID.class,
                        ticketId),
                    hexHash(),
                    hexHash()))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void matchExternalIdentityIsUniquePerNamespace() {
    String namespace = ns();
    String fixtureId = fx();
    insertMatch(namespace, fixtureId);
    assertThatThrownBy(() -> insertMatch(namespace, fixtureId))
        .isInstanceOf(DataAccessException.class);
    insertMatch(namespace, fixtureId + "-other");
  }

  @Test
  void marketIdentityIsCompositeAndIgnoresDisplayLabels() {
    UUID matchId = insertMatch(ns(), fx());
    insertMarketWithLabel(matchId, "Primeiro rotulo");
    assertThatThrownBy(() -> insertMarketWithLabel(matchId, "Rotulo diferente"))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void marketIdentityUsesSentinelNoneDistinctFromZero() {
    UUID matchId = insertMatch(ns(), fx());
    // same identity except the line: canonical absence (NONE) and zero (0) are
    // different market instances, never interchangeable.
    UUID none = insertMarketWithLine(matchId, "NONE", "0");
    UUID zero = insertMarketWithLine(matchId, "0", "0");
    assertThat(none).isNotEqualTo(zero);
    assertThatThrownBy(() -> insertMarketWithLine(matchId, "NONE", "0"))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void identityRejectsNullAndEmptyComponents() {
    UUID matchId = insertMatch(ns(), fx());
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.markets (match_id, source_namespace, bookmaker_id,"
                        + " external_market_id, market_type, display_name, period, line, participant,"
                        + " specifiers_key, rule_key, provider_status)"
                        + " VALUES (?, 'ns', '1', '10', '1X2', 'Mercado', 'FULL_TIME', 'NONE', NULL, 'NONE', 'NONE', 'UNKNOWN')",
                    matchId))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.markets (match_id, source_namespace, bookmaker_id,"
                        + " external_market_id, market_type, display_name, period, line, participant,"
                        + " specifiers_key, rule_key, provider_status)"
                        + " VALUES (?, 'ns', '1', '11', '1X2', 'Mercado', 'FULL_TIME', 'NONE', '', 'NONE', 'NONE', 'UNKNOWN')",
                    matchId))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void marketLineMustBeCanonicalDecimalWithoutRedundantZeros() {
    UUID matchId = insertMatch(ns(), fx());
    insertMarketWithLine(matchId, "2.5", "100");
    insertMarketWithLine(matchId, "-1.5", "101");
    assertThatThrownBy(() -> insertMarketWithLine(matchId, "2.50", "102"))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> insertMarketWithLine(matchId, "02", "103"))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> insertMarketWithLine(matchId, "2.", "104"))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> insertMarketWithLine(matchId, "", "105"))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void marketIdentityPersistsRuleKeyAndSpecifiersKeyInTheirOwnColumns() {
    UUID matchId = insertMatch(ns(), fx());
    String ruleKey = "rule-" + shortId();
    String specifiers = "spec-" + shortId();
    UUID marketId =
        insertMarket(matchId, "ns", "1", "10", "FULL_TIME", "NONE", "NONE", ruleKey, specifiers);
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT rule_key, specifiers_key FROM public.markets WHERE id = ?", marketId);
    // rule_key and specifiers_key are different columns of the identity: each
    // bind must land in its own column, never swapped with the other one.
    assertThat(row.get("rule_key")).isEqualTo(ruleKey);
    assertThat(row.get("specifiers_key")).isEqualTo(specifiers);
  }

  @Test
  void specifiersKeyIsLimitedTo1024BytesAndNeverTruncated() {
    UUID matchId = insertMatch(ns(), fx());
    insertMarketWithSpecifiers(matchId, "s".repeat(1024), "200");
    assertThatThrownBy(() -> insertMarketWithSpecifiers(matchId, "s".repeat(1025), "201"))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void marketIdentityByteBudgetKeepsTheCompositeKeyIndexable() {
    UUID matchId = insertMatch(ns(), fx());
    String specifiers = "s".repeat(1024);
    String participant = "p".repeat(255);
    String ruleKey = "r".repeat(255);
    String bookmaker = "b".repeat(255);
    String namespace = "ns";
    String period = "FULL_TIME";
    // 1024 + 255 + 255 + 255 + 244 + 2 + 9 + 4 = 2048 bytes: the largest key
    // the budget accepts. It must be insertable and indexable (duplicate hit).
    String atBudget = "m".repeat(244);
    assertThat(
            octetLength(specifiers)
                + octetLength(participant)
                + octetLength(ruleKey)
                + octetLength(bookmaker)
                + atBudget.length()
                + octetLength(namespace)
                + octetLength(period)
                + octetLength("NONE"))
        .isEqualTo(2048);

    UUID marketId =
        insertMarket(
            matchId,
            namespace,
            bookmaker,
            atBudget,
            period,
            "NONE",
            participant,
            ruleKey,
            specifiers);
    assertThat(marketId).isNotNull();
    // the duplicate is rejected by the identity index itself, proving that the
    // btree can index a key at the maximum admitted byte budget.
    assertThatThrownBy(
            () ->
                insertMarket(
                    matchId,
                    namespace,
                    bookmaker,
                    atBudget,
                    period,
                    "NONE",
                    participant,
                    ruleKey,
                    specifiers))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("uq_markets_identity");

    assertThatThrownBy(
            () ->
                insertMarket(
                    matchId,
                    namespace,
                    bookmaker,
                    "m".repeat(245),
                    period,
                    "NONE",
                    participant,
                    ruleKey,
                    specifiers))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void oddsAreBoundedToTwoDecimalsBetween101And100() {
    UUID marketId = insertMarket(insertMatch(ns(), fx()));
    UUID validLow = insertSelectionWithOdds(marketId, "1.01");
    UUID validHigh = insertSelectionWithOdds(marketId, "100.00");
    assertThat(validLow).isNotEqualTo(validHigh);
    assertThatThrownBy(() -> insertSelectionWithOdds(marketId, "1.00"))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> insertSelectionWithOdds(marketId, "100.01"))
        .isInstanceOf(DataAccessException.class);
    // numeric(5, 2) normalizes on cast; the "at most two decimals" rule is
    // validated in the application before the SQL cast (PRD 8.3).
    BigDecimal rounded = insertSelectionWithOddsReturningOdds(marketId, "2.005");
    assertThat(rounded.scale()).isEqualTo(2);
  }

  @Test
  void monetaryValuesMustBePositiveAndKeepScale() {
    UUID adminId = insertAdmin();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.tickets (ticket_code, request_key, request_hash, session_hash,"
                        + " bettor_name, bettor_phone, stake_amount, total_odds, potential_return, status, expires_at)"
                        + " VALUES (?, gen_random_uuid(), ?, ?, 'Apostador', '11999999999', 0.00, 2.00, 20.00, 'PENDING', now())",
                    ticketCode(),
                    hexHash(),
                    hexHash()))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.tickets (ticket_code, request_key, request_hash, session_hash,"
                        + " bettor_name, bettor_phone, stake_amount, total_odds, potential_return, status, expires_at)"
                        + " VALUES (?, gen_random_uuid(), ?, ?, 'Apostador', '11999999999', 10.00, -2.00, 20.00, 'PENDING', now())",
                    ticketCode(),
                    hexHash(),
                    hexHash()))
        .isInstanceOf(DataAccessException.class);
    UUID ticketId = insertTicket(ticketCode(), "CONFIRMED", adminId, null);
    BigDecimal stake =
        jdbc.queryForObject(
            "SELECT stake_amount FROM public.tickets WHERE id = ?", BigDecimal.class, ticketId);
    assertThat(stake).isEqualByComparingTo("10.00");
    assertThat(stake.scale()).isEqualTo(2);
  }

  @Test
  void versionsMustBePositive() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.matches (competition, home_team, away_team, match_date, status,"
                        + " source_namespace, external_fixture_id, provider_status, version)"
                        + " VALUES ('Serie A', 'Home FC', 'Away FC', now(), 'OPEN', ?, ?, 'UNKNOWN', 0)",
                    ns(),
                    fx()))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.matches (competition, home_team, away_team, match_date, status,"
                        + " source_namespace, external_fixture_id, provider_status, version)"
                        + " VALUES ('Serie A', 'Home FC', 'Away FC', now(), 'OPEN', ?, ?, 'UNKNOWN', -1)",
                    ns(),
                    fx()))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void sha256HashesAreExactlySixtyFourHexChars() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.tickets (ticket_code, request_key, request_hash, session_hash,"
                        + " bettor_name, bettor_phone, stake_amount, total_odds, potential_return, status, expires_at)"
                        + " VALUES (?, gen_random_uuid(), ?, ?, 'Apostador', '11999999999', 10.00, 2.00, 20.00, 'PENDING', now())",
                    ticketCode(),
                    hexHash().substring(1),
                    hexHash()))
        .isInstanceOf(DataAccessException.class);
  }

  // ----------------------------------------------------------- ticket rules

  @Test
  void manualDecisionsRequireOperatorAndTimestamp() {
    UUID adminId = insertAdmin();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.tickets (ticket_code, request_key, request_hash, session_hash,"
                        + " bettor_name, bettor_phone, stake_amount, total_odds, potential_return, status, expires_at)"
                        + " VALUES (?, gen_random_uuid(), ?, ?, 'Apostador', '11999999999', 10.00, 2.00, 20.00, 'CONFIRMED', now())",
                    ticketCode(),
                    hexHash(),
                    hexHash()))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.tickets (ticket_code, request_key, request_hash, session_hash,"
                        + " bettor_name, bettor_phone, stake_amount, total_odds, potential_return, status, expires_at,"
                        + " handled_by, handled_at)"
                        + " VALUES (?, gen_random_uuid(), ?, ?, 'Apostador', '11999999999', 10.00, 2.00, 20.00, 'PENDING', now(), ?, now())",
                    ticketCode(),
                    hexHash(),
                    hexHash(),
                    adminId))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> insertTicket(ticketCode(), "CANCELLED", adminId, null))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> insertTicket(ticketCode(), "CONFIRMED", adminId, "motivo indevido"))
        .isInstanceOf(DataAccessException.class);
    insertTicket(ticketCode(), "CANCELLED", adminId, "Pix nao identificado");
  }

  // ------------------------------------------------------------ snapshots

  @Test
  void ticketItemsAreLimitedToOnePerMatchAndKeepVersionSnapshots() {
    UUID matchId = insertMatch(ns(), fx());
    UUID marketOne = insertMarket(matchId);
    UUID marketTwo = insertMarketWithLine(matchId, "2.5", "300");
    UUID selectionOne = insertSelection(marketOne, "SEL-A-" + shortId());
    UUID selectionTwo = insertSelection(marketTwo, "SEL-B-" + shortId());
    UUID ticketId = insertTicket(ticketCode(), "PENDING", null, null);
    insertItem(ticketId, matchId, selectionOne);
    assertThatThrownBy(() -> insertItem(ticketId, matchId, selectionTwo))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.ticket_items (ticket_id, match_id, market_selection_id,"
                        + " selected_odds, match_version_snapshot, market_version_snapshot,"
                        + " selection_version_snapshot, market_snapshot, selection_snapshot,"
                        + " selection_code_snapshot, period_snapshot, line_snapshot, participant_snapshot,"
                        + " provider_snapshot, bookmaker_snapshot, external_fixture_id_snapshot,"
                        + " external_market_id_snapshot, external_selection_key_snapshot,"
                        + " specifiers_snapshot, competition_snapshot, home_team_snapshot,"
                        + " away_team_snapshot, match_date_snapshot)"
                        + " VALUES (?, ?, ?, 2.00, 1, 1, 0, 'Mercado', 'Selecao', 'HOME', 'FULL_TIME',"
                        + " 'NONE', 'NONE', 'ns', '1', '10', '10', 'SEL', 'NONE', 'Serie A', 'Home FC',"
                        + " 'Away FC', now())",
                    ticketId,
                    matchId,
                    selectionOne))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.ticket_items (ticket_id, match_id, market_selection_id,"
                        + " selected_odds, match_version_snapshot, market_version_snapshot,"
                        + " selection_version_snapshot, market_snapshot, selection_snapshot,"
                        + " selection_code_snapshot, period_snapshot, line_snapshot, participant_snapshot,"
                        + " provider_snapshot, bookmaker_snapshot, external_fixture_id_snapshot,"
                        + " external_market_id_snapshot, external_selection_key_snapshot,"
                        + " specifiers_snapshot, competition_snapshot, home_team_snapshot,"
                        + " away_team_snapshot, match_date_snapshot)"
                        + " VALUES (?, ?, ?, 2.00, 1, 1, 1, 'Mercado', 'Selecao', 'HOME', 'FULL_TIME',"
                        + " '2.50', 'NONE', 'ns', '1', '10', '10', 'SEL', 'NONE', 'Serie A', 'Home FC',"
                        + " 'Away FC', now())",
                    ticketId,
                    matchId,
                    selectionOne))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.ticket_items (ticket_id, match_id, market_selection_id,"
                        + " selected_odds, match_version_snapshot, market_version_snapshot,"
                        + " selection_version_snapshot, market_snapshot, selection_snapshot,"
                        + " selection_code_snapshot, period_snapshot, line_snapshot, participant_snapshot,"
                        + " provider_snapshot, bookmaker_snapshot, external_fixture_id_snapshot,"
                        + " external_market_id_snapshot, external_selection_key_snapshot,"
                        + " specifiers_snapshot, competition_snapshot, home_team_snapshot,"
                        + " away_team_snapshot, match_date_snapshot)"
                        + " VALUES (?, ?, ?, 2.00, 1, 1, 1, 'Mercado', 'Selecao', 'HOME', 'FULL_TIME',"
                        + " 'NONE', 'NONE', 'ns', '1', '10', '10', 'SEL', ?, 'Serie A', 'Home FC',"
                        + " 'Away FC', now())",
                    ticketId,
                    matchId,
                    selectionOne,
                    "x".repeat(1025)))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> insertItem(ticketId, matchId, UUID.randomUUID()))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void selectedItemOddsAreBounded() {
    UUID matchId = insertMatch(ns(), fx());
    UUID selectionId = insertSelection(insertMarket(matchId), "SEL-" + shortId());
    UUID ticketId = insertTicket(ticketCode(), "PENDING", null, null);
    assertThatThrownBy(() -> insertItemWithOdds(ticketId, matchId, selectionId, "0.99"))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> insertItemWithOdds(ticketId, matchId, selectionId, "100.01"))
        .isInstanceOf(DataAccessException.class);
  }

  // -------------------------------------------------------- cross-row rules

  @Test
  void ticketItemMustReferenceTheMatchThatOwnsItsSelection() {
    UUID matchId = insertMatch(ns(), fx());
    UUID otherMatchId = insertMatch(ns(), fx());
    UUID selectionId = insertSelection(insertMarket(matchId), "SEL-" + shortId());
    UUID ticketId = insertTicket(ticketCode(), "PENDING", null, null);
    assertThatThrownBy(() -> insertItem(ticketId, otherMatchId, selectionId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("does not match the match of selection");
    insertItem(ticketId, matchId, selectionId);
  }

  @Test
  void catalogRowsNeverMoveBetweenParents() {
    UUID matchId = insertMatch(ns(), fx());
    UUID otherMatchId = insertMatch(ns(), fx());
    UUID marketId = insertMarket(matchId);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.markets SET match_id = ? WHERE id = ?", otherMatchId, marketId))
        .isInstanceOf(DataAccessException.class);

    UUID selectionId = insertSelection(marketId, "SEL-" + shortId());
    UUID otherMarketId = insertMarketWithLine(otherMatchId, "NONE", "400");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.market_selections SET market_id = ? WHERE id = ?",
                    otherMarketId,
                    selectionId))
        .isInstanceOf(DataAccessException.class);

    UUID secondSelectionId = insertSelection(marketId, "SEL2-" + shortId());
    UUID ticketId = insertTicket(ticketCode(), "PENDING", null, null);
    UUID itemId = insertItem(ticketId, matchId, selectionId);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE public.ticket_items SET market_selection_id = ? WHERE id = ?",
                    secondSelectionId,
                    itemId))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void referencedHistoryCannotBeDeleted() {
    UUID adminId = insertAdmin();
    UUID matchId = insertMatch(ns(), fx());
    UUID marketId = insertMarket(matchId);
    UUID selectionId = insertSelection(marketId, "SEL-" + shortId());
    UUID ticketId = insertTicket(ticketCode(), "CONFIRMED", adminId, null);
    insertItem(ticketId, matchId, selectionId);

    assertThatThrownBy(() -> jdbc.update("DELETE FROM public.admins WHERE id = ?", adminId))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM public.matches WHERE id = ?", matchId))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM public.markets WHERE id = ?", marketId))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(
            () -> jdbc.update("DELETE FROM public.market_selections WHERE id = ?", selectionId))
        .isInstanceOf(DataAccessException.class);
    assertThatThrownBy(() -> jdbc.update("DELETE FROM public.tickets WHERE id = ?", ticketId))
        .isInstanceOf(DataAccessException.class);
  }

  // --------------------------------------------------------------- audit

  @Test
  void updatedAtIsRefreshedAndCreatedAtIsPreservedOnDirectSql() {
    UUID matchId = insertMatch(ns(), fx());
    Map<String, Object> before =
        jdbc.queryForMap("SELECT created_at, updated_at FROM public.matches WHERE id = ?", matchId);
    Timestamp createdAt = (Timestamp) before.get("created_at");
    Timestamp updatedAt = (Timestamp) before.get("updated_at");

    jdbc.execute("SELECT pg_sleep(0.05)");
    jdbc.update(
        "UPDATE public.matches SET created_at = created_at - interval '1 day',"
            + " competition = 'Brasileirao' WHERE id = ?",
        matchId);

    Map<String, Object> after =
        jdbc.queryForMap("SELECT created_at, updated_at FROM public.matches WHERE id = ?", matchId);
    assertThat(after.get("created_at")).isEqualTo(createdAt);
    assertThat((Timestamp) after.get("updated_at")).isAfter(updatedAt);
  }

  // -------------------------------------------------------------- fixtures

  private static String ns() {
    return "ns-" + shortId();
  }

  private static String fx() {
    return "fx-" + shortId();
  }

  private static String shortId() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  private static String ticketCode() {
    return "TF-" + shortId().toUpperCase();
  }

  private static String hexHash() {
    return "ab".repeat(32);
  }

  private static int octetLength(String value) {
    return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
  }

  private UUID insertAdmin() {
    return jdbc.queryForObject(
        "INSERT INTO public.admins (username, display_name, password_hash)"
            + " VALUES (?, 'Operador', 'hash') RETURNING id",
        UUID.class,
        "op-" + shortId());
  }

  private UUID insertMatch(String namespace, String fixtureId) {
    return jdbc.queryForObject(
        "INSERT INTO public.matches (competition, home_team, away_team, match_date, status,"
            + " source_namespace, external_fixture_id, provider_status)"
            + " VALUES ('Serie A', 'Home FC', 'Away FC', now() + interval '1 day', 'OPEN', ?, ?, 'UNKNOWN')"
            + " RETURNING id",
        UUID.class,
        namespace,
        fixtureId);
  }

  private UUID insertMarket(UUID matchId) {
    return insertMarket(
        matchId, "ns-" + shortId(), "1", "10", "FULL_TIME", "NONE", "NONE", "NONE", "NONE");
  }

  private UUID insertMarketWithLabel(UUID matchId, String label) {
    return jdbc.queryForObject(
        "INSERT INTO public.markets (match_id, source_namespace, bookmaker_id, external_market_id,"
            + " market_type, display_name, period, line, participant, specifiers_key, rule_key,"
            + " provider_status) VALUES (?, 'ns', '1', '10', '1X2', ?, 'FULL_TIME', 'NONE', 'NONE',"
            + " 'NONE', 'NONE', 'UNKNOWN') RETURNING id",
        UUID.class,
        matchId,
        label);
  }

  private UUID insertMarketWithLine(UUID matchId, String line, String externalMarketId) {
    return insertMarket(
        matchId, "ns", "1", externalMarketId, "FULL_TIME", line, "NONE", "NONE", "NONE");
  }

  private UUID insertMarketWithSpecifiers(
      UUID matchId, String specifiers, String externalMarketId) {
    return insertMarket(
        matchId, "ns", "1", externalMarketId, "FULL_TIME", "NONE", "NONE", "NONE", specifiers);
  }

  private UUID insertMarket(
      UUID matchId,
      String namespace,
      String bookmaker,
      String externalMarketId,
      String period,
      String line,
      String participant,
      String ruleKey,
      String specifiers) {
    return jdbc.queryForObject(
        "INSERT INTO public.markets (match_id, source_namespace, bookmaker_id, external_market_id,"
            + " market_type, display_name, period, line, participant, specifiers_key, rule_key,"
            + " provider_status) VALUES (?, ?, ?, ?, '1X2', '1X2', ?, ?, ?, ?, ?, 'UNKNOWN')"
            + " RETURNING id",
        UUID.class,
        matchId,
        namespace,
        bookmaker,
        externalMarketId,
        period,
        line,
        participant,
        specifiers,
        ruleKey);
  }

  private UUID insertSelection(UUID marketId, String key) {
    return insertSelectionWithOdds(marketId, key, "2.00");
  }

  private UUID insertSelectionWithOdds(UUID marketId, String odds) {
    return insertSelectionWithOdds(marketId, "SEL-" + shortId(), odds);
  }

  private UUID insertSelectionWithOdds(UUID marketId, String key, String odds) {
    return jdbc.queryForObject(
        "INSERT INTO public.market_selections (market_id, external_selection_key, selection_code,"
            + " display_name, odds, provider_status) VALUES (?, ?, 'HOME', 'Casa', ?, 'UNKNOWN')"
            + " RETURNING id",
        UUID.class,
        marketId,
        key,
        new BigDecimal(odds));
  }

  private BigDecimal insertSelectionWithOddsReturningOdds(UUID marketId, String odds) {
    UUID selectionId = insertSelectionWithOdds(marketId, odds);
    return jdbc.queryForObject(
        "SELECT odds FROM public.market_selections WHERE id = ?", BigDecimal.class, selectionId);
  }

  private UUID insertTicket(String code, String status, UUID adminId, String reason) {
    if ("CONFIRMED".equals(status) || "CANCELLED".equals(status)) {
      return jdbc.queryForObject(
          "INSERT INTO public.tickets (ticket_code, request_key, request_hash, session_hash,"
              + " bettor_name, bettor_phone, stake_amount, total_odds, potential_return, status,"
              + " expires_at, handled_by, handled_at, cancellation_reason)"
              + " VALUES (?, gen_random_uuid(), ?, ?, 'Apostador', '11999999999', 10.00, 2.00, 20.00,"
              + " ?, now() + interval '30 minutes', ?, now(), ?) RETURNING id",
          UUID.class,
          code,
          hexHash(),
          hexHash(),
          status,
          adminId,
          reason);
    }
    return jdbc.queryForObject(
        "INSERT INTO public.tickets (ticket_code, request_key, request_hash, session_hash,"
            + " bettor_name, bettor_phone, stake_amount, total_odds, potential_return, status,"
            + " expires_at) VALUES (?, gen_random_uuid(), ?, ?, 'Apostador', '11999999999', 10.00,"
            + " 2.00, 20.00, ?, now() + interval '30 minutes') RETURNING id",
        UUID.class,
        code,
        hexHash(),
        hexHash(),
        status);
  }

  private UUID insertItem(UUID ticketId, UUID matchId, UUID selectionId) {
    return insertItemWithOdds(ticketId, matchId, selectionId, "2.00");
  }

  private UUID insertItemWithOdds(
      UUID ticketId, UUID matchId, UUID selectionId, String selectedOdds) {
    return jdbc.queryForObject(
        "INSERT INTO public.ticket_items (ticket_id, match_id, market_selection_id, selected_odds,"
            + " match_version_snapshot, market_version_snapshot, selection_version_snapshot,"
            + " market_snapshot, selection_snapshot, selection_code_snapshot, period_snapshot,"
            + " line_snapshot, participant_snapshot, rule_snapshot, provider_snapshot,"
            + " bookmaker_snapshot, external_fixture_id_snapshot, external_market_id_snapshot,"
            + " external_selection_key_snapshot, specifiers_snapshot, competition_snapshot,"
            + " home_team_snapshot, away_team_snapshot, match_date_snapshot)"
            + " VALUES (?, ?, ?, ?, 1, 1, 1, '1X2', 'Casa', 'HOME', 'FULL_TIME', 'NONE', 'NONE',"
            + " 'sem regras', 'ns', '1', '10', '10', 'SEL', 'NONE', 'Serie A', 'Home FC', 'Away FC',"
            + " now() + interval '1 day') RETURNING id",
        UUID.class,
        ticketId,
        matchId,
        selectionId,
        new BigDecimal(selectedOdds));
  }
}
