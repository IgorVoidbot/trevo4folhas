package com.trevo4folhas.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;

/**
 * Configurable abuse limits (PRD RNF-09). Defaults follow the reference policy: 5 login attempts
 * per minute per IP. Excess returns {@code 429} without permanent blocking.
 */
@Validated
@ConfigurationProperties(prefix = "trevo.security.login-rate-limit")
public class LoginRateLimitProperties {

  /** PRD RNF-09 reference: 5 login attempts per minute per IP. */
  public static final int DEFAULT_MAX_ATTEMPTS = 5;

  /** One-minute window, as in the PRD RNF-09 reference. */
  public static final long DEFAULT_WINDOW_SECONDS = 60L;

  @Min(value = 1, message = "O limite de tentativas deve ser pelo menos 1.")
  private int maxAttempts = DEFAULT_MAX_ATTEMPTS;

  @Min(value = 1, message = "A janela deve ser de pelo menos 1 segundo.")
  private long windowSeconds = DEFAULT_WINDOW_SECONDS;

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public long getWindowSeconds() {
    return windowSeconds;
  }

  public void setWindowSeconds(long windowSeconds) {
    this.windowSeconds = windowSeconds;
  }
}
