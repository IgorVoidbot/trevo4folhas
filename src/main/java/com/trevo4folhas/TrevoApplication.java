package com.trevo4folhas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TrevoApplication {

  public static void main(String[] args) {
    if (java.util.Arrays.asList(args).contains("--operator-maintenance")) {
      com.trevo4folhas.admin.OperatorMaintenance.run(
          java.util.Arrays.stream(args)
              .filter(arg -> !arg.equals("--operator-maintenance"))
              .toArray(String[]::new));
      return;
    }
    SpringApplication.run(TrevoApplication.class, args);
  }
}
