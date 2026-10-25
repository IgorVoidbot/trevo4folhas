package com.trevo4folhas.db;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Regression for secondary teardown failures: when container setup fails before the container is
 * assigned (for example during Testcontainers/Ryuk initialization), the test teardown must not
 * throw a NullPointerException that masks the original setup error.
 */
class TestDbTeardownTest {

  @Test
  void teardownAfterFailedSetupDoesNotThrowNullPointer() {
    assertThatCode(() -> TestDb.stopQuietly(null)).doesNotThrowAnyException();
  }

  @Test
  void teardownAfterSetupThatNeverStartedAContainerDoesNotThrow() {
    PostgreSQLContainer<?> neverStarted =
        new PostgreSQLContainer<>(
            DockerImageName.parse(TestDb.IMAGE).asCompatibleSubstituteFor("postgres"));
    assertThatCode(() -> TestDb.stopQuietly(neverStarted)).doesNotThrowAnyException();
  }
}
