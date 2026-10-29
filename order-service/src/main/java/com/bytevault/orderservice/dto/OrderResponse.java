package com.bytevault.orderservice.dto;

import com.bytevault.orderservice.entity.Order;

import java.math.BigDecimal;
import java.time.Instant;

public class OrderResponse {

    private Long orderId;
    private String username;
    private Long productId;
    private String productName;
    private BigDecimal price;
    private String status;
    private String downloadLink;
    private Instant downloadLinkExpiresAt;
    private Instant createdAt;

    public OrderResponse(Long orderId, String username, Long productId, String productName, BigDecimal price, String status,
                          String downloadLink, Instant downloadLinkExpiresAt, Instant createdAt) {
        this.orderId = orderId;
        this.username = username;
        this.productId = productId;
        this.productName = productName;
        this.price = price;
        this.status = status;
        this.downloadLink = downloadLink;
        this.downloadLinkExpiresAt = downloadLinkExpiresAt;
        this.createdAt = createdAt;
    }

    public static OrderResponse of(Order order, String productName, String downloadLink, Instant downloadLinkExpiresAt) {
        return new OrderResponse(
                order.getId(),
                order.getUsername(),
                order.getProductId(),
                productName,
                order.getPrice(),
                order.getStatus().name(),
                downloadLink,
                downloadLinkExpiresAt,
                order.getCreatedAt()
        );
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDownloadLink() {
        return downloadLink;
    }

    public void setDownloadLink(String downloadLink) {
        this.downloadLink = downloadLink;
    }

    public Instant getDownloadLinkExpiresAt() {
        return downloadLinkExpiresAt;
    }

    public void setDownloadLinkExpiresAt(Instant downloadLinkExpiresAt) {
        this.downloadLinkExpiresAt = downloadLinkExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
