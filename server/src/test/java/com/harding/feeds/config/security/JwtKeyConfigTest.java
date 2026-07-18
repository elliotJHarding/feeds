package com.harding.feeds.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JwtKeyConfig loads a persisted signing key so tokens survive restarts. The
 * property that matters for the `rollout restart` deploy loop: a token signed by
 * one instance must still verify against another instance built from the same
 * key — otherwise every deploy silently signs users out.
 */
class JwtKeyConfigTest {

    private static String pkcs8Base64() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        // getEncoded() on an RSA private key is PKCS#8 DER — the format loadKeyPair parses.
        return Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
    }

    private static String sign(JwtEncoder encoder) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject("parent@test.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    @Test
    void tokenSignedBeforeARestartStillVerifiesAfter() throws Exception {
        String key = pkcs8Base64();

        String token = sign(new JwtKeyConfig(key).jwtEncoder());
        JwtDecoder afterRestart = new JwtKeyConfig(key).jwtDecoder();

        assertEquals("parent@test.com", afterRestart.decode(token).getSubject());
    }

    @Test
    void aTokenSignedWithADifferentKeyIsRejected() throws Exception {
        String token = sign(new JwtKeyConfig(pkcs8Base64()).jwtEncoder());
        JwtDecoder otherKey = new JwtKeyConfig(pkcs8Base64()).jwtDecoder();

        assertThrows(Exception.class, () -> otherKey.decode(token));
    }

    @Test
    void pemArmouredKeysAreTolerated() throws Exception {
        String pem = "-----BEGIN PRIVATE KEY-----\n" + pkcs8Base64() + "\n-----END PRIVATE KEY-----";

        assertNotNull(new JwtKeyConfig(pem).jwtEncoder());
    }

    @Test
    void anAbsentKeyFallsBackToAGeneratedOne() throws Exception {
        // Blank property (localdev / tests) must still produce working beans.
        assertNotNull(sign(new JwtKeyConfig("").jwtEncoder()));
    }
}
