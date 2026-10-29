package com.trevo4folhas.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deployment-time bootstrap credentials for the first operator (PRD RF-16). Values come exclusively
 * from the environment and deliberately have no default: the repository must never carry a default
 * password, a known hash or a permanent bootstrap secret. Leaving every value empty simply means
 * "no bootstrap requested", which is the steady state after the first controlled deploy.
 */
@ConfigurationProperties(prefix = "trevo.admin.bootstrap")
public class AdminBootstrapProperties {

  private String username = "";
  private String displayName = "";
  private String password = "";

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }
}
