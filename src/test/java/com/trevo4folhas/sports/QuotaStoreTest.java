package com.trevo4folhas.sports;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class QuotaStoreTest {
  @TempDir Path directory;
  private final Instant now = Instant.parse("2026-10-31T03:01:00Z");
  private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
  private Path path;
  private QuotaStore store;

  @BeforeEach
  void setup() throws Exception {
    Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
    path = directory.resolve("quota.json");
    Files.write(
        path,
        SportsTestSupport.mapper()
            .writeValueAsBytes(
                new QuotaStore.State(now.plusSeconds(3600), 0, 10, null, null, null, "NOT_RUN")));
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    store = new QuotaStore(SportsTestSupport.properties(path), SportsTestSupport.mapper(), clock);
  }

  @Test
  void reservationsSurviveRestartAndPreserveReserve() {
    for (int i = 0; i < 8; i++) store.reserve();
    var restarted =
        new QuotaStore(SportsTestSupport.properties(path), SportsTestSupport.mapper(), clock);
    assertThat(restarted.read().used()).isEqualTo(8);
    assertThatThrownBy(restarted::reserve)
        .isInstanceOf(SyncFailure.class)
        .hasMessageContaining("QUOTA_EXHAUSTED");
    assertThat(restarted.read().providerRemaining()).isEqualTo(2);
  }

  @Test
  void corruptMissingInsecureAndExpiredStateFailClosed() throws Exception {
    Files.writeString(path, "broken");
    assertThatThrownBy(store::reserve).isInstanceOf(SyncFailure.class);
    Files.delete(path);
    assertThatThrownBy(store::reserve).isInstanceOf(SyncFailure.class);
    setup();
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-r--r--"));
    assertThatThrownBy(store::reserve).isInstanceOf(SyncFailure.class);
    setup();
    var expired =
        new QuotaStore(
            SportsTestSupport.properties(path),
            SportsTestSupport.mapper(),
            Clock.fixed(now.plusSeconds(3600), ZoneOffset.UTC));
    assertThatThrownBy(expired::reserve)
        .isInstanceOf(SyncFailure.class)
        .hasMessageContaining("QUOTA_RESET_UNVERIFIED");
  }

  @Test
  void serverRemainingNeverIncreasesAndRetryAfterSurvivesRestart() {
    store.reserve();
    store.observe(3, 1, null);
    store.observe(100, 100, null);
    assertThat(store.read().providerRemaining()).isEqualTo(3);
    store.observe(3, 0, null);
    assertThatThrownBy(store::reserve)
        .isInstanceOf(SyncFailure.class)
        .hasMessageContaining("RATE_LIMIT_WAIT");
    store.finish("PARTIAL", false);
    assertThat(store.read().lastSuccessAt()).isNull();
    store.finish("COMPLETE", true);
    assertThat(store.read().lastSuccessAt()).isEqualTo(now);
  }

  @Test
  void runLockExcludesOtherExecutorsAndIsReleased() throws Exception {
    var second =
        new QuotaStore(SportsTestSupport.properties(path), SportsTestSupport.mapper(), clock);
    try (var lock = store.acquireRun()) {
      assertThatThrownBy(second::acquireRun)
          .isInstanceOf(SyncFailure.class)
          .hasMessageContaining("RUN_BUSY");
    }
    try (var lock = second.acquireRun()) {
      assertThat(lock.lock().isValid()).isTrue();
    }
  }

  @Test
  void symbolicLinksCannotRedirectQuotaWrites() throws Exception {
    Path real = directory.resolve("original");
    Files.move(path, real);
    Files.createSymbolicLink(path, real);
    assertThatThrownBy(store::reserve).isInstanceOf(SyncFailure.class);
  }
}
