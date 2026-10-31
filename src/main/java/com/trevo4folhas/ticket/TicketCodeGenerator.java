package com.trevo4folhas.ticket;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

@Component
public class TicketCodeGenerator {
  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private final SecureRandom random = new SecureRandom();

  public String next() {
    StringBuilder code = new StringBuilder("TF-");
    for (int index = 0; index < 10; index++)
      code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
    return code.toString();
  }
}
