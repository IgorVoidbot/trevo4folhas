package com.trevo4folhas.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Controlled creation of operator accounts from deployment-time environment credentials (PRD
 * RF-16). There is no default password and no public signup: without explicit environment values
 * nothing is created. The raw password is never logged, stored or returned; only its BCrypt hash is
 * persisted, so re-running the deployment with the same values cannot rebuild or reset a known
 * credential.
 */
@Service
public class AdminBootstrapService {

  /**
   * Initial password policy for bootstrap: length only, measured on the raw password at creation
   * time. Cost/rotation policy for BCrypt stays with the security profile and its benchmark.
   */
  static final int MIN_PASSWORD_LENGTH = 12;

  private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);

  private final AdminBootstrapProperties properties;
  private final AdminRepository adminRepository;
  private final PasswordEncoder passwordEncoder;

  public AdminBootstrapService(
      AdminBootstrapProperties properties,
      AdminRepository adminRepository,
      PasswordEncoder passwordEncoder) {
    this.properties = properties;
    this.adminRepository = adminRepository;
    this.passwordEncoder = passwordEncoder;
  }

  /**
   * Creates the operator described by the environment. A partial configuration fails closed (the
   * application must not start half-configured); an already existing username is never overwritten,
   * so a recurrent deployment cannot reset a password or create duplicates.
   */
  @Transactional
  public void bootstrap() {
    String username = properties.getUsername() == null ? "" : properties.getUsername().trim();
    String displayName =
        properties.getDisplayName() == null ? "" : properties.getDisplayName().trim();
    String rawPassword = properties.getPassword() == null ? "" : properties.getPassword();

    if (username.isEmpty() && rawPassword.isEmpty() && displayName.isEmpty()) {
      log.debug("event=admin_bootstrap_not_requested");
      return;
    }
    if (username.isEmpty() || rawPassword.isEmpty()) {
      throw new IllegalStateException(
          "ADMIN_BOOTSTRAP_USERNAME and ADMIN_BOOTSTRAP_PASSWORD must be provided together");
    }
    if (rawPassword.length() < MIN_PASSWORD_LENGTH || rawPassword.equals(username)) {
      throw new IllegalStateException(
          "ADMIN_BOOTSTRAP_PASSWORD does not meet the minimum bootstrap password policy");
    }
    if (adminRepository.findByUsername(username).isPresent()) {
      log.warn("event=admin_bootstrap_skipped_existing_username");
      return;
    }
    String effectiveDisplayName = displayName.isEmpty() ? username : displayName;
    String passwordHash = passwordEncoder.encode(rawPassword);
    Admin created = adminRepository.save(new Admin(username, effectiveDisplayName, passwordHash));
    // Identity only; never the username, the password or the hash.
    log.info("event=admin_bootstrap_operator_created adminId={}", created.getId());
  }
}
