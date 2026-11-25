package com.campusmarket.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class SessionFilter extends OncePerRequestFilter {
    private final LoginSessions sessions;

    SessionFilter(LoginSessions sessions) {
        this.sessions = sessions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (!LoginSessions.COOKIE.equals(cookie.getName())) {
                    continue;
                }
                try {
                    var identity = sessions.authenticate(cookie.getValue());
                    if (identity != null) {
                        var authorities = identity.roles().stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
                        var context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                                identity, null, authorities));
                        SecurityContextHolder.setContext(context);
                    }
                } catch (DataAccessException ex) {
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                    return;
                }
                break;
            }
        }
        chain.doFilter(request, response);
    }
}
