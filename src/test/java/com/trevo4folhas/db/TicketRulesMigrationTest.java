package com.trevo4folhas.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class TicketRulesMigrationTest {
  @Test
  void migrationPreservesExistingTicketsAndIdentifiesLegacyRules() {
    var postgres = TestDb.startPostgres16();
    try {
      Flyway.configure()
          .configuration(TestDb.configuredFlyway(postgres).getConfiguration())
          .target("8")
          .load()
          .migrate();
      var jdbc = TestDb.jdbc(postgres);
      var key = UUID.randomUUID();
      jdbc.update(
          """
          INSERT INTO public.tickets(ticket_code,request_key,request_hash,session_hash,bettor_name,bettor_phone,
            stake_amount,total_odds,potential_return,status,expires_at)
          VALUES('TF-LEGACY',?,repeat('a',64),repeat('b',64),'Pessoa fictícia','5511999999999',10,1.8,18,'PENDING',now()+interval '30 minutes')
          """,
          key);
      var before =
          jdbc.queryForMap(
              "SELECT id,created_at,expires_at,stake_amount,total_odds,potential_return FROM public.tickets");
      assertThat(TestDb.configuredFlyway(postgres).migrate().migrationsExecuted).isEqualTo(1);
      assertThat(
              jdbc.queryForMap(
                  "SELECT id,created_at,expires_at,stake_amount,total_odds,potential_return FROM public.tickets"))
          .isEqualTo(before);
      assertThat(jdbc.queryForObject("SELECT rules_version FROM public.tickets", String.class))
          .isEqualTo("LEGACY_PRE_S3");
      assertThat(TestDb.configuredFlyway(postgres).migrate().migrationsExecuted).isZero();
    } finally {
      TestDb.stopQuietly(postgres);
    }
  }
}
