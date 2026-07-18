package com.harding.feeds.config.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.UUID;

/**
 * RSA keypair exposed as Nimbus encoder/decoder beans; the resource server
 * verifies our own tokens with the in-memory public key.
 *
 * The signing key is loaded from JWT_PRIVATE_KEY (base64-encoded PKCS#8, PEM
 * armour tolerated) so it survives restarts — regenerating per boot would
 * invalidate every access AND refresh token on each deploy, silently signing
 * users out (the deploy loop is `rollout restart`). When the property is unset
 * (localdev, tests) a keypair is generated so `bootRun` still works.
 *
 * Future: publish a JWKS with `kid` for staged rotation if a second key is ever
 * needed. Not required while a single instance both issues and verifies tokens.
 */
@Configuration
public class JwtKeyConfig {

    private final KeyPair keyPair;

    public JwtKeyConfig(@Value("${JWT_PRIVATE_KEY:}") String privateKeyBase64) throws GeneralSecurityException {
        this.keyPair = privateKeyBase64.isBlank()
                ? generateKeyPair()
                : loadKeyPair(privateKeyBase64);
    }

    private static KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }

    /**
     * Rebuild the keypair from a PKCS#8 private key. The matching public key is
     * derived from the private key's modulus and public exponent (RSA CRT), so
     * only the private key needs to be stored in the secret.
     */
    private static KeyPair loadKeyPair(String privateKeyBase64) throws GeneralSecurityException {
        byte[] der = Base64.getDecoder().decode(stripPem(privateKeyBase64));
        KeyFactory rsa = KeyFactory.getInstance("RSA");
        RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) rsa.generatePrivate(new PKCS8EncodedKeySpec(der));
        RSAPublicKey publicKey = (RSAPublicKey) rsa.generatePublic(
                new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
        return new KeyPair(publicKey, privateKey);
    }

    private static String stripPem(String value) {
        return value.replaceAll("-----BEGIN (RSA )?PRIVATE KEY-----", "")
                .replaceAll("-----END (RSA )?PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();

        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }
}
