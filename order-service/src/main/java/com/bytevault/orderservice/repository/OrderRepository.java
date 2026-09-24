package com.bytevault.orderservice.repository;

import com.bytevault.orderservice.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUsername(String username);

    Optional<Order> findByIdAndUsername(Long id, String username);
}
