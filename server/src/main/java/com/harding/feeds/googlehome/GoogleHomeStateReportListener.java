package com.harding.feeds.googlehome;

import com.harding.feeds.repository.GoogleHomeLinkRepository;
import com.harding.feeds.service.FeedChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Reports the changed baby's device state to Home Graph after every feed
 * mutation, but only while the family actually has a Google Home link (so
 * reporting stops after DISCONNECT). The state is computed synchronously in
 * the mutating transaction; the HTTP send is async inside the reporter.
 */
@Component
public class GoogleHomeStateReportListener {

    private final GoogleHomeStateReporter reporter;
    private final GoogleHomeDeviceStates deviceStates;
    private final GoogleHomeLinkRepository linkRepository;

    public GoogleHomeStateReportListener(GoogleHomeStateReporter reporter,
                                         GoogleHomeDeviceStates deviceStates,
                                         GoogleHomeLinkRepository linkRepository) {
        this.reporter = reporter;
        this.deviceStates = deviceStates;
        this.linkRepository = linkRepository;
    }

    @EventListener
    public void onFeedChanged(FeedChangedEvent event) {
        if (!reporter.enabled() || !linkRepository.existsByUserFamilyGroup(event.baby().getFamilyGroup())) {
            return;
        }
        reporter.report(
                event.baby().getFamilyGroup().getUuid().toString(),
                String.valueOf(event.baby().getId()),
                deviceStates.forBaby(event.baby()));
    }
}
