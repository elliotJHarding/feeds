package com.harding.feeds.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One nap. Deliberate mirror of {@link Feed} minus type, side and amount - a
 * nap has none of those. The id is generated client-side so offline sync
 * retries are idempotent. A null endTime means the nap is in progress.
 * Duration is always derived from endTime - startTime and never stored.
 *
 * <p>Naps are a separate entity rather than a third Feed.Type because no feed
 * query carries a type filter - {@code FeedRepository.findForBaby} and the
 * Google Home state finders among them. A nap sharing that table would enter
 * all of them silently, and a missed filter is a wrong number rather than an
 * error.
 */
@Entity
@Table(indexes = {
        @Index(name = "idx_nap_baby_start", columnList = "baby_id, start_time"),
        @Index(name = "idx_nap_baby_updated", columnList = "baby_id, updated_at")
})
public class Nap {

    /** Client-generated UUID, stored verbatim. */
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "baby_id", nullable = false)
    private Baby baby;

    @Column(nullable = false)
    private OffsetDateTime startTime;

    /** Null while the nap is in progress. */
    private OffsetDateTime endTime;

    /** Display-only; any group member may edit or delete any nap. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    /** Server-set on every write; drives updatedSince sync and last-write-wins. */
    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    public Nap() {
    }

    public Nap(UUID id, Baby baby, OffsetDateTime startTime, OffsetDateTime endTime, AppUser createdBy) {
        this.id = id;
        this.baby = baby;
        this.startTime = startTime;
        this.endTime = endTime;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Baby getBaby() {
        return baby;
    }

    public void setBaby(Baby baby) {
        this.baby = baby;
    }

    public OffsetDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(OffsetDateTime startTime) {
        this.startTime = startTime;
    }

    public OffsetDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(OffsetDateTime endTime) {
        this.endTime = endTime;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(AppUser createdBy) {
        this.createdBy = createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
