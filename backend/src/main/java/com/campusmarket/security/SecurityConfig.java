package com.campusmarket.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, LoginSessions sessions) throws Exception {
        return http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.csrfTokenRepository(new CookieCsrfTokenRepository()))
                .addFilterBefore(new SessionFilter(sessions), AnonymousAuthenticationFilter.class)
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/items", "/api/items/{id}",
                                "/api/items/{id}/image", "/api/items/{id}/quote").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/seller/**", "/api/reservations/**", "/api/auctions/**",
                                "/api/conversations/**", "/ws").hasRole("STUDENT")
                        .anyRequest().denyAll())
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
