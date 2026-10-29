package com.bytevault.userservice.security;

import com.bytevault.userservice.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * user-service issues the login JWT, but it also verifies it on every
 * account-management endpoint. Only register, login, the load-balancing
 * instance endpoint and the actuator health/info endpoints are public.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String USERNAME_ATTRIBUTE = "authUsername";
    public static final String ROLE_ATTRIBUTE = "authRole";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.startsWith("/actuator/")) {
            return true; // health/info only, linked from the Eureka dashboard
        }
        if ("GET".equalsIgnoreCase(request.getMethod()) && path.equals("/api/users/instance")) {
            return true; // load-balancing demo, exposes nothing sensitive
        }
        return "POST".equalsIgnoreCase(request.getMethod())
                && (path.equals("/api/users/register") || path.equals("/api/users/login"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            respondUnauthorized(response, "Missing or malformed Authorization header");
            return;
        }

        String token = header.substring(7);
        try {
            Claims claims = jwtUtil.parseClaims(token);
            request.setAttribute(USERNAME_ATTRIBUTE, claims.getSubject());
            request.setAttribute(ROLE_ATTRIBUTE, claims.get("role", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            respondUnauthorized(response, "Invalid or expired token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void respondUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
