package com.harding.feeds.nap;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.Baby;
import com.harding.feeds.entity.FamilyGroup;
import com.harding.feeds.entity.Nap;
import com.harding.feeds.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /naps: newest first, in-progress naps included, optional startTime
 * window (from inclusive, to exclusive) and updatedSince incremental filter.
 * Deliberate mirror of FeedQueryTest - keep the two in step.
 */
class NapQueryTest extends IntegrationTest {

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
    void returnsNapsNewestFirst_includingInProgressNaps() throws Exception {
        savedNap(baby, parent, T0.plusHours(1), T0.plusHours(2).plusMinutes(30));
        Nap inProgress = savedNap(baby, parent, T0.plusHours(6), null);

        getAs(parent, "/naps?babyId=" + baby.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(inProgress.getId().toString()))
                .andExpect(jsonPath("$[0].endTime").isEmpty());
    }

    @Test
    void windowIsFromInclusiveToExclusive() throws Exception {
        savedNap(baby, parent, T0.minusHours(1), null);           // before window
        Nap atFrom = savedNap(baby, parent, T0, null);            // on 'from' - included
        savedNap(baby, parent, T0.plusHours(6), null);            // on 'to' - excluded

        getAs(parent, "/naps?babyId=" + baby.getId()
                + "&from=" + T0 + "&to=" + T0.plusHours(6))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(atFrom.getId().toString()));
    }

    /**
     * The window filters on startTime, not on overlap: a long nap that began
     * before 'from' is absent even though it was still running inside the
     * window. That is what keeps this predicate the exact complement of the
     * client's local delete predicate.
     */
    @Test
    void windowExcludesALongNapThatStartedBeforeIt_evenWhileStillRunning() throws Exception {
        savedNap(baby, parent, T0.minusHours(3), null);

        getAs(parent, "/naps?babyId=" + baby.getId() + "&from=" + T0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void updatedSinceReturnsOnlyWritesAfterTheGivenInstant() throws Exception {
        savedNap(baby, parent, T0, T0.plusMinutes(45));

        // UTC so the query parameter ends in 'Z' - a '+01:00' offset's '+'
        // would decode as a space in the query string.
        OffsetDateTime pollCursor = OffsetDateTime.now(ZoneOffset.UTC);
        Thread.sleep(10); // ensure the next write's server-set updatedAt is strictly later

        Nap newWrite = savedNap(baby, parent, T0.plusHours(3), null);

        getAs(parent, "/naps?babyId=" + baby.getId() + "&updatedSince=" + pollCursor)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(newWrite.getId().toString()));
    }
}
