package com.trevo4folhas.db;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.postgresql.Driver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.NetworkSettings;
import com.github.dockerjava.api.model.Ports;

/**
 * Shared fixture for the migration tests. Every test class runs the real migrations against a real
 * PostgreSQL 16 container (never H2) using the same Flyway configuration as the application
 * (technical history in schema {@code t4f_flyway}, domain tables in {@code public}).
 */
final class TestDb {

  static final String FLYWAY_HISTORY_SCHEMA = "t4f_flyway";
  static final String DOMAIN_SCHEMA = "public";
  static final String IMAGE = "postgres:16-alpine";

  private TestDb() {}

  static PostgreSQLContainer<?> startPostgres16() {
    PostgreSQLContainer<?> container =
        new PostgreSQLContainer<>(
            DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"));
    container.start();
    return container;
  }

  static Flyway configuredFlyway(PostgreSQLContainer<?> container) {
    return Flyway.configure()
        .dataSource(utcDataSource(container))
        .defaultSchema(FLYWAY_HISTORY_SCHEMA)
        .schemas(FLYWAY_HISTORY_SCHEMA, DOMAIN_SCHEMA)
        .createSchemas(true)
        .locations("classpath:db/migration")
        .load();
  }

  static void migrate(PostgreSQLContainer<?> container) {
    configuredFlyway(container).migrate();
  }

  /** Each call opens a fresh connection, like a restarted application would. */
  static JdbcTemplate jdbc(PostgreSQLContainer<?> container) {
    return new JdbcTemplate(utcDataSource(container));
  }

  /**
   * Every test connection reproduces the application's per-connection policy (application.yml
   * initialises each Hikari connection with {@code SET TIME ZONE 'UTC'}): connections opened for
   * SQL, for Flyway and again after a real restart all run in UTC, without depending on the
   * container image's default timezone, the host timezone or the session default. The UTC
   * assertions stay literal because the policy, not the environment, guarantees them.
   */
  static DataSource utcDataSource(PostgreSQLContainer<?> container) {
    return new UtcDriverDataSource(
        currentJdbcUrl(container), container.getUsername(), container.getPassword());
  }

  /**
   * Resolves the JDBC endpoint against the container's CURRENT published port. In this runtime a
   * dynamic host port can change when the container is stopped and started again, while the mapping
   * Testcontainers reports keeps pointing at the port captured at startup; the fixture therefore
   * re-obtains the mapping from a live container inspection before every (re)connection instead of
   * trusting a stale one.
   */
  static String currentJdbcUrl(PostgreSQLContainer<?> container) {
    Integer livePort = liveMappedPort(container.getContainerId());
    if (livePort == null) {
      return container.getJdbcUrl();
    }
    return "jdbc:postgresql://"
        + container.getHost()
        + ":"
        + livePort
        + "/"
        + container.getDatabaseName();
  }

  static void restart(PostgreSQLContainer<?> container) throws InterruptedException {
    String containerId = container.getContainerId();
    String before = endpointSnapshot(container, containerId);
    DockerClientFactory.instance().client().stopContainerCmd(containerId).exec();
    DockerClientFactory.instance().client().startContainerCmd(containerId).exec();
    String after = endpointSnapshot(container, containerId);
    System.out.println("[TestDb.restart] endpoint before stop/start: " + before);
    System.out.println("[TestDb.restart] endpoint after stop/start:  " + after);
    awaitJdbc(container, before, after);
  }

  /**
   * Teardown helper: a container that was never assigned (setup failed before start returned) must
   * not cause a NullPointerException in teardown that masks the original failure, and a stop error
   * is reported on stderr instead of being thrown from teardown. Ryuk reaps anything left behind.
   */
  static void stopQuietly(PostgreSQLContainer<?> container) {
    if (container == null) {
      return;
    }
    try {
      container.stop();
    } catch (RuntimeException stopFailure) {
      System.err.println("[TestDb] container teardown failed: " + stopFailure);
    }
  }

  private static Integer liveMappedPort(String containerId) {
    try {
      NetworkSettings network =
          DockerClientFactory.instance()
              .client()
              .inspectContainerCmd(containerId)
              .exec()
              .getNetworkSettings();
      return hostPortOf(network, PostgreSQLContainer.POSTGRESQL_PORT);
    } catch (RuntimeException unavailable) {
      return null;
    }
  }

  private static Integer hostPortOf(NetworkSettings network, int containerPort) {
    Ports published = network.getPorts();
    if (published == null || published.getBindings() == null) {
      return null;
    }
    for (Map.Entry<ExposedPort, Ports.Binding[]> binding : published.getBindings().entrySet()) {
      if (binding.getKey().getPort() == containerPort
          && binding.getValue() != null
          && binding.getValue().length > 0) {
        String hostPort = String.valueOf(binding.getValue()[0].getHostPortSpec());
        if (!hostPort.isEmpty() && !"null".equals(hostPort)) {
          return Integer.valueOf(hostPort.split("-", 2)[0]);
        }
      }
    }
    return null;
  }

  private static void awaitJdbc(PostgreSQLContainer<?> container, String before, String after)
      throws InterruptedException {
    RuntimeException lastFailure = null;
    for (int attempt = 0; attempt < 60; attempt++) {
      try {
        jdbc(container).execute("SELECT 1");
        return;
      } catch (RuntimeException notReady) {
        lastFailure = notReady;
        Thread.sleep(500);
      }
    }
    IllegalStateException failure =
        new IllegalStateException(
            "PostgreSQL did not accept connections after restart; last JDBC failure: "
                + lastFailure
                + " | endpoint before restart: "
                + before
                + " | endpoint after restart: "
                + after);
    failure.initCause(lastFailure);
    throw failure;
  }

  /**
   * Diagnostic view of the published endpoint: what Testcontainers reports (JDBC URL, host, mapped
   * port) next to a live container inspection (state, container IP, published port bindings), plus
   * raw TCP probes against the reported and the live port. Used to compare the endpoint before and
   * after a stop/start without assuming a cause.
   */
  private static String endpointSnapshot(PostgreSQLContainer<?> container, String containerId) {
    String reportedUrl;
    Integer reportedPort;
    String host;
    try {
      reportedUrl = container.getJdbcUrl();
      host = container.getHost();
      reportedPort = container.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
    } catch (RuntimeException unavailable) {
      return "testcontainers[unavailable: " + unavailable + "]";
    }
    Integer livePort;
    String inspection;
    try {
      InspectContainerResponse inspected =
          DockerClientFactory.instance().client().inspectContainerCmd(containerId).exec();
      NetworkSettings network = inspected.getNetworkSettings();
      livePort = hostPortOf(network, PostgreSQLContainer.POSTGRESQL_PORT);
      inspection =
          "inspect[state="
              + inspected.getState().getStatus()
              + ", containerIp="
              + network.getIpAddress()
              + ", published="
              + renderBindings(network.getPorts())
              + "]";
    } catch (RuntimeException unavailable) {
      livePort = null;
      inspection = "inspect[unavailable: " + unavailable + "]";
    }
    return "testcontainers[jdbcUrl="
        + reportedUrl
        + ", host="
        + host
        + ", mappedPort5432="
        + reportedPort
        + "] "
        + inspection
        + " livePort5432="
        + livePort
        + " tcp[reported="
        + tcpProbe(host, reportedPort)
        + ", live="
        + tcpProbe(host, livePort)
        + "]";
  }

  private static String renderBindings(Ports published) {
    if (published == null || published.getBindings() == null) {
      return "none";
    }
    StringBuilder rendered = new StringBuilder("{");
    for (Map.Entry<ExposedPort, Ports.Binding[]> binding : published.getBindings().entrySet()) {
      for (Ports.Binding hostSide : binding.getValue()) {
        rendered
            .append(binding.getKey())
            .append("->")
            .append(String.valueOf(hostSide.getHostIp()))
            .append(':')
            .append(String.valueOf(hostSide.getHostPortSpec()))
            .append(' ');
      }
    }
    return rendered.append('}').toString();
  }

  private static String tcpProbe(String host, Integer port) {
    if (host == null || port == null) {
      return "not-probed";
    }
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), 2000);
      return "connected";
    } catch (IOException notReachable) {
      return "failed(" + notReachable.getMessage() + ")";
    }
  }

  /** SimpleDriverDataSource that pins UTC on every connection it hands out, like Hikari does. */
  private static final class UtcDriverDataSource extends SimpleDriverDataSource {

    UtcDriverDataSource(String url, String username, String password) {
      super(new Driver(), url, username, password);
    }

    @Override
    public Connection getConnection() throws SQLException {
      return withUtc(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
      return withUtc(super.getConnection(username, password));
    }

    private static Connection withUtc(Connection connection) throws SQLException {
      try (Statement statement = connection.createStatement()) {
        statement.execute("SET TIME ZONE 'UTC'");
      } catch (SQLException initializationFailure) {
        try {
          connection.close();
        } catch (SQLException closeFailure) {
          initializationFailure.addSuppressed(closeFailure);
        }
        throw initializationFailure;
      }
      return connection;
    }
  }
}
