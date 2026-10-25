package com.trevo4folhas.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Catalog-level checks of the Flyway migrations on a real PostgreSQL 16 container: objects, types,
 * defaults, constraints, triggers and indexes.
 */
class MigrationCatalogTest {

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

  @Test
  void runsOnRealPostgresql16() {
    String version = jdbc.queryForObject("SHOW server_version", String.class);
    assertThat(version).startsWith("16.");
  }

  @Test
  void domainSchemaHasExactlyTheSixTables() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables"
                + " WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name",
            String.class);
    assertThat(tables)
        .containsExactly(
            "admins", "market_selections", "markets", "matches", "ticket_items", "tickets");
  }

  @Test
  void flywayTechnicalHistoryLivesOutsideTheDomainSchema() {
    String schema =
        jdbc.queryForList(
                "SELECT table_schema FROM information_schema.tables WHERE table_name = 'flyway_schema_history'",
                String.class)
            .get(0);
    assertThat(schema).isEqualTo(TestDb.FLYWAY_HISTORY_SCHEMA).isNotEqualTo(TestDb.DOMAIN_SCHEMA);
  }

  @Test
  void auditColumnsAreTimestampNotNullWithDefaultOnAllSixTables() {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT table_name, column_name, data_type, is_nullable, column_default"
                + " FROM information_schema.columns"
                + " WHERE table_schema = 'public' AND column_name IN ('created_at', 'updated_at')"
                + " ORDER BY table_name, column_name");
    assertThat(rows).hasSize(12);
    for (Map<String, Object> row : rows) {
      assertThat(row.get("data_type")).isEqualTo("timestamp without time zone");
      assertThat(row.get("is_nullable")).isEqualTo("NO");
      assertThat(String.valueOf(row.get("column_default")))
          .containsIgnoringCase("CURRENT_TIMESTAMP");
    }
  }

  @Test
  void everyTableHasAnUpdatedAtTrigger() {
    List<String> triggers =
        jdbc.queryForList(
            "SELECT c.relname || ':' || t.tgname"
                + " FROM pg_trigger t"
                + " JOIN pg_class c ON c.oid = t.tgrelid"
                + " JOIN pg_namespace n ON n.oid = c.relnamespace"
                + " WHERE n.nspname = 'public' AND NOT t.tgisinternal"
                + " AND t.tgname LIKE 'trg\\_%\\_updated_at' ESCAPE '\\'"
                + " ORDER BY 1",
            String.class);
    assertThat(triggers)
        .containsExactly(
            "admins:trg_admins_updated_at",
            "market_selections:trg_market_selections_updated_at",
            "markets:trg_markets_updated_at",
            "matches:trg_matches_updated_at",
            "ticket_items:trg_ticket_items_updated_at",
            "tickets:trg_tickets_updated_at");
  }

  @Test
  void everyForeignKeyIsOnDeleteRestrict() {
    List<Map<String, Object>> foreignKeys =
        jdbc.queryForList(
            "SELECT rel.relname AS table_name, con.conname, con.confdeltype"
                + " FROM pg_constraint con"
                + " JOIN pg_class rel ON rel.oid = con.conrelid"
                + " JOIN pg_namespace n ON n.oid = rel.relnamespace"
                + " WHERE n.nspname = 'public' AND con.contype = 'f' ORDER BY con.conname");
    assertThat(foreignKeys)
        .extracting(fk -> String.valueOf(fk.get("conname")))
        .containsExactly(
            "fk_market_selections_market",
            "fk_markets_match",
            "fk_ticket_items_market_selection",
            "fk_ticket_items_match",
            "fk_ticket_items_ticket",
            "fk_tickets_handled_by");
    assertThat(foreignKeys).allSatisfy(fk -> assertThat(fk.get("confdeltype")).isEqualTo("r"));
  }

  @Test
  void uniqueConstraintsCoverTicketCodeRequestKeyAndCompositeIdentities() {
    List<String> uniques =
        jdbc.queryForList(
            "SELECT con.conname FROM pg_constraint con"
                + " JOIN pg_namespace n ON n.oid = con.connamespace"
                + " WHERE n.nspname = 'public' AND con.contype = 'u' ORDER BY 1",
            String.class);
    assertThat(uniques)
        .contains(
            "uq_admins_username",
            "uq_matches_source_identity",
            "uq_markets_identity",
            "uq_market_selections_identity",
            "uq_tickets_ticket_code",
            "uq_tickets_request_key",
            "uq_ticket_items_ticket_match");
  }

  @Test
  void marketIdentityIndexIsCoveredByMatchIdPrefixInCanonicalOrder() {
    List<String> columns =
        jdbc.queryForList(
            "SELECT a.attname"
                + " FROM pg_index i"
                + " JOIN unnest(i.indkey) WITH ORDINALITY AS k(attnum, ord) ON TRUE"
                + " JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = k.attnum"
                + " JOIN pg_class ic ON ic.oid = i.indexrelid"
                + " WHERE ic.relname = 'uq_markets_identity' ORDER BY k.ord",
            String.class);
    assertThat(columns)
        .containsExactly(
            "match_id",
            "source_namespace",
            "bookmaker_id",
            "external_market_id",
            "period",
            "line",
            "participant",
            "rule_key",
            "specifiers_key");
  }

  @Test
  void expectedIndexesExistWithoutRedundantDuplicates() {
    List<String> indexes =
        jdbc.queryForList(
            "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' ORDER BY indexname",
            String.class);
    assertThat(indexes)
        .contains(
            "idx_matches_match_date",
            "idx_ticket_items_match_id",
            "idx_ticket_items_market_selection_id",
            "idx_tickets_expires_at",
            "idx_tickets_handled_by",
            "idx_tickets_status_created_at");
    // ticket_code lookup is served by its UNIQUE constraint index; the FK
    // markets(match_id) is served by the uq_markets_identity prefix and
    // market_selections(market_id) by uq_market_selections_identity.
    assertThat(indexes)
        .doesNotContain(
            "idx_tickets_ticket_code",
            "idx_markets_match_id",
            "idx_market_selections_market_id",
            "idx_ticket_items_ticket_id");
  }

  @Test
  void numericColumnsKeepDocumentedPrecisionAndScale() {
    assertThat(numericSpec("market_selections", "odds")).isEqualTo("numeric:5:2");
    assertThat(numericSpec("ticket_items", "selected_odds")).isEqualTo("numeric:5:2");
    assertThat(numericSpec("tickets", "stake_amount")).isEqualTo("numeric:14:2");
    assertThat(numericSpec("tickets", "potential_return")).isEqualTo("numeric:14:2");
    assertThat(numericSpec("tickets", "total_odds")).isEqualTo("numeric:41:20");
  }

  @Test
  void identityAndLabelColumnsKeepDocumentedLengths() {
    assertThat(spec("tickets", "ticket_code")).isEqualTo("character varying:32");
    assertThat(spec("tickets", "request_hash")).isEqualTo("character:64");
    assertThat(spec("tickets", "session_hash")).isEqualTo("character:64");
    assertThat(spec("tickets", "bettor_name")).isEqualTo("character varying:100");
    assertThat(spec("tickets", "bettor_phone")).isEqualTo("character varying:15");
    assertThat(spec("tickets", "cancellation_reason")).isEqualTo("character varying:250");
    assertThat(spec("tickets", "operator_note")).isEqualTo("character varying:500");
    assertThat(spec("matches", "competition")).isEqualTo("character varying:120");
    assertThat(spec("matches", "status")).isEqualTo("character varying:16");
    assertThat(spec("markets", "period")).isEqualTo("character varying:64");
    assertThat(spec("markets", "line")).isEqualTo("character varying:64");
    assertThat(spec("markets", "rule_key")).isEqualTo("character varying:255");
    assertThat(spec("market_selections", "selection_code")).isEqualTo("character varying:64");
    assertThat(spec("market_selections", "external_selection_key"))
        .isEqualTo("character varying:255");
  }

  @Test
  void businessDatesAreTimestamptzAndVersionsAreBigIntNotNull() {
    for (String column : List.of("match_date")) {
      assertThat(spec("matches", column)).isEqualTo("timestamp with time zone:-");
    }
    assertThat(spec("tickets", "expires_at")).isEqualTo("timestamp with time zone:-");
    assertThat(spec("tickets", "handled_at")).isEqualTo("timestamp with time zone:-");
    assertThat(spec("ticket_items", "match_date_snapshot")).isEqualTo("timestamp with time zone:-");
    assertThat(spec("market_selections", "provider_updated_at"))
        .isEqualTo("timestamp with time zone:-");
    for (String table : List.of("matches", "markets", "market_selections", "tickets")) {
      assertThat(spec(table, "version")).isEqualTo("bigint:-");
      assertThat(isNullable(table, "version")).isEqualTo("NO");
    }
    for (String column :
        List.of(
            "match_version_snapshot", "market_version_snapshot", "selection_version_snapshot")) {
      assertThat(spec("ticket_items", column)).isEqualTo("bigint:-");
      assertThat(isNullable("ticket_items", column)).isEqualTo("NO");
    }
  }

  @Test
  void nullableFreshnessMetadataIsExplicitlyUnset() {
    for (String table : List.of("matches", "markets", "market_selections")) {
      for (String column :
          List.of(
              "request_attempt_at",
              "last_successful_sync_at",
              "provider_updated_at",
              "manual_reviewed_at")) {
        assertThat(spec(table, column)).isEqualTo("timestamp with time zone:-");
        assertThat(isNullable(table, column)).isEqualTo("YES");
      }
    }
  }

  private String numericSpec(String table, String column) {
    return jdbc.queryForObject(
        "SELECT data_type || ':' || numeric_precision || ':' || numeric_scale"
            + " FROM information_schema.columns"
            + " WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
        String.class,
        table,
        column);
  }

  private String spec(String table, String column) {
    return jdbc.queryForObject(
        "SELECT data_type || ':' || COALESCE(character_maximum_length::text, '-')"
            + " FROM information_schema.columns"
            + " WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
        String.class,
        table,
        column);
  }

  private String isNullable(String table, String column) {
    return jdbc.queryForObject(
        "SELECT is_nullable FROM information_schema.columns"
            + " WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
        String.class,
        table,
        column);
  }
}
