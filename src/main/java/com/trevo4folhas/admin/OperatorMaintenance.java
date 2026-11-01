package com.trevo4folhas.admin;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Explicit one-shot maintenance process; never exposes a web endpoint or password in SQL. */
public final class OperatorMaintenance {
  private OperatorMaintenance() {}

  @Configuration(proxyBeanMethods = false)
  @Profile("operator-maintenance")
  @EnableAutoConfiguration(
      exclude = {
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
            .class
      })
  static class MaintenanceConfiguration {}

  public static void run(String[] args) {
    try {
      String action = System.getenv("TREVO_OPERATOR_ACTION"),
          username = System.getenv("TREVO_OPERATOR_USERNAME");
      if (username == null
          || username.isBlank()
          || !("disable".equals(action) || "reset-password".equals(action)))
        throw new IllegalArgumentException("Invalid maintenance options");
      String hash = null;
      if ("reset-password".equals(action)) {
        char[] input =
            System.console() == null ? null : System.console().readPassword("Nova senha: ");
        String password =
            input == null ? System.getenv("TREVO_OPERATOR_PASSWORD") : new String(input);
        if (input != null) Arrays.fill(input, '\0');
        if (password == null
            || password.length() < 12
            || password.getBytes(StandardCharsets.UTF_8).length > 72)
          throw new IllegalArgumentException("Invalid password length");
        hash = new BCryptPasswordEncoder().encode(password);
      }
      var application = new SpringApplication(MaintenanceConfiguration.class);
      application.setAdditionalProfiles("operator-maintenance");
      application.setWebApplicationType(WebApplicationType.NONE);
      application.setBannerMode(Banner.Mode.OFF);
      application.setLogStartupInfo(false);
      try (var context = application.run(args)) {
        var jdbc = context.getBean(JdbcTemplate.class);
        int updated =
            "disable".equals(action)
                ? jdbc.update(
                    "UPDATE public.admins SET active=false WHERE username=?", username.strip())
                : jdbc.update(
                    "UPDATE public.admins SET password_hash=? WHERE username=?",
                    hash,
                    username.strip());
        if (updated != 1) throw new IllegalStateException("Operator not found");
        System.out.println(
            "event=operator_access_changed action="
                + ("disable".equals(action) ? "DISABLE" : "RESET_PASSWORD")
                + " updated=1");
      }
    } catch (RuntimeException failure) {
      System.err.println(
          "Não foi possível concluir a manutenção. Confira a configuração e os requisitos da operação.");
      System.exit(1);
    }
  }
}
