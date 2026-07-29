package com.harding.feeds.googlehome;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.harding.feeds.entity.AppUser;
import com.harding.feeds.properties.JwtProperties;
import com.harding.feeds.repository.AppUserRepository;
import com.harding.feeds.service.auth.VerifyGoogleJwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

/**
 * OAuth2 account linking for Google Home: the authorization endpoint (a
 * Google sign-in page), its consent callback, and the token endpoint.
 *
 * These are the one deliberate exception to the contract-first rule: the
 * shapes are fixed by Google's cloud-to-cloud account-linking protocol and
 * are consumed by Google, not the Android client, so they live outside
 * model/openapi.yaml as hand-written endpoints.
 */
@RestController
public class GoogleHomeAuthController {

    private static final Logger log = LoggerFactory.getLogger(GoogleHomeAuthController.class);

    private final GoogleHomeProperties properties;
    private final AuthCodeStore authCodeStore;
    private final GoogleHomeTokenService tokenService;
    private final VerifyGoogleJwtService verifyGoogleJwtService;
    private final AppUserRepository appUserRepository;
    private final JwtProperties jwtProperties;
    private final String gisClientId;

    public GoogleHomeAuthController(
            GoogleHomeProperties properties,
            AuthCodeStore authCodeStore,
            GoogleHomeTokenService tokenService,
            VerifyGoogleJwtService verifyGoogleJwtService,
            AppUserRepository appUserRepository,
            JwtProperties jwtProperties,
            @Value("${oauth.google-client-id}") String gisClientId) {
        this.properties = properties;
        this.authCodeStore = authCodeStore;
        this.tokenService = tokenService;
        this.verifyGoogleJwtService = verifyGoogleJwtService;
        this.appUserRepository = appUserRepository;
        this.jwtProperties = jwtProperties;
        this.gisClientId = gisClientId;
    }

    /**
     * The authorization endpoint Google opens in the Home app's link flow.
     * Validates the request before rendering the sign-in page.
     */
    @GetMapping(value = "/googlehome/auth", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> authorize(
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "response_type", required = false) String responseType) {
        if (!properties.getClientId().equals(clientId)
                || !"code".equals(responseType)
                || !properties.allowedRedirectUris().contains(redirectUri)) {
            log.warn("Rejected Google Home authorize request (client_id={}, redirect_uri={})",
                    clientId, redirectUri);
            return ResponseEntity.badRequest().body("Invalid authorization request");
        }
        return ResponseEntity.ok(linkPage());
    }

    /**
     * Consent callback from the sign-in page: verifies the Google ID token,
     * requires an existing app user who has a family group (linking never
     * creates accounts), and answers the redirect URL carrying the code.
     */
    @PostMapping("/googlehome/auth/consent")
    public ResponseEntity<?> consent(@RequestBody ConsentRequest request) {
        if (!properties.getClientId().equals(request.clientId())
                || !properties.allowedRedirectUris().contains(request.redirectUri())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid authorization request"));
        }

        String subject;
        try {
            subject = verifyGoogleJwtService.verify(request.credential()).getPayload().getSubject();
        } catch (Exception e) {
            log.warn("Google Home consent with unverifiable credential: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Google sign-in could not be verified"));
        }

        Optional<AppUser> user = Optional.ofNullable(appUserRepository.findByUsername(subject))
                .filter(u -> u.getFamilyGroup() != null);
        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error",
                    "No linked Feeds account. Sign in to the Feeds app and join your family group first."));
        }

        String code = authCodeStore.issue(user.get().getId(), request.redirectUri());
        String redirectUrl = request.redirectUri()
                + "?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                + "&state=" + URLEncoder.encode(request.state() == null ? "" : request.state(), StandardCharsets.UTF_8);
        return ResponseEntity.ok(Map.of("redirectUrl", redirectUrl));
    }

    /**
     * The token endpoint. Google exchanges an authorization code for an
     * access/refresh pair, then trades the refresh token for fresh access
     * tokens from then on. The refresh token never expires and never
     * rotates, as the protocol requires.
     */
    @PostMapping(value = "/googlehome/auth/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> token(
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "client_secret", required = false) String clientSecret,
            @RequestParam(name = "grant_type", required = false) String grantType,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "refresh_token", required = false) String refreshToken) {
        if (!constantTimeEquals(properties.getClientId(), clientId)
                || !constantTimeEquals(properties.getClientSecret(), clientSecret)) {
            return invalidGrant();
        }

        return switch (grantType == null ? "" : grantType) {
            case "authorization_code" -> authCodeStore.redeem(code, redirectUri)
                    .flatMap(appUserRepository::findById)
                    .map(user -> tokenResponse(user, tokenService.issueRefreshToken(user)))
                    .orElseGet(this::invalidGrant);
            case "refresh_token" -> tokenService.userForRefreshToken(refreshToken)
                    .map(user -> tokenResponse(user, null))
                    .orElseGet(this::invalidGrant);
            default -> invalidGrant();
        };
    }

    private ResponseEntity<Object> tokenResponse(AppUser user, String refreshToken) {
        return ResponseEntity.ok(new TokenResponse(
                "Bearer",
                tokenService.mintAccessToken(user),
                refreshToken,
                jwtProperties.getAccessTokenValidity()));
    }

    private ResponseEntity<Object> invalidGrant() {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid_grant"));
    }

    private boolean constantTimeEquals(String expected, String presented) {
        if (expected == null || expected.isEmpty() || presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }

    private String linkPage() {
        try {
            String template = new ClassPathResource("googlehome/link.html")
                    .getContentAsString(StandardCharsets.UTF_8);
            return template.replace("__GIS_CLIENT_ID__", gisClientId);
        } catch (IOException e) {
            throw new UncheckedIOException("googlehome/link.html missing from classpath", e);
        }
    }

    public record ConsentRequest(String credential, String clientId, String redirectUri, String state) {
    }

    /** Shape fixed by Google's account-linking protocol; refreshToken omitted on refresh grants. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TokenResponse(
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") long expiresIn) {
    }
}
