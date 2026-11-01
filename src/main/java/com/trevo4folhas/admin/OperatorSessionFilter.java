package com.trevo4folhas.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.trevo4folhas.config.ApiAwareAuthenticationEntryPoint;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

/** Revoke cached admin sessions when an operator is disabled or its password rotates. */
public class OperatorSessionFilter extends OncePerRequestFilter {
  private final AdminRepository repository;

  public OperatorSessionFilter(AdminRepository repository) {
    this.repository = repository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI();
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if ((path.startsWith("/admin")
            || path.startsWith("/api/admin/")
            || path.startsWith("/actuator/metrics"))
        && auth != null
        && auth.getPrincipal() instanceof AdminUserDetails principal) {
      var operator = repository.findById(principal.getAdminId()).orElse(null);
      if (operator == null
          || !operator.isActive()
          || !MessageDigest.isEqual(
              operator.passwordHashForAuthentication().getBytes(StandardCharsets.UTF_8),
              principal.getPassword().getBytes(StandardCharsets.UTF_8))) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        Cookie cookie = new Cookie("JSESSIONID", "");
        cookie.setPath(request.getContextPath().isEmpty() ? "/" : request.getContextPath());
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        cookie.setSecure(request.isSecure());
        response.addCookie(cookie);
        new ApiAwareAuthenticationEntryPoint()
            .commence(
                request,
                response,
                new InsufficientAuthenticationException("Operator session revoked"));
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
