package com.harding.feeds.nap;

import com.harding.feeds.dto.Side;
import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.Baby;
import com.harding.feeds.entity.FamilyGroup;
import com.harding.feeds.entity.Feed;
import com.harding.feeds.entity.Nap;
import com.harding.feeds.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The one genuinely new rule: at most one event - feed or nap - is in progress
 * for a baby at a time. Starting either ends whatever else is running, at the
 * new event's start time.
 *
 * <p>Only a <em>start</em> enforces this. A create that is already complete is
 * a retrospective log, an idempotent replay is not a start at all, and an
 * update is a correction of history - none of them end anything.
 */
class ActiveEventExclusivityTest extends IntegrationTest {

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 7, 17, 13, 0, 0, 0, ZoneOffset.UTC);

    private AppUser parent;
    private Baby baby;

    @BeforeEach
    void setUp() {
        FamilyGroup family = group();
        parent = userIn(family, "parent@test.com");
        baby = babyIn(family);
    }

    private Feed reload(Feed feed) {
        return feedRepository.findById(feed.getId()).orElseThrow();
    }

    private Nap reload(Nap nap) {
        return napRepository.findById(nap.getId()).orElseThrow();
    }

    @Test
    void startingANap_endsTheRunningFeed_atTheNapsStartTime() throws Exception {
        Feed running = savedFeed(baby, parent, T0, null);

        postAs(parent, "/naps", napDto(UUID.randomUUID(), baby.getId(), T0.plusMinutes(20), null))
                .andExpect(status().isCreated());

        assertEquals(T0.plusMinutes(20), reload(running).getEndTime());
    }

    @Test
    void startingAFeed_endsTheRunningNap_atTheFeedsStartTime() throws Exception {
        Nap running = savedNap(baby, parent, T0, null);

        postAs(parent, "/feeds",
                breastFeedDto(UUID.randomUUID(), baby.getId(), Side.L, T0.plusHours(2), null))
                .andExpect(status().isCreated());

        assertEquals(T0.plusHours(2), reload(running).getEndTime());
    }

    /**
     * A backdated start clamps to the running event's own start rather than
     * being refused. Overlaps are tolerated all over this codebase; negative
     * durations are not.
     */
    @Test
    void aStartBeforeTheRunningEventsStart_clampsInsteadOfGoingNegative() throws Exception {
        Feed running = savedFeed(baby, parent, T0, null);

        postAs(parent, "/naps", napDto(UUID.randomUUID(), baby.getId(), T0.minusMinutes(30), null))
                .andExpect(status().isCreated());

        assertEquals(T0, reload(running).getEndTime());
    }

    @Test
    void creatingAnAlreadyCompleteNap_endsNothing() throws Exception {
        Feed running = savedFeed(baby, parent, T0, null);

        postAs(parent, "/naps",
                napDto(UUID.randomUUID(), baby.getId(), T0.plusMinutes(20), T0.plusMinutes(50)))
                .andExpect(status().isCreated());

        assertNull(reload(running).getEndTime());
    }

    /**
     * The feed-side half of the retrospective-log rule. This is the shape every
     * bottle takes - the client writes endTime equal to startTime so a bottle
     * can never read as in progress - so a bottle must never end a nap either.
     */
    @Test
    void creatingAnAlreadyCompleteFeed_endsNothing() throws Exception {
        Nap running = savedNap(baby, parent, T0, null);

        postAs(parent, "/feeds", breastFeedDto(UUID.randomUUID(), baby.getId(), Side.L,
                T0.plusMinutes(20), T0.plusMinutes(20)))
                .andExpect(status().isCreated());

        assertNull(reload(running).getEndTime());
    }

    /**
     * The regression guard on placing the rule after the idempotency
     * early-return. SyncEngine treats a 200 replay as a normal outcome, so a
     * replay that re-ended a feed the parent had since restarted would be a
     * silent corruption reachable in ordinary use.
     */
    @Test
    void replayingANapCreate_endsNothing() throws Exception {
        UUID napId = UUID.randomUUID();
        postAs(parent, "/naps", napDto(napId, baby.getId(), T0, null))
                .andExpect(status().isCreated());

        Feed restarted = savedFeed(baby, parent, T0.plusHours(1), null);

        postAs(parent, "/naps", napDto(napId, baby.getId(), T0, null))
                .andExpect(status().isOk());

        assertNull(reload(restarted).getEndTime());
    }

    @Test
    void updatingANap_endsNothing() throws Exception {
        Nap nap = savedNap(baby, parent, T0, T0.plusMinutes(40));
        Feed running = savedFeed(baby, parent, T0.plusHours(2), null);

        putAs(parent, "/naps/" + nap.getId(),
                napDto(nap.getId(), baby.getId(), T0.plusHours(3), null))
                .andExpect(status().isOk());

        assertNull(reload(running).getEndTime());
    }

    @Test
    void startingANapWithNothingRunning_touchesNoOtherRow() throws Exception {
        Feed finished = savedFeed(baby, parent, T0, T0.plusMinutes(20));

        postAs(parent, "/naps", napDto(UUID.randomUUID(), baby.getId(), T0.plusHours(1), null))
                .andExpect(status().isCreated());

        assertEquals(T0.plusMinutes(20), reload(finished).getEndTime());
    }

    /** Guards against implementing endAll as "any in-progress feed" rather than scoping to the baby. */
    @Test
    void startingANap_doesNotTouchAnotherGroupsRunningFeed() throws Exception {
        FamilyGroup otherFamily = group();
        AppUser otherParent = userIn(otherFamily, "other@family.com");
        Baby otherBaby = babyIn(otherFamily);
        Feed otherRunning = savedFeed(otherBaby, otherParent, T0, null);

        postAs(parent, "/naps", napDto(UUID.randomUUID(), baby.getId(), T0.plusMinutes(20), null))
                .andExpect(status().isCreated());

        assertNull(reload(otherRunning).getEndTime());
    }
}
