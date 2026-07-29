package com.harding.feeds.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * A Google Home account link: the refresh token Google holds for a linked
 * user, stored as a SHA-256 hash of an opaque random secret (the raw value
 * is never stored).
 *
 * Deliberately not an {@link AppJwtToken}: Google requires refresh tokens
 * that never expire and are never rotated, so this must survive both
 * TokenCleanupService's expiry sweep and any future revoke-all-tokens path.
 * A row is deleted only by the Google Home DISCONNECT intent.
 */
@Entity
@Table(
        name = "google_home_link",
        indexes = {
                @Index(name = "idx_ghl_token_hash", columnList = "token_hash", unique = true),
                @Index(name = "idx_ghl_user", columnList = "user_id")
        }
)
public class GoogleHomeLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Hex-encoded SHA-256 of the opaque refresh token value. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
