package com.harding.feeds.googlehome;

import com.harding.feeds.entity.Baby;
import com.harding.feeds.entity.Feed;
import com.harding.feeds.repository.FeedRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The Google Home state of a baby's virtual feed device, computed live from
 * the feed history. The single source for QUERY responses, EXECUTE result
 * states, and Report State payloads, so all three always agree.
 */
@Component
public class GoogleHomeDeviceStates {

    private final FeedRepository feedRepository;

    public GoogleHomeDeviceStates(FeedRepository feedRepository) {
        this.feedRepository = feedRepository;
    }

    /**
     * StartStop state plus the two query-only modes: the last breast side and
     * the bucketed time since the last feed. Mode entries are omitted while
     * the baby has no data for them. Callers must have scoped the baby.
     */
    public Map<String, Object> forBaby(Baby baby) {
        Optional<Feed> active = feedRepository.findFirstByBabyAndEndTimeIsNullOrderByStartTimeDesc(baby);

        Map<String, Object> modes = new HashMap<>();
        feedRepository
                .findFirstByBabyAndTypeAndSideIsNotNullOrderByStartTimeDesc(baby, Feed.Type.BREAST)
                .ifPresent(feed -> modes.put(GoogleHomeFulfillmentService.MODE_SIDE, settingName(feed.getSide())));
        recencyBucket(active, feedRepository.findFirstByBabyOrderByStartTimeDesc(baby))
                .ifPresent(bucket -> modes.put(GoogleHomeFulfillmentService.MODE_LAST_FEED, bucket));

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("online", true);
        state.put("isRunning", active.isPresent());
        state.put("isPaused", false);
        state.put("currentModeSettings", modes);
        return state;
    }

    /** The Modes setting_name for a side - must match the SYNC declaration. */
    static String settingName(Feed.Side side) {
        return side == Feed.Side.L ? "left" : "right";
    }

    /**
     * The "how long ago was the last feed" mode value, measured from the end
     * of the newest feed; empty when the baby has no feeds yet.
     */
    private Optional<String> recencyBucket(Optional<Feed> active, Optional<Feed> latest) {
        if (active.isPresent()) {
            return Optional.of("right_now");
        }
        return latest.map(feed -> {
            OffsetDateTime reference = feed.getEndTime() != null ? feed.getEndTime() : feed.getStartTime();
            long minutes = Duration.between(reference, OffsetDateTime.now()).toMinutes();
            if (minutes < 30) return "just_now";
            if (minutes < 60) return "under_an_hour";
            if (minutes < 120) return "one_to_two_hours";
            if (minutes < 240) return "two_to_four_hours";
            return "over_four_hours";
        });
    }
}
