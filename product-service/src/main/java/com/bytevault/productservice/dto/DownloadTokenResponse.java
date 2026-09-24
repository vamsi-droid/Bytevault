package com.bytevault.productservice.dto;

import java.time.Instant;

public class DownloadTokenResponse {

    private Long productId;
    private String downloadToken;
    private String downloadUrl;
    private Instant expiresAt;

    public DownloadTokenResponse(Long productId, String downloadToken, String downloadUrl, Instant expiresAt) {
        this.productId = productId;
        this.downloadToken = downloadToken;
        this.downloadUrl = downloadUrl;
        this.expiresAt = expiresAt;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getDownloadToken() {
        return downloadToken;
    }

    public void setDownloadToken(String downloadToken) {
        this.downloadToken = downloadToken;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
