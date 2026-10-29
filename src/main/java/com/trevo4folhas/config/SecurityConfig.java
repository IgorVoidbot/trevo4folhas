package com.trevo4folhas.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

import com.trevo4folhas.admin.AdminLoginRateLimitFilter;
import com.trevo4folhas.admin.AdminLoginRateLimiter;
import com.trevo4folhas.admin.AdminUserDetailsService;

/**
 * Admin authentication and route protection (PRD RF-16, RNF-07/RNF-08). Operators authenticate
 * through a server-side session; bettors stay anonymous. Verification of raw passwords happens
 * exactly once here, through {@link DaoAuthenticationProvider} and the {@link PasswordEncoder}
 * (BCrypt), never by comparing hashes in domain code.
 *
 * <p>Protection is fail-closed: only the public routes of PRD 8.5 are permitted, {@code /admin/**}
 * and {@code /api/admin/**} require {@code ROLE_ADMIN} and anything else is denied. CSRF stays
 * enabled for public and admin routes alike.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final String CSP_DIRECTIVES =
      "default-src 'self'; script-src 'self'; style-src 'self'; object-src 'none'; "
          + "base-uri 'self'; frame-ancestors 'none'; form-action 'self'";

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public DaoAuthenticationProvider authenticationProvider(
      AdminUserDetailsService adminUserDetailsService, PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(adminUserDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return provider;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      DaoAuthenticationProvider authenticationProvider,
      AdminLoginRateLimiter adminLoginRateLimiter)
      throws Exception {
    ApiAwareAuthenticationEntryPoint authenticationEntryPoint =
        new ApiAwareAuthenticationEntryPoint();
    ApiAwareAccessDeniedHandler accessDeniedHandler = new ApiAwareAccessDeniedHandler();

    http.authenticationProvider(authenticationProvider);
    // CSRF stays on for every mutable route, public ones included. Failures travel to
    // ExceptionTranslationFilter, where the accessDeniedHandler/entryPoint pair below applies
    // the same api/page error contract (CSRF_INVALID / ?csrfError) instead of opening the route
    // to the frontend. This Spring Security version exposes no CsrfConfigurer accessDeniedHandler,
    // so classification happens at the supported exception-handling seam.
    http.csrf(Customizer.withDefaults());

    http.authorizeHttpRequests(
        auth ->
            auth.requestMatchers(
                    HttpMethod.GET,
                    "/",
                    "/error",
                    "/admin/login",
                    "/api/config",
                    "/api/matches/**",
                    "/api/markets/*/selections",
                    "/css/**",
                    "/js/**",
                    "/images/**")
                .permitAll()
                .requestMatchers(HttpMethod.POST, "/admin/login", "/api/tickets", "/api/tickets/**")
                .permitAll()
                .requestMatchers("/admin/**", "/api/admin/**")
                .hasRole("ADMIN")
                .anyRequest()
                .denyAll());

    http.formLogin(
        form ->
            form.loginPage("/admin/login")
                .loginProcessingUrl("/admin/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .defaultSuccessUrl("/admin", true)
                .failureUrl("/admin/login?error")
                .permitAll());

    // Logout is POST-only with CSRF; sessions are invalidated and the cookie deleted.
    http.logout(
        logout ->
            logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/admin/logout", "POST"))
                .logoutSuccessUrl("/admin/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"));

    // Session id rotates on login (session fixation), per RNF-07.
    http.sessionManagement(session -> session.sessionFixation().changeSessionId());

    http.exceptionHandling(
        handling ->
            handling
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler));

    http.addFilterBefore(
        new AdminLoginRateLimitFilter(adminLoginRateLimiter),
        UsernamePasswordAuthenticationFilter.class);

    http.headers(
        headers ->
            headers
                .contentTypeOptions(contentType -> {})
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER))
                // Default HSTS writer: only on HTTPS requests, so local HTTP development is not
                // broken (security profile rule 12).
                .httpStrictTransportSecurity(hsts -> {})
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP_DIRECTIVES)));

    return http.build();
  }
}
