package com.bytevault.apigateway.filter;

import com.bytevault.apigateway.util.JwtValidator;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Runs before every proxied request. Public routes (registration/login,
 * catalog browsing, the token-guarded download endpoint) are let through;
 * everything else must carry a valid Bearer login JWT, checked here with the
 * same shared secret every backend service independently verifies again.
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private record PublicRoute(String method, Pattern pathPattern) {
        boolean matches(String method, String path) {
            return this.method.equals(method) && pathPattern.matcher(path).matches();
        }
    }

    private static final List<PublicRoute> PUBLIC_ROUTES = List.of(
            new PublicRoute("POST", Pattern.compile("^/api/users/register$")),
            new PublicRoute("POST", Pattern.compile("^/api/users/login$")),
            new PublicRoute("GET", Pattern.compile("^/api/products(/\\d+)?$")),
            new PublicRoute("GET", Pattern.compile("^/api/products/\\d+/download$"))
    );

    private final JwtValidator jwtValidator;

    public JwtAuthGlobalFilter(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();

        if (isPublic(method, path)) {
            return chain.filter(exchange);
        }

        String header = request.getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ") || !jwtValidator.isValid(header.substring(7))) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    private boolean isPublic(String method, String path) {
        return PUBLIC_ROUTES.stream().anyMatch(route -> route.matches(method, path));
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
