package com.bytevault.orderservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stamps every response with the instance that served it (X-Served-By:
 * order-service:&lt;port&gt;) and logs it, so with two instances running you can watch
 * the gateway / Feign load balancer alternate between them.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InstanceHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Served-By";

    private static final Logger log = LoggerFactory.getLogger(InstanceHeaderFilter.class);

    private final String instanceId;

    public InstanceHeaderFilter(@Value("${spring.application.name}") String applicationName,
                                @Value("${server.port}") String port) {
        this.instanceId = applicationName + ":" + port;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        response.setHeader(HEADER, instanceId);
        if (!request.getServletPath().startsWith("/actuator/")) {
            log.info("{} {} served by {}", request.getMethod(), request.getServletPath(), instanceId);
        }
        filterChain.doFilter(request, response);
    }

    public String getInstanceId() {
        return instanceId;
    }
}
