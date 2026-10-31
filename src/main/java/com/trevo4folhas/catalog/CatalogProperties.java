package com.trevo4folhas.catalog;

import java.time.DateTimeException;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

/** Public bank settings, never provider credentials. An unset contact stays unavailable. */
@Validated
@ConfigurationProperties(prefix = "trevo.catalog")
public record CatalogProperties(
    String bankTimeZone,
    @Pattern(
            regexp = "|[1-9][0-9]{7,14}",
            message = "O WhatsApp da banca deve usar formato internacional, somente dígitos.")
        String bankWhatsappNumber,
    Long manualPriceMaxAgeSeconds,
    Long manualScheduleMaxAgeSeconds,
    Long manualStatusMaxAgeSeconds) {

  public CatalogProperties {
    bankTimeZone = bankTimeZone == null ? "America/Sao_Paulo" : bankTimeZone;
    bankWhatsappNumber = bankWhatsappNumber == null ? "" : bankWhatsappNumber;
  }

  @AssertTrue(message = "O fuso horário da banca é inválido.")
  public boolean isTimeZoneValid() {
    try {
      ZoneId.of(bankTimeZone);
      return true;
    } catch (DateTimeException invalid) {
      return false;
    }
  }
}
