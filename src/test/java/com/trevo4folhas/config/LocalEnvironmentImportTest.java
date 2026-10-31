package com.trevo4folhas.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;

/** Real ConfigData loading with synthetic secrets; never reads the developer's .env. */
class LocalEnvironmentImportTest {
  @TempDir Path directory;

  @Configuration(proxyBeanMethods = false)
  static class ProbeConfiguration {}

  private SpringApplication application() {
    var application = new SpringApplication(ProbeConfiguration.class);
    application.setWebApplicationType(WebApplicationType.NONE);
    application.setLogStartupInfo(false);
    application.setRegisterShutdownHook(false);
    return application;
  }

  @Test
  void localProfileResolvesKeyFromExtensionlessFile() throws Exception {
    Path file = directory.resolve(".env");
    Files.writeString(file, "API_FOOTBALL_API_KEY=synthetic-local-key\n");
    try (var context =
        application().run("--spring.profiles.active=local", "--TREVO_ENV_FILE=" + file)) {
      assertThat(context.getEnvironment().getProperty("trevo.sports.api-key"))
          .isEqualTo("synthetic-local-key");
      assertThat(context.getEnvironment().getProperty("trevo.sports.enabled", Boolean.class))
          .isFalse();
      assertThat(context.getEnvironment().getProperty("trevo.sports.sync-enabled", Boolean.class))
          .isFalse();
    }
  }

  @Test
  void explicitRuntimeValueOverridesLocalFile() throws Exception {
    Path file = directory.resolve(".env");
    Files.writeString(file, "API_FOOTBALL_API_KEY=synthetic-file-key\n");
    try (var context =
        application()
            .run(
                "--spring.profiles.active=local",
                "--TREVO_ENV_FILE=" + file,
                "--API_FOOTBALL_API_KEY=synthetic-runtime-key")) {
      assertThat(context.getEnvironment().getProperty("trevo.sports.api-key"))
          .isEqualTo("synthetic-runtime-key");
    }
  }

  @Test
  void missingLocalFileIsOptional() {
    try (var context =
        application()
            .run(
                "--spring.profiles.active=local",
                "--TREVO_ENV_FILE=" + directory.resolve("absent"))) {
      assertThat(context.isActive()).isTrue();
    }
  }

  @Test
  void defaultProfileDoesNotImportLocalFile() throws Exception {
    Path file = directory.resolve(".env");
    Files.writeString(file, "trevo.test.local-marker=loaded\n");
    try (var context = application().run("--TREVO_ENV_FILE=" + file)) {
      assertThat(context.getEnvironment().getProperty("trevo.test.local-marker")).isNull();
    }
  }
}
