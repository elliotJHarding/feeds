package com.harding.feeds.feed;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.Baby;
import com.harding.feeds.entity.FamilyGroup;
import com.harding.feeds.entity.Feed;
import com.harding.feeds.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /feeds: newest first, in-progress feeds included, optional startTime
 * window (from inclusive, to exclusive) and updatedSince incremental filter.
 */
class FeedQueryTest extends IntegrationTest {

    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 7, 17, 0, 0, 0, 0, ZoneOffset.UTC);

    private AppUser parent;
    private Baby baby;

    @BeforeEach
    void setUp() {
        FamilyGroup family = group();
        parent = userIn(family, "parent@test.com");
        baby = babyIn(family);
    }

    @Test
    void returnsFeedsNewestFirst_includingInProgressFeeds() throws Exception {
        savedFeed(baby, parent, T0.plusHours(1), T0.plusHours(1).plusMinutes(20));
        Feed inProgress = savedFeed(baby, parent, T0.plusHours(4), null);

        getAs(parent, "/feeds?babyId=" + baby.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(inProgress.getId().toString()))
                .andExpect(jsonPath("$[0].endTime").isEmpty());
    }

    @Test
    void windowIsFromInclusiveToExclusive() throws Exception {
        savedFeed(baby, parent, T0.minusHours(1), null);          // before window
        Feed atFrom = savedFeed(baby, parent, T0, null);          // on 'from' - included
        savedFeed(baby, parent, T0.plusHours(6), null);           // on 'to' - excluded

        getAs(parent, "/feeds?babyId=" + baby.getId()
                + "&from=" + T0 + "&to=" + T0.plusHours(6))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(atFrom.getId().toString()));
    }

    @Test
    void updatedSinceReturnsOnlyWritesAfterTheGivenInstant() throws Exception {
        savedFeed(baby, parent, T0, T0.plusMinutes(15));

        // UTC so the query parameter ends in 'Z' - a '+01:00' offset's '+'
        // would decode as a space in the query string.
        OffsetDateTime pollCursor = OffsetDateTime.now(ZoneOffset.UTC);
        Thread.sleep(10); // ensure the next write's server-set updatedAt is strictly later

        Feed newWrite = savedFeed(baby, parent, T0.plusHours(3), null);

        getAs(parent, "/feeds?babyId=" + baby.getId() + "&updatedSince=" + pollCursor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(newWrite.getId().toString()));
    }
}
