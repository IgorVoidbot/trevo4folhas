package com.trevo4folhas.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Unit tests for the validated environment configuration. A configuration outside the MVP ranges
 * must fail the startup instead of being silently accepted (fail-closed).
 */
class BettingPropertiesTest {

  private static final String MIN_STAKE = "trevo.betting.min-stake-amount=";
  private static final String MAX_STAKE = "trevo.betting.max-stake-amount=";
  private static final String MAX_SELECTIONS = "trevo.betting.max-selections=";
  private static final String MAX_POTENTIAL_RETURN = "trevo.betting.max-potential-return=";
  private static final String VALIDITY_MINUTES = "trevo.betting.ticket-validity-minutes=";

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(TestPropertiesConfiguration.class);

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(BettingProperties.class)
  static class TestPropertiesConfiguration {}

  @Test
  void bindsAndValidatesRegisteredDevLimits() {
    this.contextRunner
        .withPropertyValues(validValues())
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              BettingProperties properties = context.getBean(BettingProperties.class);
              assertThat(properties.getMinStakeAmount()).isEqualByComparingTo("5.00");
              assertThat(properties.getMaxStakeAmount()).isEqualByComparingTo("500.00");
              assertThat(properties.getMaxSelections()).isEqualTo(10);
              assertThat(properties.getMaxPotentialReturn()).isEqualByComparingTo("10000.00");
              assertThat(properties.getTicketValidityMinutes()).isEqualTo(30);
            });
  }

  @Test
  void rejectsMissingLimits() {
    this.contextRunner.run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsMaxSelectionsAboveMvpCeiling() {
    String[] values = validValues();
    values[2] = MAX_SELECTIONS + "11";
    this.contextRunner.withPropertyValues(values).run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsNonPositiveStake() {
    String[] values = validValues();
    values[0] = MIN_STAKE + "0.00";
    this.contextRunner.withPropertyValues(values).run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsMinimumStakeAboveMaximumStake() {
    String[] values = validValues();
    values[0] = MIN_STAKE + "600.00";
    this.contextRunner.withPropertyValues(values).run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsTicketValidityAboveMvpCeiling() {
    String[] values = validValues();
    values[4] = VALIDITY_MINUTES + "31";
    this.contextRunner.withPropertyValues(values).run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsMonetaryValuesWithExcessDecimalPlaces() {
    String[] values = validValues();
    values[3] = MAX_POTENTIAL_RETURN + "10000.005";
    this.contextRunner.withPropertyValues(values).run(context -> assertThat(context).hasFailed());
  }

  private static String[] validValues() {
    return new String[] {
      MIN_STAKE + "5.00",
      MAX_STAKE + "500.00",
      MAX_SELECTIONS + "10",
      MAX_POTENTIAL_RETURN + "10000.00",
      VALIDITY_MINUTES + "30"
    };
  }
}
