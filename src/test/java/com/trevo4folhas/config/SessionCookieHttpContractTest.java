package com.trevo4folhas.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import com.trevo4folhas.admin.Admin;
import com.trevo4folhas.admin.AdminRepository;
import com.trevo4folhas.db.TestDb;

/**
 * Effective HTTP contract of the session cookie and CSRF on a real local HTTP test server (RNF-07,
 * PRD 8.5): the {@code Set-Cookie} posture that actually leaves the container, session fixation
 * with pre-login versus post-login ids, and CSRF absent/invalid/valid on the public mutation flow.
 * Real PostgreSQL 16 via Testcontainers (never H2); the database is required only because the
 * Spring context boots with JPA.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionCookieHttpContractTest {

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

  @LocalServerPort private int port;

  @Autowired private AdminRepository adminRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private DataSource dataSource;

  private final HttpClient client =
      HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
  private JdbcTemplate jdbc;

  @BeforeEach
  void seedOperator() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DELETE FROM public.admins");
    adminRepository.save(
        new Admin("ops.active", "Operador Ativo", passwordEncoder.encode(ACTIVE_PASSWORD)));
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
  void loginSetCookieCarriesTheProductionPostureAndTheSessionIdRotatesOnLogin() throws Exception {
    HttpResponse<String> loginPage = get("/admin/login");
    assertThat(loginPage.statusCode()).isEqualTo(200);
    SetCookie preLogin = sessionCookie(loginPage);
    assertThat(preLogin.httpOnly).as("JSESSIONID must be HttpOnly").isTrue();
    assertThat(preLogin.secure).as("base profile must mark JSESSIONID Secure (RNF-07)").isTrue();
    assertThat(preLogin.sameSiteLax).as("JSESSIONID must be SameSite=Lax (RNF-07)").isTrue();
    String csrfToken = extractCsrfToken(loginPage.body());

    HttpResponse<String> login =
        post(
            "/admin/login",
            "username="
                + encode("ops.active")
                + "&password="
                + encode(ACTIVE_PASSWORD)
                + "&_csrf="
                + encode(csrfToken),
            preLogin.value);
    assertThat(login.statusCode()).isEqualTo(302);
    assertThat(login.headers().firstValue("Location").orElse("")).contains("/admin");

    SetCookie postLogin = sessionCookie(login);
    assertThat(postLogin.value)
        .as("session fixation: the id must change between pre-login and post-login")
        .isNotEqualTo(preLogin.value);
    assertThat(postLogin.httpOnly).isTrue();
    assertThat(postLogin.secure).isTrue();
    assertThat(postLogin.sameSiteLax).isTrue();

    HttpResponse<String> home = get("/admin", postLogin.value);
    assertThat(home.statusCode()).isEqualTo(200);
    assertThat(home.body()).contains("Operador Ativo");
  }

  @Test
  void publicFlowCsrfRejectsAbsentAndInvalidTokensAndAcceptsAValidOne() throws Exception {
    // Absent token: no session, no token at all.
    HttpResponse<String> absent = post("/api/tickets", "", null);
    assertThat(absent.statusCode()).isEqualTo(403);
    assertThat(absent.body()).contains("CSRF_INVALID");
    assertThat(absent.headers().firstValue("Cache-Control").orElse("")).contains("no-store");

    // Invalid token: a real session but a forged token.
    HttpResponse<String> page = get("/admin/login");
    String csrfToken = extractCsrfToken(page.body());
    SetCookie session = sessionCookie(page);
    HttpResponse<String> invalid = post("/api/tickets", "_csrf=forged-token-value", session.value);
    assertThat(invalid.statusCode()).isEqualTo(403);
    assertThat(invalid.body()).contains("CSRF_INVALID");
    assertThat(invalid.headers().firstValue("Cache-Control").orElse("")).contains("no-store");

    // Valid token: the security layer must let the request through (the issuance API itself
    // belongs to a later task, so only "not rejected as 401/403" is asserted here).
    HttpResponse<String> valid = post("/api/tickets", "_csrf=" + encode(csrfToken), session.value);
    assertThat(valid.statusCode()).isNotIn(401, 403);
  }

  private HttpResponse<String> get(String path) throws Exception {
    return get(path, null);
  }

  private HttpResponse<String> get(String path, String sessionCookie) throws Exception {
    HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url(path)));
    if (sessionCookie != null) {
      request.header("Cookie", "JSESSIONID=" + sessionCookie);
    }
    return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> post(String path, String formBody, String sessionCookie)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(url(path)))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formBody));
    if (sessionCookie != null) {
      request.header("Cookie", "JSESSIONID=" + sessionCookie);
    }
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private String url(String path) {
    return "http://127.0.0.1:" + port + path;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static SetCookie sessionCookie(HttpResponse<String> response) {
    Optional<String> raw =
        response.headers().allValues("Set-Cookie").stream()
            .filter(value -> value.startsWith("JSESSIONID="))
            .findFirst();
    assertThat(raw).as("response must set the JSESSIONID cookie").isPresent();
    return SetCookie.parse(raw.get());
  }

  private static String extractCsrfToken(String html) {
    Matcher meta = Pattern.compile("name=\"csrf-token\"\\s+content=\"([^\"]+)\"").matcher(html);
    if (meta.find()) {
      return meta.group(1);
    }
    Matcher input = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"").matcher(html);
    if (input.find()) {
      return input.group(1);
    }
    throw new IllegalStateException("CSRF token not rendered in the login page");
  }

  private record SetCookie(String value, boolean httpOnly, boolean secure, boolean sameSiteLax) {

    static SetCookie parse(String raw) {
      String[] parts = raw.split(";");
      String nameValue = parts[0];
      String value = nameValue.substring(nameValue.indexOf('=') + 1);
      boolean httpOnly = false;
      boolean secure = false;
      boolean sameSiteLax = false;
      for (int i = 1; i < parts.length; i++) {
        String attribute = parts[i].trim().toLowerCase();
        if (attribute.equals("httponly")) {
          httpOnly = true;
        } else if (attribute.equals("secure")) {
          secure = true;
        } else if (attribute.equals("samesite=lax")) {
          sameSiteLax = true;
        }
      }
      return new SetCookie(value, httpOnly, secure, sameSiteLax);
    }
  }
}
