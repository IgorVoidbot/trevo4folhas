package com.trevo4folhas.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Restart behaviour: migrations applied to an empty PostgreSQL 16 survive a container restart with
 * data intact, and a second migration run is a no-op that validates cleanly, exactly like an
 * application restart.
 */
class MigrationRestartTest {

  @Test
  void migrationsSurviveRestartAndRemainIdempotent() throws InterruptedException {
    PostgreSQLContainer<?> postgres = TestDb.startPostgres16();
    try {
      TestDb.migrate(postgres);
      JdbcTemplate before = TestDb.jdbc(postgres);
      UUID adminId =
          before.queryForObject(
              "INSERT INTO public.admins (username, display_name, password_hash)"
                  + " VALUES ('restart-probe', 'Operador', 'hash') RETURNING id",
              UUID.class);
      Map<String, Object> stored =
          before.queryForMap("SELECT created_at FROM public.admins WHERE id = ?", adminId);
      Timestamp createdAt = (Timestamp) stored.get("created_at");

      TestDb.restart(postgres);

      JdbcTemplate after = TestDb.jdbc(postgres);
      assertThat(after.queryForObject("SHOW TIME ZONE", String.class)).isEqualTo("UTC");
      Map<String, Object> restored =
          after.queryForMap(
              "SELECT created_at, updated_at FROM public.admins WHERE id = ?", adminId);
      assertThat(restored.get("created_at")).isEqualTo(createdAt);

      Flyway flyway = TestDb.configuredFlyway(postgres);
      MigrateResult result = flyway.migrate();
      assertThat(result.migrationsExecuted).isZero();
      flyway.validate();

      after.update(
          "UPDATE public.admins SET display_name = 'Operador revisado' WHERE id = ?", adminId);
      Map<String, Object> updated =
          after.queryForMap(
              "SELECT created_at, updated_at FROM public.admins WHERE id = ?", adminId);
      assertThat(updated.get("created_at")).isEqualTo(createdAt);
      assertThat((Timestamp) updated.get("updated_at"))
          .isAfter((Timestamp) restored.get("updated_at"));
    } finally {
      TestDb.stopQuietly(postgres);
    }
  }
}
