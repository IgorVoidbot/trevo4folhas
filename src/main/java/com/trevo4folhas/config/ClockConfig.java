package com.trevo4folhas.config;

import java.time.Clock;
import java.time.ZoneOffset;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the injectable {@link Clock} used by ticket expiry and freshness checks. Tests replace
 * this bean with a fixed clock instead of depending on the system clock.
 */
@Configuration
public class ClockConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC().withZone(ZoneOffset.UTC);
  }
}
