package com.bytevault.orderservice.controller;

import com.bytevault.orderservice.dto.OrderRequest;
import com.bytevault.orderservice.dto.OrderResponse;
import com.bytevault.orderservice.security.JwtAuthFilter;
import com.bytevault.orderservice.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest request, HttpServletRequest httpRequest) {
        OrderResponse response = orderService.placeOrder(username(httpRequest), request.getProductId(), authorization(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public List<OrderResponse> listOrders(HttpServletRequest httpRequest) {
        return orderService.listOrders(username(httpRequest), authorization(httpRequest));
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id, HttpServletRequest httpRequest) {
        return orderService.getOrder(username(httpRequest), id, authorization(httpRequest));
    }

    private String username(HttpServletRequest request) {
        return (String) request.getAttribute(JwtAuthFilter.USERNAME_ATTRIBUTE);
    }

    private String authorization(HttpServletRequest request) {
        return request.getHeader("Authorization");
    }
}
