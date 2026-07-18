package com.harding.feeds.service.auth;

import com.harding.feeds.entity.AppJwtToken;
import com.harding.feeds.entity.AppUser;
import com.harding.feeds.properties.JwtProperties;
import com.harding.feeds.repository.AppJwtTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Issues, refreshes, and revokes the app's own JWTs.
 *
 * Access tokens are short-lived and stateless; refresh tokens are long-lived
 * and tracked by jti in the database so they can be revoked and rotated.
 */
@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtProperties jwtProperties;
    private final AppJwtTokenRepository tokenRepository;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            JwtDecoder jwtDecoder,
            JwtProperties jwtProperties,
            AppJwtTokenRepository tokenRepository) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.jwtProperties = jwtProperties;
        this.tokenRepository = tokenRepository;
    }

    @Transactional
    public TokenPair generateTokenPair(AppUser user, HttpServletRequest request) {
        log.debug("Generating token pair for user: {}", user.getEmail());

        String accessToken = generateAccessToken(user);
        String refreshToken = generateRefreshToken(user, request);

        return new TokenPair(accessToken, refreshToken, jwtProperties.getAccessTokenValidity());
    }

    private String generateAccessToken(AppUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.getAccessTokenValidity(), ChronoUnit.SECONDS);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .audience(List.of(jwtProperties.getAudience()))
                .subject(user.getEmail())
                .issuedAt(now)
                .expiresAt(expiry)
                .claim("userId", user.getId())
                .claim("email", user.getEmail())
                .claim("type", "access")
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private String generateRefreshToken(AppUser user, HttpServletRequest request) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.getRefreshTokenValidity(), ChronoUnit.SECONDS);
        String jti = UUID.randomUUID().toString();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .audience(List.of(jwtProperties.getAudience()))
                .subject(user.getEmail())
                .issuedAt(now)
                .expiresAt(expiry)
                .id(jti)
                .claim("userId", user.getId())
                .claim("type", "refresh")
                .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        AppJwtToken tokenEntity = new AppJwtToken();
        tokenEntity.setJti(jti);
        tokenEntity.setTokenType(AppJwtToken.TokenType.REFRESH);
        tokenEntity.setUser(user);
        tokenEntity.setExpiresAt(OffsetDateTime.ofInstant(expiry, ZoneOffset.UTC));
        tokenEntity.setCreatedAt(OffsetDateTime.now());
        tokenEntity.setRevoked(false);

        if (request != null) {
            tokenEntity.setIpAddress(getClientIp(request));
            tokenEntity.setUserAgent(request.getHeader("User-Agent"));
        }

        tokenRepository.save(tokenEntity);
        log.debug("Stored refresh token for user {} (jti: {}, expires at: {})", user.getEmail(), jti, expiry);

        return token;
    }

    /**
     * Exchanges a valid refresh token for a new pair. With rotation enabled
     * the presented token is revoked, so it can be used exactly once.
     */
    @Transactional
    public TokenPair refreshAccessToken(String refreshToken, HttpServletRequest request) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(refreshToken);
        } catch (JwtException e) {
            log.warn("Failed to decode refresh token: {}", e.getMessage());
            throw new InvalidTokenException("Invalid refresh token", e);
        }

        if (!"refresh".equals(jwt.getClaim("type"))) {
            throw new InvalidTokenException("Token is not a refresh token");
        }

        String jti = jwt.getId();
        AppJwtToken tokenEntity = tokenRepository.findByJti(jti)
                .orElseThrow(() -> new InvalidTokenException("Refresh token not found"));

        if (tokenEntity.isRevoked()) {
            log.warn("Attempted to use revoked refresh token (jti: {}, user: {})",
                    jti, tokenEntity.getUser().getEmail());
            throw new InvalidTokenException("Refresh token has been revoked");
        }

        if (tokenEntity.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new InvalidTokenException("Refresh token has expired");
        }

        AppUser user = tokenEntity.getUser();

        String newAccessToken = generateAccessToken(user);

        String newRefreshToken = null;
        if (jwtProperties.isEnableTokenRotation()) {
            tokenEntity.setRevoked(true);
            tokenEntity.setRevokedAt(OffsetDateTime.now());
            tokenRepository.save(tokenEntity);

            newRefreshToken = generateRefreshToken(user, request);
        }

        return new TokenPair(newAccessToken, newRefreshToken, jwtProperties.getAccessTokenValidity());
    }

    @Transactional
    public void revokeAllUserTokens(AppUser user) {
        log.info("Revoking all tokens for user: {}", user.getEmail());
        tokenRepository.revokeAllForUser(user.getId());
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * @param refreshToken may be null when rotation is disabled
     * @param expiresIn    seconds until the access token expires
     */
    public record TokenPair(String accessToken, String refreshToken, long expiresIn) {
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }

        public InvalidTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
