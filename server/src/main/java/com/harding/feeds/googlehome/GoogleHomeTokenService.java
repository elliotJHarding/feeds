package com.harding.feeds.googlehome;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.GoogleHomeLink;
import com.harding.feeds.repository.GoogleHomeLinkRepository;
import com.harding.feeds.service.auth.JwtTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Tokens for the Google Home account link. The refresh token Google holds is
 * an opaque random secret - non-expiring and non-rotating as the protocol
 * requires - stored only as a SHA-256 hash. Access tokens are the app's
 * normal short-lived JWTs.
 */
@Service
public class GoogleHomeTokenService {

    private final GoogleHomeLinkRepository linkRepository;
    private final JwtTokenService jwtTokenService;
    private final SecureRandom random = new SecureRandom();

    public GoogleHomeTokenService(GoogleHomeLinkRepository linkRepository, JwtTokenService jwtTokenService) {
        this.linkRepository = linkRepository;
        this.jwtTokenService = jwtTokenService;
    }

    /** Mints a new refresh token for Google and stores its hash. */
    @Transactional
    public String issueRefreshToken(AppUser user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        GoogleHomeLink link = new GoogleHomeLink();
        link.setTokenHash(hash(token));
        link.setUser(user);
        linkRepository.save(link);

        return token;
    }

    /** The linked user a presented refresh token belongs to, if it is valid. */
    @Transactional(readOnly = true)
    public Optional<AppUser> userForRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            return Optional.empty();
        }
        return linkRepository.findByTokenHash(hash(refreshToken)).map(GoogleHomeLink::getUser);
    }

    public String mintAccessToken(AppUser user) {
        return jwtTokenService.generateAccessToken(user);
    }

    /** Unlinks the user: called by the DISCONNECT intent. */
    @Transactional
    public void revokeFor(AppUser user) {
        linkRepository.deleteByUser(user);
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
