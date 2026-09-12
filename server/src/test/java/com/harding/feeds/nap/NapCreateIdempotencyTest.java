package com.harding.feeds.nap;

import com.harding.feeds.dto.NapDto;
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
 * POST /naps is idempotent by the client-generated id: the first request
 * creates (201), a replay of the same id succeeds (200) and returns the
 * stored nap unchanged, so offline sync retries are safe.
 */
class NapCreateIdempotencyTest extends IntegrationTest {

    // Non-zero seconds: ISO_OFFSET_DATE_TIME omits ":ss" when it is zero,
    // which would make the serialized-form assertion below brittle.
    private static final OffsetDateTime START = OffsetDateTime.of(2026, 7, 17, 13, 5, 30, 0, ZoneOffset.UTC);

    private AppUser parent;
    private Baby baby;

    @BeforeEach
    void setUp() {
        FamilyGroup family = group();
        parent = userIn(family, "parent@test.com");
        baby = babyIn(family);
    }

    @Test
    void firstCreateReturns201WithTheNap() throws Exception {
        UUID id = UUID.randomUUID();

        postAs(parent, "/naps", napDto(id, baby.getId(), START, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.endTime").isEmpty())
                .andExpect(jsonPath("$.createdBy").value(parent.getId()));

        assertEquals(1, napRepository.count());
    }

    @Test
    void replayOfSameIdReturns200WithStoredNapUnchanged() throws Exception {
        UUID id = UUID.randomUUID();
        postAs(parent, "/naps", napDto(id, baby.getId(), START, null))
                .andExpect(status().isCreated());

        // Replay carries different field values - they must be ignored.
        NapDto retry = napDto(id, baby.getId(), START.plusHours(2), START.plusHours(3));

        postAs(parent, "/naps", retry)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.startTime").value("2026-07-17T13:05:30Z"))
                .andExpect(jsonPath("$.endTime").isEmpty());

        assertEquals(1, napRepository.count());
    }
}
