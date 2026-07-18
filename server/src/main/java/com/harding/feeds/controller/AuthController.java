package com.harding.feeds.controller;

import com.harding.feeds.api.AuthenticationApi;
import com.harding.feeds.config.security.GoogleJwtAuthenticationToken;
import com.harding.feeds.dto.LoginRequest;
import com.harding.feeds.dto.LoginResponse;
import com.harding.feeds.dto.RefreshTokenRequest;
import com.harding.feeds.dto.TokenResponse;
import com.harding.feeds.entity.AppUser;
import com.harding.feeds.mapping.UserMapper;
import com.harding.feeds.service.auth.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthenticationApi {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final UserMapper userMapper;
    private final HttpServletRequest httpRequest;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtTokenService jwtTokenService,
                          UserMapper userMapper,
                          HttpServletRequest httpRequest) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.userMapper = userMapper;
        this.httpRequest = httpRequest;
    }

    @Override
    public ResponseEntity<LoginResponse> login(LoginRequest loginRequest) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    GoogleJwtAuthenticationToken.unauthenticated(loginRequest.getToken()));
        } catch (AuthenticationException e) {
            log.warn("Google ID token rejected: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AppUser user = (AppUser) authentication.getPrincipal();
        JwtTokenService.TokenPair tokenPair = jwtTokenService.generateTokenPair(user, httpRequest);

        return ResponseEntity.ok(new LoginResponse()
                .user(userMapper.toDto(user))
                .accessToken(tokenPair.accessToken())
                .refreshToken(tokenPair.refreshToken())
                .expiresIn(tokenPair.expiresIn())
                .tokenType("Bearer"));
    }

    @Override
    public ResponseEntity<TokenResponse> refreshToken(RefreshTokenRequest refreshTokenRequest) {
        try {
            JwtTokenService.TokenPair tokenPair =
                    jwtTokenService.refreshAccessToken(refreshTokenRequest.getRefreshToken(), httpRequest);

            return ResponseEntity.ok(new TokenResponse()
                    .accessToken(tokenPair.accessToken())
                    .refreshToken(tokenPair.refreshToken())
                    .expiresIn(tokenPair.expiresIn())
                    .tokenType("Bearer"));

        } catch (JwtTokenService.InvalidTokenException e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
