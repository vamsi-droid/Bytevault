package com.bytevault.orderservice.dto;

import jakarta.validation.constraints.NotNull;

public class OrderRequest {

    @NotNull(message = "productId is required")
    private Long productId;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }
}
