package com.trevo4folhas.admin;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs the controlled operator bootstrap at startup. With no environment credentials this is a
 * no-op; misconfiguration or policy violations fail the startup instead of falling back silently.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

  private final AdminBootstrapService adminBootstrapService;

  public AdminBootstrapRunner(AdminBootstrapService adminBootstrapService) {
    this.adminBootstrapService = adminBootstrapService;
  }

  @Override
  public void run(ApplicationArguments args) {
    adminBootstrapService.bootstrap();
  }
}
