package com.bytevault.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * Serves the dashboard page (static/index.html) at the gateway root, so
 * http://localhost:8080/ opens the UI. WebFlux doesn't map "/" to
 * index.html on its own here.
 */
@Configuration
public class DashboardConfig {

    @Bean
    public RouterFunction<ServerResponse> dashboardRoute() {
        ClassPathResource page = new ClassPathResource("static/index.html");
        return route(GET("/"), request -> ServerResponse.ok()
                .contentType(MediaType.TEXT_HTML)
                .bodyValue(page));
    }
}
