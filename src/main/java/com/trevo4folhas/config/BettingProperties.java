package com.trevo4folhas.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Server-side commercial limits bound from the environment and validated at startup. Invalid
 * configuration prevents the application from starting; the browser is never trusted for these
 * values.
 */
@Validated
@ConfigurationProperties(prefix = "trevo.betting")
public class BettingProperties {

  /** MVP ceiling for selections per ticket until total_odds precision is reviewed. */
  public static final int MAX_SELECTIONS_CEILING = 10;

  /** MVP ceiling for ticket validity in minutes. */
  public static final int MAX_TICKET_VALIDITY_MINUTES = 30;

  @NotNull(message = "A aposta mínima é obrigatória.")
  @Positive(message = "A aposta mínima deve ser positiva.")
  @Digits(
      integer = 12,
      fraction = 2,
      message = "A aposta mínima deve ter no máximo duas casas decimais.")
  private BigDecimal minStakeAmount;

  @NotNull(message = "A aposta máxima é obrigatória.")
  @Positive(message = "A aposta máxima deve ser positiva.")
  @Digits(
      integer = 12,
      fraction = 2,
      message = "A aposta máxima deve ter no máximo duas casas decimais.")
  private BigDecimal maxStakeAmount;

  @NotNull(message = "O limite de seleções é obrigatório.")
  @Min(value = 1, message = "O limite de seleções deve ser pelo menos 1.")
  @Max(value = MAX_SELECTIONS_CEILING, message = "O limite de seleções não pode exceder 10.")
  private Integer maxSelections;

  @NotNull(message = "O teto de retorno potencial é obrigatório.")
  @Positive(message = "O teto de retorno potencial deve ser positivo.")
  @Digits(
      integer = 12,
      fraction = 2,
      message = "O teto de retorno potencial deve ter no máximo duas casas decimais.")
  private BigDecimal maxPotentialReturn;

  @NotNull(message = "A validade do bilhete é obrigatória.")
  @Min(value = 1, message = "A validade do bilhete deve ser pelo menos 1 minuto.")
  @Max(
      value = MAX_TICKET_VALIDITY_MINUTES,
      message = "A validade do bilhete não pode exceder 30 minutos.")
  private Integer ticketValidityMinutes;

  @AssertTrue(message = "A aposta mínima não pode ser maior que a aposta máxima.")
  public boolean isStakeRangeConsistent() {
    if (minStakeAmount == null || maxStakeAmount == null) {
      return true;
    }
    return minStakeAmount.compareTo(maxStakeAmount) <= 0;
  }

  public BigDecimal getMinStakeAmount() {
    return minStakeAmount;
  }

  public void setMinStakeAmount(BigDecimal minStakeAmount) {
    this.minStakeAmount = minStakeAmount;
  }

  public BigDecimal getMaxStakeAmount() {
    return maxStakeAmount;
  }

  public void setMaxStakeAmount(BigDecimal maxStakeAmount) {
    this.maxStakeAmount = maxStakeAmount;
  }

  public Integer getMaxSelections() {
    return maxSelections;
  }

  public void setMaxSelections(Integer maxSelections) {
    this.maxSelections = maxSelections;
  }

  public BigDecimal getMaxPotentialReturn() {
    return maxPotentialReturn;
  }

  public void setMaxPotentialReturn(BigDecimal maxPotentialReturn) {
    this.maxPotentialReturn = maxPotentialReturn;
  }

  public Integer getTicketValidityMinutes() {
    return ticketValidityMinutes;
  }

  public void setTicketValidityMinutes(Integer ticketValidityMinutes) {
    this.ticketValidityMinutes = ticketValidityMinutes;
  }
}
