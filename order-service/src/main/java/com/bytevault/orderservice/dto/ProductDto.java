package com.bytevault.orderservice.dto;

import java.math.BigDecimal;

/** Mirrors product-service's ProductResponse - what we get back over Feign. */
public class ProductDto {

    private Long id;
    private String name;
    private BigDecimal price;
    private String fileType;

    public ProductDto() {
    }

    public ProductDto(Long id, String name, BigDecimal price, String fileType) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.fileType = fileType;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }
}
