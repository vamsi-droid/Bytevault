package com.bytevault.orderservice.service;

import com.bytevault.orderservice.client.ProductClient;
import com.bytevault.orderservice.dto.DownloadTokenDto;
import com.bytevault.orderservice.dto.OrderResponse;
import com.bytevault.orderservice.dto.ProductDto;
import com.bytevault.orderservice.entity.Order;
import com.bytevault.orderservice.entity.OrderStatus;
import com.bytevault.orderservice.exception.InvalidOrderStateException;
import com.bytevault.orderservice.exception.OrderNotFoundException;
import feign.FeignException;
import com.bytevault.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public OrderService(OrderRepository orderRepository, ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
    }

    /**
     * This is a zero-payment-gateway demo: fetching the product (proving it
     * exists) and minting its download token in the same call is treated as
     * a successful purchase, so every order is created straight into PAID.
     */
    @Transactional
    public OrderResponse placeOrder(String username, Long productId, String authorization) {
        ProductDto product = productClient.getProduct(productId, authorization);

        Order order = new Order(username, product.getId(), product.getPrice(), OrderStatus.PAID, null, Instant.now());
        order = orderRepository.save(order);

        DownloadTokenDto downloadToken = productClient.generateDownloadToken(product.getId(), authorization);
        order.setDownloadToken(downloadToken.getDownloadToken());
        order = orderRepository.save(order);

        return OrderResponse.of(order, product.getName(), downloadToken.getDownloadUrl(), downloadToken.getExpiresAt());
    }

    public List<OrderResponse> listOrders(String username, String authorization) {
        return orderRepository.findByUsername(username).stream()
                .map(order -> toResponse(order, authorization))
                .collect(Collectors.toList());
    }

    public List<OrderResponse> listAllOrders(String authorization) {
        return orderRepository.findAll().stream()
                .map(order -> toResponse(order, authorization))
                .collect(Collectors.toList());
    }

    public OrderResponse getOrder(String username, Long orderId, String authorization) {
        return toResponse(findOwnOrder(username, orderId), authorization);
    }

    /**
     * Update operation for orders: a PAID order can be cancelled, which also
     * revokes its stored download link.
     */
    @Transactional
    public OrderResponse cancelOrder(String username, Long orderId, String authorization) {
        Order order = findOwnOrder(username, orderId);
        if (order.getStatus() != OrderStatus.PAID) {
            throw new InvalidOrderStateException("Only a PAID order can be cancelled (order " + orderId
                    + " is " + order.getStatus() + ")");
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setDownloadToken(null);
        return toResponse(orderRepository.save(order), authorization);
    }

    @Transactional
    public void deleteOrder(String username, Long orderId) {
        orderRepository.delete(findOwnOrder(username, orderId));
    }

    private Order findOwnOrder(String username, Long orderId) {
        return orderRepository.findByIdAndUsername(orderId, username)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private OrderResponse toResponse(Order order, String authorization) {
        String productName;
        try {
            productName = productClient.getProduct(order.getProductId(), authorization).getName();
        } catch (FeignException.NotFound e) {
            // The product was removed from the catalog after it was bought;
            // the order history should still list.
            productName = "(product no longer available)";
        }
        String downloadLink = order.getDownloadToken() == null ? null
                : "/api/products/" + order.getProductId() + "/download?token=" + order.getDownloadToken();
        // The download token's own expiry lives in product-service (separate signing
        // key), so re-fetching it here isn't worth another network hop for a demo.
        return OrderResponse.of(order, productName, downloadLink, null);
    }
}
