package com.harding.feeds.auth;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.service.auth.JwtTokenService;
import com.harding.feeds.service.auth.JwtTokenService.InvalidTokenException;
import com.harding.feeds.service.auth.JwtTokenService.TokenPair;
import com.harding.feeds.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Refresh token rotation: each refresh yields a new pair and revokes the
 * presented refresh token, so a refresh token can be used exactly once.
 */
class JwtRefreshRotationTest extends IntegrationTest {

    @Autowired
    private JwtTokenService jwtTokenService;

    private AppUser user;
    private TokenPair initialPair;

    @BeforeEach
    void setUp() {
        user = userIn(group(), "parent@test.com");
        initialPair = jwtTokenService.generateTokenPair(user, null);
    }

    @Test
    void refreshIssuesANewPair() {
        TokenPair rotated = jwtTokenService.refreshAccessToken(initialPair.refreshToken(), null);

        assertNotNull(rotated.accessToken());
        assertNotNull(rotated.refreshToken());
        assertNotEquals(initialPair.refreshToken(), rotated.refreshToken());
    }

    @Test
    void aRefreshTokenCanOnlyBeUsedOnce() {
        jwtTokenService.refreshAccessToken(initialPair.refreshToken(), null);

        assertThrows(InvalidTokenException.class,
                () -> jwtTokenService.refreshAccessToken(initialPair.refreshToken(), null));
    }

    @Test
    void anAccessTokenCannotBeUsedAsARefreshToken() {
        assertThrows(InvalidTokenException.class,
                () -> jwtTokenService.refreshAccessToken(initialPair.accessToken(), null));
    }
}
