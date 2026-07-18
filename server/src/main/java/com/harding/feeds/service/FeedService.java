package com.harding.feeds.service;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.Baby;
import com.harding.feeds.entity.Feed;
import com.harding.feeds.repository.BabyRepository;
import com.harding.feeds.repository.FeedRepository;
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
 * verifies it belongs to the caller's family group before touching it.
 */
@Service
public class FeedService {

    private final FeedRepository feedRepository;
    private final BabyRepository babyRepository;

    public FeedService(FeedRepository feedRepository, BabyRepository babyRepository) {
        this.feedRepository = feedRepository;
        this.babyRepository = babyRepository;
    }

    @Transactional(readOnly = true)
    public List<Feed> getFeeds(AppUser user, Long babyId, OffsetDateTime from, OffsetDateTime to,
                               OffsetDateTime updatedSince) {
        Baby baby = scopedBaby(user, babyId);
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

        Baby baby = scopedBaby(user, babyId);
        Feed feed = new Feed(id, baby, type, side, amountMl, startTime, endTime, user);
        return new Creation(feedRepository.save(feed), true);
    }

    /** Full replacement of the editable fields. Any group member may update any feed. */
    @Transactional
    public Feed update(AppUser user, UUID id, Long babyId, Feed.Type type, Feed.Side side,
                       Integer amountMl, OffsetDateTime startTime, OffsetDateTime endTime) {
        Feed feed = scopedFeed(user, id);

        feed.setBaby(scopedBaby(user, babyId));
        feed.setType(type);
        feed.setSide(side);
        feed.setAmountMl(amountMl);
        feed.setStartTime(startTime);
        feed.setEndTime(endTime);

        return feedRepository.save(feed);
    }

    @Transactional
    public void delete(AppUser user, UUID id) {
        feedRepository.delete(scopedFeed(user, id));
    }

    private Feed scopedFeed(AppUser user, UUID id) {
        Feed feed = feedRepository.findById(id)
                .orElseThrow(() -> notFound("Feed not found"));
        requireInGroup(user, feed.getBaby().getFamilyGroup(), "Feed");
        return feed;
    }

    private Baby scopedBaby(AppUser user, Long babyId) {
        Baby baby = babyRepository.findById(babyId)
                .orElseThrow(() -> notFound("Baby not found"));
        requireInGroup(user, baby.getFamilyGroup(), "Baby");
        return baby;
    }

    /** Outcome of an idempotent create: {@code created} is false on a replay. */
    public record Creation(Feed feed, boolean created) {
    }
}
