package com.trevo4folhas.ticket;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Component;

import com.trevo4folhas.config.BettingProperties;

@Component
public class MoneyCalculator {
  public record Calculation(BigDecimal totalOdds, BigDecimal potentialReturn) {}

  public Calculation calculate(BigDecimal stake, List<BigDecimal> odds, BettingProperties limits) {
    if (stake == null
        || stake.scale() > 2
        || stake.compareTo(limits.getMinStakeAmount()) < 0
        || stake.compareTo(limits.getMaxStakeAmount()) > 0)
      throw TicketException.invalid("O valor está fora dos limites permitidos.");
    if (odds.isEmpty() || odds.size() > limits.getMaxSelections())
      throw TicketException.invalid("Confira a quantidade de seleções do cupom.");
    BigDecimal product = BigDecimal.ONE;
    for (BigDecimal price : odds) {
      if (price == null
          || price.scale() > 2
          || price.compareTo(new BigDecimal("1.01")) < 0
          || price.compareTo(new BigDecimal("100.00")) > 0) throw TicketException.unavailable();
      product = product.multiply(price);
    }
    BigDecimal result = stake.multiply(product).setScale(2, RoundingMode.HALF_UP);
    if (result.compareTo(limits.getMaxPotentialReturn()) > 0)
      throw TicketException.invalid(
          "O retorno potencial excede o limite permitido. Reduza o valor ou as seleções.");
    return new Calculation(product, result);
  }
}
