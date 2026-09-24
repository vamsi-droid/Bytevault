package com.bytevault.productservice.service;

import com.bytevault.productservice.dto.DownloadTokenResponse;
import com.bytevault.productservice.dto.ProductRequest;
import com.bytevault.productservice.entity.Product;
import com.bytevault.productservice.exception.InvalidDownloadTokenException;
import com.bytevault.productservice.exception.ProductNotFoundException;
import com.bytevault.productservice.repository.ProductRepository;
import com.bytevault.productservice.security.DownloadTokenUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final DownloadTokenUtil downloadTokenUtil;

    public ProductService(ProductRepository productRepository, DownloadTokenUtil downloadTokenUtil) {
        this.productRepository = productRepository;
        this.downloadTokenUtil = downloadTokenUtil;
    }

    public List<Product> listAll() {
        return productRepository.findAll();
    }

    public Product getById(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public Product create(ProductRequest request) {
        Product product = new Product(request.getName(), request.getPrice(), request.getFileType());
        return productRepository.save(product);
    }

    @Transactional
    public Product update(Long id, ProductRequest request) {
        Product product = getById(id);
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setFileType(request.getFileType());
        return productRepository.save(product);
    }

    @Transactional
    public void delete(Long id) {
        Product product = getById(id);
        productRepository.delete(product);
    }

    public DownloadTokenResponse generateDownloadToken(Long id, String username) {
        Product product = getById(id);
        String token = downloadTokenUtil.generateToken(product.getId(), username);
        String url = "/api/products/" + product.getId() + "/download?token=" + token;
        Instant expiresAt = Instant.now().plusMillis(downloadTokenUtil.getExpirationMs());
        return new DownloadTokenResponse(product.getId(), token, url, expiresAt);
    }

    public Product validateDownload(Long id, String token) {
        Product product = getById(id);
        if (token == null || !downloadTokenUtil.isValidForProduct(token, id)) {
            throw new InvalidDownloadTokenException();
        }
        return product;
    }
}
