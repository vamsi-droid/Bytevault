package com.bytevault.orderservice.config;

import com.bytevault.orderservice.entity.OrderStatus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Hibernate writes a CHECK constraint listing the OrderStatus values when it
 * first creates the orders table, but ddl-auto=update never revisits it. This
 * rebuilds the constraint from the current enum on startup so newly added
 * statuses (e.g. CANCELLED) can be stored in an existing database.
 */
@Component
public class OrderStatusConstraintUpdater implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public OrderStatusConstraintUpdater(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        String allowed = Arrays.stream(OrderStatus.values())
                .map(status -> "'" + status.name() + "'")
                .collect(Collectors.joining(", "));
        jdbcTemplate.execute("ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check");
        jdbcTemplate.execute("ALTER TABLE orders ADD CONSTRAINT orders_status_check CHECK (status IN (" + allowed + "))");
    }
}
