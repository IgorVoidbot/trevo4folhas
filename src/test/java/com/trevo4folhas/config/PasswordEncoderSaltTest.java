package com.trevo4folhas.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt verification contract (security profile rule 2): the configured encoder generates a random
 * salt per password — the same password never yields the same hash — and raw passwords are never
 * stored. This is the real login verifier; hash-to-hash comparison is not authentication.
 */
class PasswordEncoderSaltTest {

  private final PasswordEncoder passwordEncoder = new SecurityConfig().passwordEncoder();

  @Test
  void samePasswordProducesDistinctHashesBecauseSaltIsRandomPerPassword() {
    String firstHash = passwordEncoder.encode("Correct-Horse-Battery-9");
    String secondHash = passwordEncoder.encode("Correct-Horse-Battery-9");

    assertThat(firstHash).isNotEqualTo(secondHash);
    assertThat(passwordEncoder.matches("Correct-Horse-Battery-9", firstHash)).isTrue();
    assertThat(passwordEncoder.matches("Correct-Horse-Battery-9", secondHash)).isTrue();
  }

  @Test
  void storedValueIsABcryptHashNeverTheRawPassword() {
    String rawPassword = "Correct-Horse-Battery-9";
    String hash = passwordEncoder.encode(rawPassword);

    assertThat(hash).isNotEqualTo(rawPassword);
    assertThat(hash).startsWith("$2");
    assertThat(rawPassword).doesNotContain(hash);
    assertThat(passwordEncoder.matches(rawPassword + "!", hash)).isFalse();
  }
}
