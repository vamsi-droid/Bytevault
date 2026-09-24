package com.bytevault.orderservice.client;

import com.bytevault.orderservice.dto.DownloadTokenDto;
import com.bytevault.orderservice.dto.ProductDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Talks to product-service purely through its Eureka-registered name
 * ("product-service") - Spring Cloud LoadBalancer resolves that to an actual
 * host:port at call time, which is why every service's spring.application.name
 * has to stay exactly what the other services expect.
 */
@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/api/products/{id}")
    ProductDto getProduct(@PathVariable("id") Long id, @RequestHeader("Authorization") String authorization);

    @PostMapping("/api/products/{id}/download-token")
    DownloadTokenDto generateDownloadToken(@PathVariable("id") Long id, @RequestHeader("Authorization") String authorization);
}
