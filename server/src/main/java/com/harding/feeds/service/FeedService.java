package com.harding.feeds.service;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.Baby;
import com.harding.feeds.entity.Feed;
import com.harding.feeds.repository.FeedRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.harding.feeds.service.GroupScope.notFound;
import static com.harding.feeds.service.GroupScope.requireInGroup;

/**
 * Feed CRUD, group-scoped: every operation resolves the target baby/feed and
 * verifies it belongs to the caller's family group before touching it. Every
 * mutation publishes a {@link FeedChangedEvent}.
 */
@Service
public class FeedService {

    private final FeedRepository feedRepository;
    private final BabyScope babyScope;
    private final InProgressEvents inProgressEvents;
    private final ApplicationEventPublisher eventPublisher;

    public FeedService(FeedRepository feedRepository, BabyScope babyScope,
                       InProgressEvents inProgressEvents,
                       ApplicationEventPublisher eventPublisher) {
        this.feedRepository = feedRepository;
        this.babyScope = babyScope;
        this.inProgressEvents = inProgressEvents;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<Feed> getFeeds(AppUser user, Long babyId, OffsetDateTime from, OffsetDateTime to,
                               OffsetDateTime updatedSince) {
        Baby baby = babyScope.require(user, babyId);
        return feedRepository.findForBaby(baby, from, to, updatedSince);
    }

    /**
     * Idempotent by id: replaying a create whose id already exists in the
     * caller's group succeeds and returns the existing feed unchanged, so
     * offline sync retries are safe.
     */
    @Transactional
    public Creation create(AppUser user, UUID id, Long babyId, Feed.Type type, Feed.Side side,
                           Integer amountMl, OffsetDateTime startTime, OffsetDateTime endTime) {
        Optional<Feed> existing = feedRepository.findById(id);
        if (existing.isPresent()) {
            Feed feed = existing.get();
            requireInGroup(user, feed.getBaby().getFamilyGroup(), "Feed");
            return new Creation(feed, false);
        }

        Baby baby = babyScope.require(user, babyId);
        // Only a feed that is itself in progress claims the exclusive slot; a
        // completed feed - every bottle, and any retrospective log - must not
        // end a live nap. Placed after the replay return above, so a resent
        // create never re-ends an event the user has since restarted.
        if (endTime == null) {
            inProgressEvents.endAll(baby, startTime);
        }
        Feed feed = feedRepository.save(new Feed(id, baby, type, side, amountMl, startTime, endTime, user));
        eventPublisher.publishEvent(new FeedChangedEvent(baby));
        return new Creation(feed, true);
    }

    /** Full replacement of the editable fields. Any group member may update any feed. */
    @Transactional
    public Feed update(AppUser user, UUID id, Long babyId, Feed.Type type, Feed.Side side,
                       Integer amountMl, OffsetDateTime startTime, OffsetDateTime endTime) {
        Feed feed = scopedFeed(user, id);

        feed.setBaby(babyScope.require(user, babyId));
        feed.setType(type);
        feed.setSide(side);
        feed.setAmountMl(amountMl);
        feed.setStartTime(startTime);
        feed.setEndTime(endTime);

        Feed saved = feedRepository.save(feed);
        eventPublisher.publishEvent(new FeedChangedEvent(saved.getBaby()));
        return saved;
    }

    @Transactional
    public void delete(AppUser user, UUID id) {
        Feed feed = scopedFeed(user, id);
        feedRepository.delete(feed);
        eventPublisher.publishEvent(new FeedChangedEvent(feed.getBaby()));
    }

    /**
     * Starts a breast feed now, on the given side or the computed next side:
     * the opposite of the latest breast feed's side, else L. Mirrors the
     * Android client's ActiveEventUseCase.defaultNextSide() - keep the two in
     * step. Returns empty when a feed is already in progress (nothing is
     * created); the id is server-generated, which is safe because creates are
     * idempotent by id and clients reconcile by range refetch.
     */
    @Transactional
    public Optional<Feed> startVoiceFeed(AppUser user, Long babyId, Feed.Side sideOverride) {
        Baby baby = babyScope.require(user, babyId);
        if (feedRepository.findFirstByBabyAndEndTimeIsNullOrderByStartTimeDesc(baby).isPresent()) {
            return Optional.empty();
        }

        OffsetDateTime now = OffsetDateTime.now();
        // A voice start is still a start, so it ends a running nap like any
        // other. Without this the server is the one place that can hold two
        // in-progress events, which is the invariant the client relies on.
        inProgressEvents.endAll(baby, now);

        Feed.Side side = sideOverride != null ? sideOverride : nextSide(baby);
        Feed feed = feedRepository.save(new Feed(UUID.randomUUID(), baby, Feed.Type.BREAST, side, null,
                now, null, user));
        eventPublisher.publishEvent(new FeedChangedEvent(baby));
        return Optional.of(feed);
    }

    /**
     * Ends the in-progress feed now. Returns the stopped feed, or empty when
     * nothing is in progress.
     */
    @Transactional
    public Optional<Feed> stopVoiceFeed(AppUser user, Long babyId) {
        Baby baby = babyScope.require(user, babyId);
        return feedRepository.findFirstByBabyAndEndTimeIsNullOrderByStartTimeDesc(baby)
                .map(feed -> {
                    OffsetDateTime now = OffsetDateTime.now();
                    feed.setEndTime(now.isBefore(feed.getStartTime()) ? feed.getStartTime() : now);
                    Feed saved = feedRepository.save(feed);
                    eventPublisher.publishEvent(new FeedChangedEvent(baby));
                    return saved;
                });
    }

    private Feed.Side nextSide(Baby baby) {
        return feedRepository
                .findFirstByBabyAndTypeAndSideIsNotNullOrderByStartTimeDesc(baby, Feed.Type.BREAST)
                .map(feed -> feed.getSide().opposite())
                .orElse(Feed.Side.L);
    }

    private Feed scopedFeed(AppUser user, UUID id) {
        Feed feed = feedRepository.findById(id)
                .orElseThrow(() -> notFound("Feed not found"));
        requireInGroup(user, feed.getBaby().getFamilyGroup(), "Feed");
        return feed;
    }

    /** Outcome of an idempotent create: {@code created} is false on a replay. */
    public record Creation(Feed feed, boolean created) {
    }
}
