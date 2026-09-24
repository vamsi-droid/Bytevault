package com.bytevault.productservice.controller;

import com.bytevault.productservice.dto.DownloadTokenResponse;
import com.bytevault.productservice.dto.ProductRequest;
import com.bytevault.productservice.dto.ProductResponse;
import com.bytevault.productservice.entity.Product;
import com.bytevault.productservice.exception.ForbiddenException;
import com.bytevault.productservice.security.JwtAuthFilter;
import com.bytevault.productservice.service.ProductService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductResponse> listAll() {
        return productService.listAll().stream().map(ProductResponse::from).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        return ProductResponse.from(productService.getById(id));
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request, HttpServletRequest httpRequest) {
        requireAdmin(httpRequest);
        Product created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(created));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request, HttpServletRequest httpRequest) {
        requireAdmin(httpRequest);
        return ProductResponse.from(productService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        requireAdmin(httpRequest);
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Called by order-service (via Feign, forwarding the buyer's JWT) once a
     * purchase is confirmed, to mint the short-lived secure download link.
     */
    @PostMapping("/{id}/download-token")
    public DownloadTokenResponse generateDownloadToken(@PathVariable Long id, HttpServletRequest httpRequest) {
        String username = (String) httpRequest.getAttribute(JwtAuthFilter.USERNAME_ATTRIBUTE);
        return productService.generateDownloadToken(id, username);
    }

    /**
     * The actual "download". Since this is a zero-physical-inventory
     * marketplace there's no real file on disk here - a valid download token
     * is treated as proof of purchase and we hand back a confirmation
     * payload instead of bytes.
     */
    @GetMapping("/{id}/download")
    public Map<String, Object> download(@PathVariable Long id, @RequestParam String token) {
        Product product = productService.validateDownload(id, token);
        return Map.of(
                "message", "Download authorized for '" + product.getName() + "'",
                "productId", product.getId(),
                "fileType", product.getFileType()
        );
    }

    private void requireAdmin(HttpServletRequest httpRequest) {
        String role = (String) httpRequest.getAttribute(JwtAuthFilter.ROLE_ATTRIBUTE);
        if (!"ADMIN".equals(role)) {
            throw new ForbiddenException("Only an ADMIN can manage the product catalog");
        }
    }
}
