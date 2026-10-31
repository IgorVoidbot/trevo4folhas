package com.trevo4folhas.ticket;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

record TicketIntent(
    String bettorName,
    String bettorPhone,
    BigDecimal stakeAmount,
    String rulesVersion,
    List<TicketRequests.Item> items,
    String requestHash) {
  static TicketIntent normalize(TicketRequests.CreateTicket request, ObjectMapper mapper) {
    String name = request.bettorName().strip();
    if (name.length() < 2
        || name.length() > 100
        || name.codePoints().anyMatch(Character::isISOControl))
      throw TicketException.invalid(
          "Informe um nome entre 2 e 100 caracteres, sem quebras de linha.");
    String phone = request.bettorPhone().strip().replaceAll("[\\s()+-]", "");
    if (!phone.matches("(?:55)?[1-9][0-9]{9,10}"))
      throw TicketException.invalid("Informe um telefone brasileiro válido com DDD.");
    if (phone.length() <= 11) phone = "55" + phone;
    if (!request.rulesAccepted() || !request.adultDeclared())
      throw TicketException.invalid("Confirme a leitura das regras e a maioridade.");
    List<TicketRequests.Item> items =
        request.items().stream()
            .sorted(Comparator.comparing(i -> i.selectionId().toString()))
            .toList();
    if (items.stream().map(TicketRequests.Item::selectionId).distinct().count() != items.size())
      throw TicketException.invalid("Não repita a mesma seleção no cupom.");
    BigDecimal amount = request.stakeAmount().setScale(2);
    try {
      String hash =
          hash(
              mapper.writeValueAsString(
                  List.of(
                      name,
                      phone,
                      amount.toPlainString(),
                      request.rulesVersion(),
                      true,
                      true,
                      items)));
      return new TicketIntent(name, phone, amount, request.rulesVersion(), items, hash);
    } catch (java.io.IOException failure) {
      throw new IllegalStateException("Unable to fingerprint ticket intent");
    }
  }

  static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 unavailable");
    }
  }

  static boolean equal(String a, String b) {
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
  }

  @Override
  public String toString() {
    return "TicketIntent[redacted]";
  }
}
