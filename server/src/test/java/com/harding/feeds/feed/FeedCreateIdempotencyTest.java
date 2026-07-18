package com.harding.feeds.feed;

import com.harding.feeds.dto.FeedDto;
import com.harding.feeds.dto.Side;
import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.Baby;
import com.harding.feeds.entity.FamilyGroup;
import com.harding.feeds.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /feeds is idempotent by the client-generated id: the first request
 * creates (201), a replay of the same id succeeds (200) and returns the
 * stored feed unchanged, so offline sync retries are safe.
 */
class FeedCreateIdempotencyTest extends IntegrationTest {

    // Non-zero seconds: ISO_OFFSET_DATE_TIME omits ":ss" when it is zero,
    // which would make the serialized-form assertion below brittle.
    private static final OffsetDateTime START = OffsetDateTime.of(2026, 7, 17, 3, 0, 30, 0, ZoneOffset.UTC);

    private AppUser parent;
    private Baby baby;

    @BeforeEach
    void setUp() {
        FamilyGroup family = group();
        parent = userIn(family, "parent@test.com");
        baby = babyIn(family);
    }

    @Test
    void firstCreateReturns201WithTheFeed() throws Exception {
        UUID id = UUID.randomUUID();

        postAs(parent, "/feeds", breastFeedDto(id, baby.getId(), Side.L, START, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.side").value("L"))
                .andExpect(jsonPath("$.endTime").isEmpty())
                .andExpect(jsonPath("$.createdBy").value(parent.getId()));

        assertEquals(1, feedRepository.count());
    }

    @Test
    void replayOfSameIdReturns200WithStoredFeedUnchanged() throws Exception {
        UUID id = UUID.randomUUID();
        postAs(parent, "/feeds", breastFeedDto(id, baby.getId(), Side.L, START, null))
                .andExpect(status().isCreated());

        // Replay carries different field values - they must be ignored.
        FeedDto retry = breastFeedDto(id, baby.getId(), Side.R, START.plusHours(2), START.plusHours(3));

        postAs(parent, "/feeds", retry)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.side").value("L"))
                .andExpect(jsonPath("$.startTime").value("2026-07-17T03:00:30Z"))
                .andExpect(jsonPath("$.endTime").isEmpty());

        assertEquals(1, feedRepository.count());
    }
}
