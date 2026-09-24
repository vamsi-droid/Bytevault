package com.bytevault.productservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Issues and validates secure download tokens. Deliberately separate from the
 * login JWT (different secret, own claim shape, much shorter expiry) so a
 * leaked download link can't be replayed forever and carries no login
 * authority at all.
 */
@Component
public class DownloadTokenUtil {

    private final SecretKey signingKey;
    private final long expirationMs;

    public DownloadTokenUtil(@Value("${download.jwt.secret}") String secret,
                              @Value("${download.jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long productId, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(username)
                .claim("productId", productId)
                .claim("purpose", "download")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValidForProduct(String token, Long productId) {
        try {
            Claims claims = parseClaims(token);
            Number claimedProductId = claims.get("productId", Number.class);
            return "download".equals(claims.get("purpose", String.class))
                    && claimedProductId != null
                    && claimedProductId.longValue() == productId;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
