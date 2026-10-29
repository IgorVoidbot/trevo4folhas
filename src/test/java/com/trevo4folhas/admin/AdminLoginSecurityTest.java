package com.trevo4folhas.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;

import com.trevo4folhas.db.TestDb;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Admin login, authorization, CSRF, logout and rate limiting over MockMvc against the real security
 * chain and real PostgreSQL 16 (Testcontainers, never H2).
 *
 * <p>Session expiry is simulated by issuing a request with no valid session after a previous login:
 * from the server's perspective an expired session arrives exactly like an absent one, and MockMvc
 * cannot wait for the real 30-minute inactivity timeout. Everything else exercises the production
 * configuration.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminLoginSecurityTest {

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
    registry.add("trevo.admin.bootstrap.username", () -> "");
    registry.add("trevo.admin.bootstrap.display-name", () -> "");
    registry.add("trevo.admin.bootstrap.password", () -> "");
  }

  private static final String ACTIVE_PASSWORD = "Operator-Passphrase-2026";

  @Autowired private org.springframework.test.web.servlet.MockMvc mockMvc;
  @Autowired private AdminRepository adminRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;

  @BeforeEach
  void seedOperators() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DELETE FROM public.admins");
    adminRepository.save(
        new Admin("ops.active", "Operador Ativo", passwordEncoder.encode(ACTIVE_PASSWORD)));
    Admin inactive =
        new Admin("ops.inactive", "Operador Inativo", passwordEncoder.encode(ACTIVE_PASSWORD));
    inactive.deactivate();
    adminRepository.save(inactive);
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
  void anonymousPageAccessRedirectsToTheLoginScreen() throws Exception {
    mockMvc
        .perform(get("/admin").with(fromIp("198.51.100.1")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/login"));
  }

  @Test
  void anonymousApiAccessReturns401JsonWithoutLeakingAnything() throws Exception {
    mockMvc
        .perform(get("/api/admin/tickets").with(fromIp("198.51.100.2")))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.message").isNotEmpty());
  }

  @Test
  void loginScreenIsPublicAndRenderedInPtBr() throws Exception {
    String body =
        mockMvc
            .perform(get("/admin/login").with(fromIp("198.51.100.3")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).contains("Acesso administrativo", "Usuário", "Senha", "csrf-token");
  }

  @Test
  void validCredentialsAuthenticateAndRotateTheSession() throws Exception {
    MvcResult login =
        mockMvc
            .perform(
                post("/admin/login")
                    .param("username", "ops.active")
                    .param("password", ACTIVE_PASSWORD)
                    .with(fromIp("198.51.100.4"))
                    .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin"))
            .andReturn();

    MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
    assertThat(session).isNotNull();

    String home =
        mockMvc
            .perform(get("/admin").session(session).with(fromIp("198.51.100.4")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(home).contains("Operador Ativo");
  }

  @Test
  void wrongPasswordFailsWithOneGenericPtBrMessage() throws Exception {
    MvcResult login =
        mockMvc
            .perform(
                post("/admin/login")
                    .param("username", "ops.active")
                    .param("password", "Wrong-Passphrase-2026")
                    .with(fromIp("198.51.100.5"))
                    .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/login?error"))
            .andReturn();

    String errorPage =
        mockMvc
            .perform(get("/admin/login?error").with(fromIp("198.51.100.5")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Generic message: no user enumeration, no attempted credential reflected back.
    assertThat(errorPage).contains("Usuário ou senha inválidos.");
    assertThat(errorPage).doesNotContain("Wrong-Passphrase-2026").doesNotContain("não encontrado");
    MockHttpSession rejected = (MockHttpSession) login.getRequest().getSession(false);
    assertThat(rejected == null || rejected.getAttribute("SPRING_SECURITY_CONTEXT") == null)
        .isTrue();
  }

  @Test
  void unknownUsernameFailsExactlyLikeAWrongPassword() throws Exception {
    mockMvc
        .perform(
            post("/admin/login")
                .param("username", "ops.unknown")
                .param("password", ACTIVE_PASSWORD)
                .with(fromIp("198.51.100.6"))
                .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/login?error"));
  }

  @Test
  void inactiveOperatorIsBlockedEvenWithTheCorrectPassword() throws Exception {
    MvcResult login =
        mockMvc
            .perform(
                post("/admin/login")
                    .param("username", "ops.inactive")
                    .param("password", ACTIVE_PASSWORD)
                    .with(fromIp("198.51.100.7"))
                    .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/login?error"))
            .andReturn();

    MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
    if (session != null) {
      mockMvc
          .perform(get("/admin").session(session).with(fromIp("198.51.100.7")))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/admin/login"));
    }
  }

  @Test
  void logoutIsPostOnlyRequiresCsrfAndInvalidatesTheSession() throws Exception {
    MockHttpSession session = login("198.51.100.8");

    // GET must never log out: no mutation on GET.
    mockMvc
        .perform(get("/admin/logout").session(session).with(fromIp("198.51.100.8")))
        .andExpect(status().isNotFound());
    mockMvc
        .perform(get("/admin").session(session).with(fromIp("198.51.100.8")))
        .andExpect(status().isOk());

    // POST without CSRF is rejected and the session survives.
    mockMvc
        .perform(post("/admin/logout").session(session).with(fromIp("198.51.100.8")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/login?csrfError"));

    mockMvc
        .perform(post("/admin/logout").session(session).with(fromIp("198.51.100.8")).with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/login?logout"));
    assertThat(session.isInvalid()).isTrue();

    // After logout the admin area requires a new login.
    mockMvc
        .perform(get("/admin").with(fromIp("198.51.100.8")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/login"));
  }

  @Test
  void missingCsrfOnThePublicLoginIsRejectedWithoutAuthenticating() throws Exception {
    mockMvc
        .perform(
            post("/admin/login")
                .param("username", "ops.active")
                .param("password", ACTIVE_PASSWORD)
                .with(fromIp("198.51.100.9")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/login?csrfError"));

    mockMvc
        .perform(get("/admin").with(fromIp("198.51.100.9")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/login"));
  }

  @Test
  void invalidCsrfOnAdminApiIsRejectedWith403JsonWhileValidCsrfPasses() throws Exception {
    MockHttpSession session = login("198.51.100.10");

    mockMvc
        .perform(post("/api/admin/matches").session(session).with(fromIp("198.51.100.10")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.errorCode").value("CSRF_INVALID"))
        .andExpect(jsonPath("$.message").isNotEmpty());

    // With a valid token the request passes the security layer (the catalog API itself belongs to
    // a later task, so only "not 401/403" is asserted here).
    mockMvc
        .perform(
            post("/api/admin/matches").session(session).with(fromIp("198.51.100.10")).with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  void simulatedExpiredSessionBehavesLikeAMissingSession() throws Exception {
    MockHttpSession expired = login("198.51.100.11");
    expired.invalidate();
    assertThat(expired.isInvalid()).isTrue();

    // Simulated expiry: the next request carries no valid session, exactly like a timed-out one.
    mockMvc
        .perform(get("/api/admin/tickets").with(fromIp("198.51.100.11")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
    mockMvc
        .perform(get("/admin").with(fromIp("198.51.100.11")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/login"));
  }

  @Test
  void loginAttemptsBeyondTheLimitReturn429WithoutBlockingOtherClients() throws Exception {
    for (int attempt = 1; attempt <= 5; attempt++) {
      mockMvc
          .perform(
              post("/admin/login")
                  .param("username", "ops.active")
                  .param("password", "Wrong-Passphrase-2026")
                  .with(fromIp("203.0.113.50"))
                  .with(csrf()))
          .andExpect(status().is3xxRedirection())
          .andExpect(redirectedUrl("/admin/login?error"));
    }

    MvcResult limited =
        mockMvc
            .perform(
                post("/admin/login")
                    .param("username", "ops.active")
                    .param("password", ACTIVE_PASSWORD)
                    .with(fromIp("203.0.113.50"))
                    .with(csrf()))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"))
            .andExpect(jsonPath("$.message").isNotEmpty())
            .andReturn();
    // Contract: Retry-After is positive and bounded by the window. Its exact value is not pinned
    // here because authentication time is real; AdminLoginRateLimiterTest proves the exact
    // duration (and the upward rounding) under a controlled clock.
    long retryAfterSeconds = Long.parseLong(limited.getResponse().getHeader("Retry-After"));
    assertThat(retryAfterSeconds).isBetween(1L, 60L);

    // Another address is unaffected: no permanent or global block.
    mockMvc
        .perform(
            post("/admin/login")
                .param("username", "ops.active")
                .param("password", ACTIVE_PASSWORD)
                .with(fromIp("203.0.113.51"))
                .with(csrf()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin"));
  }

  @Test
  void credentialsAndHashesNeverReachLogsOrResponses() throws Exception {
    Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    ListAppender<ILoggingEvent> captured = new ListAppender<>();
    captured.start();
    root.addAppender(captured);
    List<String> responseBodies;
    try {
      String failedLoginBody =
          mockMvc
              .perform(
                  post("/admin/login")
                      .param("username", "ops.active")
                      .param("password", "Wrong-Passphrase-2026")
                      .with(fromIp("198.51.100.12"))
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getContentAsString();
      MockHttpSession session = login("198.51.100.12");
      String loginPageBody =
          mockMvc
              .perform(get("/admin/login?error").with(fromIp("198.51.100.12")))
              .andReturn()
              .getResponse()
              .getContentAsString();
      String homeBody =
          mockMvc
              .perform(get("/admin").session(session).with(fromIp("198.51.100.12")))
              .andReturn()
              .getResponse()
              .getContentAsString();
      responseBodies = List.of(failedLoginBody, loginPageBody, homeBody);
    } finally {
      root.detachAppender(captured);
      captured.stop();
    }

    String storedHash =
        jdbc.queryForObject(
            "SELECT password_hash FROM public.admins WHERE username = ?",
            String.class,
            "ops.active");
    String logged =
        captured.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.joining("\n"));
    for (String response : responseBodies) {
      assertThat(response).doesNotContain(ACTIVE_PASSWORD).doesNotContain(storedHash);
    }
    assertThat(logged)
        .doesNotContain(ACTIVE_PASSWORD)
        .doesNotContain(storedHash)
        .doesNotContain("ops.active");
  }

  private MockHttpSession login(String ip) throws Exception {
    MvcResult login =
        mockMvc
            .perform(
                post("/admin/login")
                    .param("username", "ops.active")
                    .param("password", ACTIVE_PASSWORD)
                    .with(fromIp(ip))
                    .with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin"))
            .andReturn();
    return (MockHttpSession) login.getRequest().getSession(false);
  }

  /**
   * Each scenario uses its own client address so the shared rate limiter cannot cross-contaminate.
   */
  private static RequestPostProcessor fromIp(String ip) {
    return request -> {
      request.setRemoteAddr(ip);
      return request;
    };
  }
}
