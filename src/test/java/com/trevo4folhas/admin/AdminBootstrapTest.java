package com.trevo4folhas.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import com.trevo4folhas.db.TestDb;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Controlled bootstrap of operator accounts (PRD RF-16) against real PostgreSQL 16 (Testcontainers,
 * never H2). The environment credential has no default value: without it nothing is created, a
 * partial configuration fails closed, a recurrent deployment never resets or duplicates an account,
 * and neither the raw password, the BCrypt hash nor the username ever reaches a log.
 */
@SpringBootTest
class AdminBootstrapTest {

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
    // Hermetic: bootstrap must depend only on these tests, never on host environment variables.
    registry.add("trevo.admin.bootstrap.username", () -> "");
    registry.add("trevo.admin.bootstrap.display-name", () -> "");
    registry.add("trevo.admin.bootstrap.password", () -> "");
  }

  @Autowired private AdminBootstrapService adminBootstrapService;
  @Autowired private AdminBootstrapProperties bootstrapProperties;
  @Autowired private AdminRepository adminRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;

  @BeforeEach
  void resetState() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DELETE FROM public.admins");
    bootstrapProperties.setUsername("");
    bootstrapProperties.setDisplayName("");
    bootstrapProperties.setPassword("");
  }

  @AfterEach
  void cleanUp() {
    jdbc.execute("DELETE FROM public.admins");
  }

  @AfterAll
  static void stopContainer() {
    TestDb.stopQuietly(POSTGRES);
  }

  @Test
  void startupWithoutEnvironmentCredentialsCreatesNothing() {
    // The application context already started with empty bootstrap values: no hidden operator,
    // no default password anywhere.
    adminBootstrapService.bootstrap();

    assertThat(adminRepository.count()).isZero();
    assertThat(bootstrapProperties.getPassword()).isEmpty();
  }

  @Test
  void validEnvironmentCredentialsCreateOneOperatorWithABcryptHash() {
    String rawPassword = "Bootstrap-Passphrase-2026";
    bootstrapProperties.setUsername("ops.first");
    bootstrapProperties.setDisplayName("Operador Principal");
    bootstrapProperties.setPassword(rawPassword);

    adminBootstrapService.bootstrap();

    Admin created = adminRepository.findByUsername("ops.first").orElseThrow();
    String storedHash = storedHash("ops.first");
    assertThat(created.getDisplayName()).isEqualTo("Operador Principal");
    assertThat(created.isActive()).isTrue();
    assertThat(storedHash).isNotEqualTo(rawPassword).startsWith("$2");
    assertThat(passwordEncoder.matches(rawPassword, storedHash)).isTrue();
  }

  @Test
  void samePasswordForTwoOperatorsStillGetsDistinctSalts() {
    String rawPassword = "Bootstrap-Passphrase-2026";
    bootstrapProperties.setUsername("ops.one");
    bootstrapProperties.setPassword(rawPassword);
    adminBootstrapService.bootstrap();

    bootstrapProperties.setUsername("ops.two");
    bootstrapProperties.setPassword(rawPassword);
    adminBootstrapService.bootstrap();

    assertThat(storedHash("ops.one")).isNotEqualTo(storedHash("ops.two"));
  }

  @Test
  void recurrentBootstrapNeverResetsOrDuplicatesAnExistingOperator() {
    String originalPassword = "Bootstrap-Passphrase-2026";
    bootstrapProperties.setUsername("ops.first");
    bootstrapProperties.setPassword(originalPassword);
    adminBootstrapService.bootstrap();
    String originalHash = storedHash("ops.first");

    // A redeploy must not rebuild a known credential from a permanent bootstrap secret.
    bootstrapProperties.setPassword("Another-Passphrase-2026");
    adminBootstrapService.bootstrap();

    assertThat(adminRepository.count()).isEqualTo(1);
    assertThat(storedHash("ops.first")).isEqualTo(originalHash);
    assertThat(passwordEncoder.matches(originalPassword, originalHash)).isTrue();
  }

  @Test
  void weakPasswordIsRejectedWithoutCreatingAnyAccount() {
    bootstrapProperties.setUsername("ops.first");
    bootstrapProperties.setPassword("short");

    assertThatThrownBy(() -> adminBootstrapService.bootstrap())
        .isInstanceOf(IllegalStateException.class);

    assertThat(adminRepository.count()).isZero();
  }

  @Test
  void partialConfigurationFailsClosedInsteadOfStartingHalfConfigured() {
    bootstrapProperties.setPassword("Bootstrap-Passphrase-2026");
    assertThatThrownBy(() -> adminBootstrapService.bootstrap())
        .isInstanceOf(IllegalStateException.class);

    bootstrapProperties.setPassword("");
    bootstrapProperties.setUsername("ops.first");
    assertThatThrownBy(() -> adminBootstrapService.bootstrap())
        .isInstanceOf(IllegalStateException.class);

    assertThat(adminRepository.count()).isZero();
  }

  @Test
  void bootstrapLogsNeverContainUsernamePasswordOrHash() {
    String rawPassword = "Bootstrap-Passphrase-2026";
    bootstrapProperties.setUsername("ops.first");
    bootstrapProperties.setPassword(rawPassword);

    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    ListAppender<ILoggingEvent> captured = new ListAppender<>();
    captured.start();
    root.addAppender(captured);
    try {
      adminBootstrapService.bootstrap();
    } finally {
      root.detachAppender(captured);
      captured.stop();
    }

    String logged =
        captured.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.joining("\n"));
    List<String> loggedMessages =
        captured.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    assertThat(loggedMessages).isNotEmpty(); // the creation event itself is recorded
    assertThat(logged).doesNotContain(rawPassword).doesNotContain("ops.first");
    assertThat(logged).doesNotContain(storedHash("ops.first"));
  }

  private String storedHash(String username) {
    return jdbc.queryForObject(
        "SELECT password_hash FROM public.admins WHERE username = ?", String.class, username);
  }
}
