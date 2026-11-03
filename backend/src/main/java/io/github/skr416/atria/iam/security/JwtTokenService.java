package io.github.skr416.atria.iam.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Service managing JWT generation, cryptographic verification, and claim extraction (ADR-0004).
 * Issues standard access tokens, restricted password-change tokens, and HttpOnly refresh tokens.
 */
@Slf4j
@Getter
@Service
public class JwtTokenService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_PWD_CHANGE = "pwd_change";
    public static final String TYPE_REFRESH = "refresh";

    @Value("${atria.security.jwt.secret:local-dev-only-jwt-secret-change-me-0123456789abcdef}")
    private String secret;

    @Value("${atria.security.jwt.access-ttl-minutes:15}")
    private long accessTtlMinutes;

    @Value("${atria.security.jwt.refresh-ttl-days:7}")
    private long refreshTtlDays;

    @Value("${atria.security.jwt.password-change-ttl-minutes:10}")
    private long passwordChangeTtlMinutes;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT secret (atria.security.jwt.secret) must not be blank.");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes (256 bits) for HMAC-SHA256.");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        log.info("JwtTokenService initialized with HS256 secret key (access: {}m, refresh: {}d, pwd_change: {}m).",
                accessTtlMinutes, refreshTtlDays, passwordChangeTtlMinutes);
    }

    /**
     * Issues a standard access token for normal authenticated operations.
     */
    public String generateAccessToken(Long userId, String username, long tokenVersion) {
        return generateToken(userId, username, tokenVersion, TYPE_ACCESS, false, Duration.ofMinutes(accessTtlMinutes));
    }

    /**
     * Issues a restricted short-lived token for forced password change gate.
     */
    public String generatePasswordChangeToken(Long userId, String username, long tokenVersion) {
        return generateToken(userId, username, tokenVersion, TYPE_PWD_CHANGE, true, Duration.ofMinutes(passwordChangeTtlMinutes));
    }

    /**
     * Issues a long-lived refresh token stored in an HttpOnly cookie.
     */
    public String generateRefreshToken(Long userId, String username, long tokenVersion) {
        return generateToken(userId, username, tokenVersion, TYPE_REFRESH, false, Duration.ofDays(refreshTtlDays));
    }

    /**
     * Generates a signed JWT with standard and custom Atria security claims.
     */
    public String generateToken(Long userId, String username, long tokenVersion, String tokenType, boolean mustChangePassword, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("tv", tokenVersion)
                .claim("typ", tokenType)
                .claim("mcp", mustChangePassword)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Parses and cryptographically validates the given JWT token.
     * Throws {@link JwtException} or {@link IllegalArgumentException} if invalid, expired, or tampered.
     */
    public TokenClaims parseClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String username = claims.getSubject();
        Long userId = null;
        Object uidObj = claims.get("uid");
        if (uidObj instanceof Number num) {
            userId = num.longValue();
        } else if (uidObj != null) {
            userId = Long.valueOf(uidObj.toString());
        }

        long tokenVersion = 0L;
        Object tvObj = claims.get("tv");
        if (tvObj instanceof Number num) {
            tokenVersion = num.longValue();
        } else if (tvObj != null) {
            tokenVersion = Long.parseLong(tvObj.toString());
        }

        String tokenType = claims.get("typ", String.class);
        Boolean mcp = claims.get("mcp", Boolean.class);
        boolean mustChangePassword = Boolean.TRUE.equals(mcp);

        long expiresInSeconds = 0;
        Date expiration = claims.getExpiration();
        if (expiration != null) {
            long diff = expiration.getTime() - System.currentTimeMillis();
            expiresInSeconds = Math.max(0, diff / 1000);
        }

        return new TokenClaims(
                userId,
                username,
                tokenVersion,
                tokenType,
                mustChangePassword,
                expiresInSeconds
        );
    }

    /**
     * Safely parses and validates a token without throwing unchecked JWT exceptions.
     */
    public Optional<TokenClaims> safeParseClaims(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(parseClaims(token));
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT token validation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public long getAccessTtlSeconds() {
        return accessTtlMinutes * 60;
    }

    public long getPasswordChangeTtlSeconds() {
        return passwordChangeTtlMinutes * 60;
    }

    public long getRefreshTtlSeconds() {
        return refreshTtlDays * 86400L;
    }

    /**
     * Typed representation of extracted JWT claims.
     */
    public record TokenClaims(
            Long userId,
            String username,
            long tokenVersion,
            String tokenType,
            boolean mustChangePassword,
            long expiresInSeconds
    ) {}
}
