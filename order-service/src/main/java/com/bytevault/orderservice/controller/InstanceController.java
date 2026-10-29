package com.bytevault.orderservice.controller;

import com.bytevault.orderservice.config.InstanceHeaderFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Public, read-only endpoint for demonstrating load balancing: call
 * GET /api/orders/instance through the gateway repeatedly while two instances
 * are running and the reported instance alternates.
 */
@RestController
public class InstanceController {

    private final InstanceHeaderFilter instanceHeaderFilter;

    public InstanceController(InstanceHeaderFilter instanceHeaderFilter) {
        this.instanceHeaderFilter = instanceHeaderFilter;
    }

    @GetMapping("/api/orders/instance")
    public Map<String, Object> instance() {
        return Map.of(
                "instance", instanceHeaderFilter.getInstanceId(),
                "timestamp", Instant.now().toString()
        );
    }
}
