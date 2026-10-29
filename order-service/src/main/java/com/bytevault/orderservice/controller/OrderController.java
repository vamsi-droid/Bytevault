package com.bytevault.orderservice.controller;

import com.bytevault.orderservice.dto.OrderRequest;
import com.bytevault.orderservice.dto.OrderResponse;
import com.bytevault.orderservice.exception.ForbiddenException;
import com.bytevault.orderservice.security.JwtAuthFilter;
import com.bytevault.orderservice.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    /** Every user's orders - ADMIN only. */
    @GetMapping("/all")
    public List<OrderResponse> listAllOrders(HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute(JwtAuthFilter.ROLE_ATTRIBUTE);
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException("Only an ADMIN can list every order");
        }
        return orderService.listAllOrders(authorization(httpRequest));
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable Long id, HttpServletRequest httpRequest) {
        return orderService.getOrder(username(httpRequest), id, authorization(httpRequest));
    }

    @PutMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable Long id, HttpServletRequest httpRequest) {
        return orderService.cancelOrder(username(httpRequest), id, authorization(httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable Long id, HttpServletRequest httpRequest) {
        orderService.deleteOrder(username(httpRequest), id);
        return ResponseEntity.noContent().build();
    }

    private String username(HttpServletRequest request) {
        return (String) request.getAttribute(JwtAuthFilter.USERNAME_ATTRIBUTE);
    }

    private String authorization(HttpServletRequest request) {
        return request.getHeader("Authorization");
    }
}
