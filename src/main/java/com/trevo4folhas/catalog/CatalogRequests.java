package com.trevo4folhas.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public final class CatalogRequests {
  private CatalogRequests() {}

  /** Reject mass assignment without changing the application's global JSON configuration. */
  public interface StrictRequest {
    @JsonAnySetter
    default void rejectUnknownField(String name, JsonNode value) {
      throw new IllegalArgumentException("Campo não permitido na requisição.");
    }
  }

  public record CreateMatch(
      @NotBlank(message = "Informe a competição.")
          @Size(max = 120, message = "Competição muito longa.")
          String competition,
      @NotBlank(message = "Informe o mandante.")
          @Size(max = 120, message = "Nome do mandante muito longo.")
          String homeTeam,
      @NotBlank(message = "Informe o visitante.")
          @Size(max = 120, message = "Nome do visitante muito longo.")
          String awayTeam,
      @NotNull(message = "Informe o início da partida.") Instant startsAt)
      implements StrictRequest {}

  public record CreateMarket(
      @NotNull(message = "Informe a versão da partida.") @Positive(message = "Versão inválida.")
          Long matchVersion,
      @NotBlank(message = "Informe o tipo de mercado.")
          @Pattern(regexp = "[A-Z0-9][A-Z0-9_:-]{0,63}", message = "Código de mercado inválido.")
          String marketType,
      @NotBlank(message = "Informe o nome do mercado.")
          @Size(max = 255, message = "Nome muito longo.")
          String displayName,
      @NotBlank(message = "Informe o período.") @Size(max = 64, message = "Período muito longo.")
          String period,
      @Size(max = 64, message = "Linha muito longa.") String line,
      @Size(max = 255, message = "Participante muito longo.") String participant,
      @NotBlank(message = "Informe a chave da regra.")
          @Size(max = 255, message = "Chave muito longa.")
          String ruleKey,
      @NotBlank(message = "Informe a regra.") @Size(max = 4000, message = "Regra muito longa.")
          String ruleText,
      @Size(max = 16, message = "Especificadores em excesso.")
          Map<
                  @Pattern(regexp = "[A-Za-z0-9_:-]{1,64}", message = "Especificador inválido.")
                  String,
                  @NotBlank(message = "Valor do especificador vazio.")
                  @Size(max = 128, message = "Valor muito longo.") String>
              specifiers,
      boolean curated)
      implements StrictRequest {}

  public record CreateSelection(
      @NotNull(message = "Informe a versão da partida.") @Positive(message = "Versão inválida.")
          Long matchVersion,
      @NotNull(message = "Informe a versão do mercado.") @Positive(message = "Versão inválida.")
          Long marketVersion,
      @NotBlank(message = "Informe o código da seleção.")
          @Pattern(regexp = "[A-Z0-9][A-Z0-9_:-]{0,63}", message = "Código de seleção inválido.")
          String selectionCode,
      @NotBlank(message = "Informe o nome da seleção.")
          @Size(max = 255, message = "Nome muito longo.")
          String displayName,
      @NotNull(message = "Informe a cotação.")
          @DecimalMin(value = "1.01", message = "A cotação mínima é 1,01.")
          @DecimalMax(value = "100.00", message = "A cotação máxima é 100,00.")
          @Digits(
              integer = 3,
              fraction = 2,
              message = "Cotação deve ter no máximo duas casas decimais.")
          @JsonDeserialize(using = DecimalStringDeserializer.class)
          BigDecimal odds,
      boolean curated)
      implements StrictRequest {}

  public record UpdateMatch(
      @NotNull @Positive Long matchVersion,
      @NotBlank @Size(max = 120) String competition,
      @NotBlank @Size(max = 120) String homeTeam,
      @NotBlank @Size(max = 120) String awayTeam,
      @NotNull Instant startsAt,
      @NotNull com.trevo4folhas.match.MatchStatus status,
      boolean adminSuspended)
      implements StrictRequest {}

  public record UpdateMarket(
      @NotNull @Positive Long matchVersion,
      @NotNull @Positive Long marketVersion,
      @NotBlank @Size(max = 255) String displayName,
      @NotBlank @Size(max = 4000) String ruleText,
      boolean curated,
      boolean adminSuspended)
      implements StrictRequest {}

  public record UpdateSelection(
      @NotNull @Positive Long matchVersion,
      @NotNull @Positive Long marketVersion,
      @NotNull @Positive Long selectionVersion,
      @NotBlank @Size(max = 255) String displayName,
      @NotNull
          @DecimalMin("1.01")
          @DecimalMax("100.00")
          @Digits(integer = 3, fraction = 2)
          @JsonDeserialize(using = DecimalStringDeserializer.class)
          BigDecimal odds,
      boolean curated,
      boolean adminSuspended)
      implements StrictRequest {}
}
