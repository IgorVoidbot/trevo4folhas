package com.trevo4folhas.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * Structural regression for the transactional boundary of the operator bootstrap (backend profile
 * rule 5): the public service method that writes must declare {@code @Transactional} — the suites
 * that pass for bootstrap behavior alone cannot prove this boundary exists.
 */
class AdminBootstrapTransactionalBoundaryTest {

  @Test
  void bootstrapWritesUnderAnExplicitTransactionalBoundary() throws Exception {
    Transactional annotation =
        AdminBootstrapService.class.getMethod("bootstrap").getAnnotation(Transactional.class);
    assertThat(annotation)
        .as("AdminBootstrapService.bootstrap() must be @Transactional (backend rule 5)")
        .isNotNull();
  }
}
