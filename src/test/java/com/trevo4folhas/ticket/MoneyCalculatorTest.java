package com.trevo4folhas.ticket;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.util.*;

import org.junit.jupiter.api.*;

import com.trevo4folhas.config.BettingProperties;

class MoneyCalculatorTest {
  final MoneyCalculator calculator = new MoneyCalculator();
  final BettingProperties limits = new BettingProperties();

  @BeforeEach
  void setup() {
    limits.setMinStakeAmount(new BigDecimal("0.01"));
    limits.setMaxStakeAmount(new BigDecimal("500.00"));
    limits.setMaxSelections(10);
    limits.setMaxPotentialReturn(new BigDecimal("10000.00"));
  }

  @Test
  void preservesFullProductAndRoundsOnlyFinalHalfCent() {
    var result =
        calculator.calculate(
            new BigDecimal("10.00"),
            List.of(new BigDecimal("1.80"), new BigDecimal("2.50")),
            limits);
    assertThat(result.totalOdds()).isEqualByComparingTo("4.50");
    assertThat(result.potentialReturn()).isEqualByComparingTo("45.00");
    var half =
        calculator.calculate(
            new BigDecimal("1.00"),
            List.of(new BigDecimal("1.01"), new BigDecimal("1.50")),
            limits);
    assertThat(half.totalOdds()).isEqualByComparingTo("1.5150");
    assertThat(half.potentialReturn()).isEqualByComparingTo("1.52");
    var ten =
        calculator.calculate(
            new BigDecimal("10.00"), Collections.nCopies(10, new BigDecimal("1.01")), limits);
    assertThat(ten.totalOdds()).isEqualByComparingTo("1.10462212541120451001");
  }

  @Test
  void rejectsStakePrecisionBoundsSelectionCountAndReturnCap() {
    for (String value : List.of("0", "500.01", "1.001"))
      assertThatThrownBy(
              () ->
                  calculator.calculate(
                      new BigDecimal(value), List.of(new BigDecimal("1.80")), limits))
          .isInstanceOf(TicketException.class);
    assertThatThrownBy(() -> calculator.calculate(new BigDecimal("10"), List.of(), limits))
        .isInstanceOf(TicketException.class);
    assertThatThrownBy(
            () ->
                calculator.calculate(
                    new BigDecimal("10"), Collections.nCopies(11, new BigDecimal("1.01")), limits))
        .isInstanceOf(TicketException.class);
    assertThatThrownBy(
            () ->
                calculator.calculate(new BigDecimal("500"), List.of(new BigDecimal("100")), limits))
        .isInstanceOf(TicketException.class);
    for (String value : List.of("1.001", "1.00", "100.01"))
      assertThatThrownBy(
              () ->
                  calculator.calculate(
                      new BigDecimal("10"), List.of(new BigDecimal(value)), limits))
          .isInstanceOf(TicketException.class);
  }
}
