package com.trevo4folhas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TrevoApplication {

  public static void main(String[] args) {
    SpringApplication.run(TrevoApplication.class, args);
  }
}
