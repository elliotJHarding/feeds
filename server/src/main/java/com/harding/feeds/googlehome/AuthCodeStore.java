package com.harding.feeds.googlehome;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single-use OAuth authorization codes for Google Home account linking,
 * held in memory: the server runs as a single replica and linking is a rare
 * interactive act, so losing pending codes on a restart just means retrying
 * the link in the Google Home app.
 */
@Component
public class AuthCodeStore {

    static final long VALIDITY_SECONDS = 600;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, PendingCode> codes = new ConcurrentHashMap<>();

    /** Issues a code bound to the user and the redirect URI it was authorized for. */
    public String issue(Long userId, String redirectUri) {
        sweep();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        codes.put(code, new PendingCode(userId, redirectUri, Instant.now().plusSeconds(VALIDITY_SECONDS)));
        return code;
    }

    /**
     * Redeems a code exactly once: valid only while unexpired and when
     * presented with the same redirect URI it was issued for.
     */
    public Optional<Long> redeem(String code, String redirectUri) {
        sweep();
        PendingCode pending = code == null ? null : codes.remove(code);
        if (pending == null
                || pending.expiresAt().isBefore(Instant.now())
                || !pending.redirectUri().equals(redirectUri)) {
            return Optional.empty();
        }
        return Optional.of(pending.userId());
    }

    private void sweep() {
        Instant now = Instant.now();
        codes.values().removeIf(pending -> pending.expiresAt().isBefore(now));
    }

    private record PendingCode(Long userId, String redirectUri, Instant expiresAt) {
    }
}
