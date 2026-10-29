package com.trevo4folhas.admin;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the admin login form and the authenticated admin landing page. Route protection is
 * enforced by {@code SecurityConfig}, not here; this controller only renders pt-BR screens.
 */
@Controller
public class AdminHomeController {

  @GetMapping("/admin/login")
  public String login(@AuthenticationPrincipal AdminUserDetails principal) {
    if (principal != null) {
      return "redirect:/admin";
    }
    return "admin/login";
  }

  @GetMapping("/admin")
  public String home(@AuthenticationPrincipal AdminUserDetails principal, Model model) {
    model.addAttribute("displayName", principal == null ? "" : principal.getDisplayName());
    return "admin/home";
  }
}
