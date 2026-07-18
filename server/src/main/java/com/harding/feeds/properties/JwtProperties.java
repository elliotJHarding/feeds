package com.harding.feeds.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * JWT token configuration, prefix "app.jwt". Defaults mirror SPEC.md:
 * 15 minute access tokens, 90 day refresh tokens, rotation on.
 */
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Access token validity in seconds (default: 15 minutes). */
    private long accessTokenValidity = 900;

    /** Refresh token validity in seconds (default: 90 days). */
    private long refreshTokenValidity = 7776000;

    /** When enabled, each refresh issues a new refresh token and revokes the old one. */
    private boolean enableTokenRotation = true;

    private String issuer = "com.harding.feeds";

    private String audience = "feeds-android-app";

    public long getAccessTokenValidity() {
        return accessTokenValidity;
    }

    public void setAccessTokenValidity(long accessTokenValidity) {
        this.accessTokenValidity = accessTokenValidity;
    }

    public long getRefreshTokenValidity() {
        return refreshTokenValidity;
    }

    public void setRefreshTokenValidity(long refreshTokenValidity) {
        this.refreshTokenValidity = refreshTokenValidity;
    }

    public boolean isEnableTokenRotation() {
        return enableTokenRotation;
    }

    public void setEnableTokenRotation(boolean enableTokenRotation) {
        this.enableTokenRotation = enableTokenRotation;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }
}
