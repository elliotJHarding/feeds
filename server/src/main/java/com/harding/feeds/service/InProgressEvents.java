package com.harding.feeds.service;

import com.harding.feeds.entity.Baby;
import com.harding.feeds.repository.FeedRepository;
import com.harding.feeds.repository.NapRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * At most one event - feed or nap - is in progress for a baby at a time.
 * Starting either ends whatever else is running, at the new event's start time.
 *
 * <p>This lives in its own component rather than in either service because
 * {@link FeedService} would otherwise need {@link NapService} and NapService
 * would need FeedService, and Spring fails startup on that constructor cycle.
 * A third component both depend on breaks it and keeps the rule written once.
 *
 * <p>The rule is a pure function of (the new start time, the set of rows with a
 * null endTime), which is why the Android client can apply it locally and
 * offline and still converge with the server without coordinating: the start
 * time travels in the create body, so both sides compute the same end time.
 */
@Component
class InProgressEvents {

    private final FeedRepository feedRepository;
    private final NapRepository napRepository;
    private final ApplicationEventPublisher eventPublisher;

    InProgressEvents(FeedRepository feedRepository, NapRepository napRepository,
                     ApplicationEventPublisher eventPublisher) {
        this.feedRepository = feedRepository;
        this.napRepository = napRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Ends any in-progress feed and any in-progress nap for this baby at
     * {@code at}, clamped so an event never ends before it started.
     *
     * <p>Ending a feed publishes {@link FeedChangedEvent} because
     * GoogleHomeStateReportListener is the only trigger for the Home Graph
     * state push. Save the feed without it and Google Home reports the feed as
     * still running indefinitely, with no error anywhere to say so.
     *
     * <p>Ends both types rather than only the other one, so a duplicate
     * in-progress row of the same type is tidied up too - nothing in the schema
     * enforces at most one.
     */
    void endAll(Baby baby, OffsetDateTime at) {
        feedRepository.findFirstByBabyAndEndTimeIsNullOrderByStartTimeDesc(baby)
                .ifPresent(feed -> {
                    feed.setEndTime(notBefore(at, feed.getStartTime()));
                    feedRepository.save(feed);
                    eventPublisher.publishEvent(new FeedChangedEvent(baby));
                });

        napRepository.findFirstByBabyAndEndTimeIsNullOrderByStartTimeDesc(baby)
                .ifPresent(nap -> {
                    nap.setEndTime(notBefore(at, nap.getStartTime()));
                    napRepository.save(nap);
                });
    }

    /**
     * A backdated start clamps to the running event's own start rather than
     * being refused: refusing would break offline-first entry, and a
     * zero-length interval beats a negative one. Same clamp as
     * FeedService.stopVoiceFeed and the client's ActiveEventUseCase.
     */
    private static OffsetDateTime notBefore(OffsetDateTime at, OffsetDateTime start) {
        return at.isBefore(start) ? start : at;
    }
}
