package com.bytevault.orderservice.security;

import com.bytevault.orderservice.dto.ErrorResponse;
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
 * Every order-service API endpoint requires a buyer to be logged in; only the
 * actuator health/info endpoints (linked from the Eureka dashboard) and the
 * load-balancing instance endpoint are exempt.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    public static final String USERNAME_ATTRIBUTE = "authUsername";
    public static final String ROLE_ATTRIBUTE = "authRole";

    private final JwtUtil jwtUtil;
    // Spring's ObjectMapper, not `new ObjectMapper()`: it has the java.time module,
    // which ErrorResponse's Instant timestamp needs to serialize.
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/actuator/")
                || ("GET".equalsIgnoreCase(request.getMethod()) && path.equals("/api/orders/instance"));
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
