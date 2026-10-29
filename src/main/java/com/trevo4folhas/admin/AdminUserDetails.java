package com.trevo4folhas.admin;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Authenticated operator session principal. Carries only what the admin session needs: identity for
 * audit trails and display name for the UI. The BCrypt hash is exposed through {@link
 * #getPassword()} solely because Spring Security's authentication provider must verify the raw
 * password against it; it never reaches a DTO, a log or a response.
 */
public final class AdminUserDetails implements UserDetails {

  private static final String ROLE_ADMIN = "ROLE_ADMIN";

  private final UUID adminId;
  private final String username;
  private final String passwordHash;
  private final String displayName;
  private final boolean active;

  AdminUserDetails(Admin admin) {
    this.adminId = admin.getId();
    this.username = admin.getUsername();
    this.passwordHash = admin.passwordHashForAuthentication();
    this.displayName = admin.getDisplayName();
    this.active = admin.isActive();
  }

  /**
   * Identifier of the operator for audit fields such as {@code handled_by}; never from request
   * bodies.
   */
  public UUID getAdminId() {
    return adminId;
  }

  public String getDisplayName() {
    return displayName;
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority(ROLE_ADMIN));
  }

  @Override
  public String getPassword() {
    return passwordHash;
  }

  @Override
  public String getUsername() {
    return username;
  }

  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  @Override
  public boolean isEnabled() {
    return active;
  }

  /** Never renders the password hash. */
  @Override
  public String toString() {
    return "AdminUserDetails [adminId="
        + adminId
        + ", username="
        + username
        + ", active="
        + active
        + "]";
  }
}
