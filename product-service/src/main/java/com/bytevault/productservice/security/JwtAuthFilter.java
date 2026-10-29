package com.bytevault.productservice.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bytevault.productservice.dto.ErrorResponse;
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
 * product-service validates the login JWT itself instead of trusting that a
 * request already passed the gateway's filter. Browsing (GET list / GET by
 * id) stays public; everything else needs a valid Authorization header.
 * The download endpoint is excluded here because it is protected by its own,
 * short-lived download token instead of the login JWT.
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
        String method = request.getMethod();
        String path = request.getServletPath();

        if (path.startsWith("/actuator/")) {
            return true; // health/info only, linked from the Eureka dashboard
        }
        if (path.endsWith("/download")) {
            return true; // guarded by its own download token, not the login JWT
        }
        return "GET".equalsIgnoreCase(method) && path.matches("^/api/products(/\\d+|/instance)?$");
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
