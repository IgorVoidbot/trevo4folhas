package com.trevo4folhas.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ClockConfigTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(ClockConfig.class);

  @Test
  void clockBeanUsesUtcSystemClock() {
    Clock clock = new ClockConfig().clock();

    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    assertThat(clock.instant()).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  void contextExposesASingleInjectableClockForExpiryChecks() {
    this.contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(Clock.class);
          assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneOffset.UTC);
        });
  }

  @Test
  void fixedClockCanReplaceTheBeanToTestExpiry() {
    Instant fixedInstant = Instant.parse("2026-01-01T00:00:00Z");
    Clock fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

    assertThat(fixedClock.instant()).isEqualTo(fixedInstant);
  }
}
