package com.trevo4folhas.sports;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CatalogSyncServiceTest {
  @TempDir Path directory;
  private final Instant now = Instant.parse("2026-10-31T03:01:00Z");
  private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
  private final com.fasterxml.jackson.databind.ObjectMapper mapper = SportsTestSupport.mapper();
  private final ImportService importer = mock(ImportService.class);
  private SportsProperties properties;
  private QuotaStore quota;

  @BeforeEach
  void setup() throws Exception {
    Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
    Path path = directory.resolve("quota.json");
    Files.write(
        path,
        mapper.writeValueAsBytes(
            new QuotaStore.State(now.plusSeconds(3600), 0, 10, null, null, null, "NOT_RUN")));
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    properties = SportsTestSupport.properties(path);
    quota = new QuotaStore(properties, mapper, clock);
    when(importer.offer(any())).thenReturn(true);
  }

  private CatalogSyncService service(SportsDataProvider provider) {
    return new CatalogSyncService(
        properties, provider, new ProviderMapping(properties), importer, quota, clock);
  }

  private SportsDataProvider.Page sample(SportsDataProvider.Endpoint endpoint, int total) {
    try {
      return new SportsDataProvider.Page(
          SportsTestSupport.sample(
                  endpoint == SportsDataProvider.Endpoint.FIXTURES ? "fixtures" : "odds", mapper)
              .path("response"),
          total,
          9,
          9);
    } catch (java.io.IOException e) {
      throw new AssertionError(e);
    }
  }

  @Test
  void completeRunProcessesAllPagesOutsideTransactionsAndPersistsCompletion() {
    AtomicInteger calls = new AtomicInteger();
    var service =
        service(
            (endpoint, query, deadline) -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              calls.incrementAndGet();
              return sample(endpoint, endpoint == SportsDataProvider.Endpoint.FIXTURES ? 1 : 2);
            });
    assertThat(service.run().outcome()).isEqualTo("COMPLETE");
    assertThat(calls.get()).isEqualTo(3);
    verify(importer, times(6)).offer(any());
    assertThat(quota.read().lastSuccessAt()).isEqualTo(now);
  }

  @Test
  void interruptedPaginationNeverClaimsCompleteAndDoesNotCloseAbsentRecords() {
    var service =
        service(
            (endpoint, query, deadline) ->
                sample(endpoint, endpoint == SportsDataProvider.Endpoint.FIXTURES ? 1 : 10));
    assertThat(service.run().outcome()).isEqualTo("PAGE_LIMIT");
    assertThat(quota.read().lastSuccessAt()).isNull();
    verify(importer, times(12)).offer(any());
  }

  @Test
  void providerFailureAndQuarantineAreVisibleAndSanitized() {
    var broken =
        service(
            (endpoint, query, deadline) -> {
              throw new SyncFailure("PROVIDER_BODY_ERROR");
            });
    assertThat(broken.run().outcome()).isEqualTo("IMPORT_ERROR");
    assertThat(quota.read().lastSuccessAt()).isNull();
    when(importer.offer(any())).thenReturn(false);
    var partial = service((endpoint, query, deadline) -> sample(endpoint, 1));
    assertThat(partial.run().outcome()).isEqualTo("PARTIAL");
    assertThat(partial.status().message()).doesNotContain("synthetic-test-key");
  }

  @Test
  void concurrentExecutorCannotAcquireRunAndMalformedConfigurationMakesNoCalls() throws Exception {
    SportsDataProvider provider = mock(SportsDataProvider.class);
    var service = service(provider);
    try (var ignored = quota.acquireRun()) {
      assertThatThrownBy(service::run)
          .isInstanceOf(SyncFailure.class)
          .hasMessageContaining("RUN_BUSY");
    }
    verifyNoInteractions(provider);
    var disabled =
        new SportsProperties(
            false, false, null, null, null, null, null, null, 0, 0, null, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            null);
    var blocked =
        new CatalogSyncService(
            disabled, provider, new ProviderMapping(disabled), importer, quota, clock);
    assertThat(blocked.run().outcome()).isEqualTo("DISABLED");
    verifyNoInteractions(provider);
  }

  @Test
  void activeTransactionPreventsNetworkBeforeAnyRequest() {
    SportsDataProvider provider = mock(SportsDataProvider.class);
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      assertThatThrownBy(service(provider)::run)
          .isInstanceOf(SyncFailure.class)
          .hasMessageContaining("TRANSACTION_ACTIVE");
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    verifyNoInteractions(provider);
  }
}
