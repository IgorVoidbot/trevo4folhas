package com.trevo4folhas.admin;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads operator accounts for Spring Security. Unknown usernames and inactive operators both fail
 * authentication: the public login message stays generic and never reveals whether a username
 * exists (PRD RF-16, security profile rule 3). No credential is logged here.
 */
@Service
public class AdminUserDetailsService implements UserDetailsService {

  private final AdminRepository adminRepository;

  public AdminUserDetailsService(AdminRepository adminRepository) {
    this.adminRepository = adminRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String username) {
    Admin admin =
        adminRepository
            .findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Operator account not found"));
    return new AdminUserDetails(admin);
  }
}
