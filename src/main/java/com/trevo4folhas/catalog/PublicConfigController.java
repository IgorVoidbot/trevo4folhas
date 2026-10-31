package com.trevo4folhas.catalog;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.trevo4folhas.config.BettingProperties;

/** Explicit public allowlist; never serialize configuration beans or environment maps. */
@RestController
public class PublicConfigController {

  private final BettingProperties betting;
  private final CatalogProperties catalog;

  public PublicConfigController(BettingProperties betting, CatalogProperties catalog) {
    this.betting = betting;
    this.catalog = catalog;
  }

  @GetMapping("/api/config")
  public PublicConfigResponse config() {
    return new PublicConfigResponse(
        betting.getMinStakeAmount().toPlainString(),
        betting.getMaxStakeAmount().toPlainString(),
        betting.getMaxSelections(),
        betting.getMaxPotentialReturn().toPlainString(),
        betting.getTicketValidityMinutes(),
        catalog.bankTimeZone(),
        catalog.bankWhatsappNumber().isEmpty() ? null : catalog.bankWhatsappNumber());
  }

  public record PublicConfigResponse(
      String minStakeAmount,
      String maxStakeAmount,
      int maxSelections,
      String maxPotentialReturn,
      int ticketValidityMinutes,
      String bankTimeZone,
      String bankWhatsappNumber) {}
}
