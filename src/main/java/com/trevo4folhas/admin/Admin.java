package com.trevo4folhas.admin;

import com.trevo4folhas.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Operator of the admin panel ({@code admins}). Disabled operators are kept for traceability.
 *
 * <p>Password verification is deliberately NOT a method of this entity: comparing hashes cannot
 * authenticate a raw password. Real verification happens once, inside Spring Security's {@code
 * DaoAuthenticationProvider}, through the configured {@code PasswordEncoder} (BCrypt) against the
 * hash handed to the authentication adapter by {@link #passwordHashForAuthentication()}.
 */
@Entity
@Table(name = "admins")
public class Admin extends BaseEntity {

  @Column(name = "username", nullable = false, length = 100)
  private String username;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  /**
   * BCrypt hash. This is persistence-only data: it must never reach a DTO, a log or an error
   * message, and there is deliberately no public accessor that copies it into API responses.
   */
  @Column(name = "password_hash", nullable = false, length = 255)
  private String passwordHash;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  protected Admin() {
    // For JPA.
  }

  public Admin(String username, String displayName, String passwordHash) {
    this.username = username;
    this.displayName = displayName;
    this.passwordHash = passwordHash;
    this.active = true;
  }

  public String getUsername() {
    return username;
  }

  public String getDisplayName() {
    return displayName;
  }

  public boolean isActive() {
    return active;
  }

  public void deactivate() {
    this.active = false;
  }

  /**
   * Package-private bridge used only by the authentication adapter ({@link
   * AdminUserDetailsService}) so Spring Security can verify the raw password with the {@code
   * PasswordEncoder}. Exposing this outside the package would risk leaking the hash into DTOs, logs
   * or responses.
   */
  String passwordHashForAuthentication() {
    return passwordHash;
  }
}
