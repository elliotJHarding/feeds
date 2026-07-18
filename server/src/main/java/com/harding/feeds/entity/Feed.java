package com.harding.feeds.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * One feed on one side. The id is generated client-side so offline sync
 * retries are idempotent. A null endTime means the feed is in progress.
 * Duration is always derived from endTime - startTime and never stored.
 */
@Entity
@Table(indexes = {
        @Index(name = "idx_feed_baby_start", columnList = "baby_id, start_time"),
        @Index(name = "idx_feed_baby_updated", columnList = "baby_id, updated_at")
})
public class Feed {

    public enum Type { BREAST, BOTTLE }

    public enum Side { L, R }

    /** Client-generated UUID, stored verbatim. */
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "baby_id", nullable = false)
    private Baby baby;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Type type;

    /** Breast side; null for bottle feeds. */
    @Enumerated(EnumType.STRING)
    @Column(length = 1)
    private Side side;

    /** Bottle feeds only; null for breast feeds. */
    private Integer amountMl;

    @Column(nullable = false)
    private OffsetDateTime startTime;

    /** Null while the feed is in progress. */
    private OffsetDateTime endTime;

    /** Display-only; any group member may edit or delete any feed. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    /** Server-set on every write; drives updatedSince sync and last-write-wins. */
    @Column(nullable = false)
    private OffsetDateTime updatedAt;

    public Feed() {
    }

    public Feed(UUID id, Baby baby, Type type, Side side, Integer amountMl,
                OffsetDateTime startTime, OffsetDateTime endTime, AppUser createdBy) {
        this.id = id;
        this.baby = baby;
        this.type = type;
        this.side = side;
        this.amountMl = amountMl;
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

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Side getSide() {
        return side;
    }

    public void setSide(Side side) {
        this.side = side;
    }

    public Integer getAmountMl() {
        return amountMl;
    }

    public void setAmountMl(Integer amountMl) {
        this.amountMl = amountMl;
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
