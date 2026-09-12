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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The security invariant, for naps: every access is scoped to the caller's
 * family group. An outsider addressing another group's baby or nap gets a 404
 * (never the data, and never a 403 that would confirm the id exists).
 */
class NapGroupScopingTest extends IntegrationTest {

    private static final OffsetDateTime START = OffsetDateTime.of(2026, 7, 17, 13, 5, 0, 0, ZoneOffset.UTC);

    private AppUser familyParent;
    private Baby familyBaby;
    private Nap familyNap;
    private AppUser outsider;

    @BeforeEach
    void setUp() {
        FamilyGroup family = group();
        familyParent = userIn(family, "parent@family.com");
        familyBaby = babyIn(family);
        familyNap = savedNap(familyBaby, familyParent, START, START.plusMinutes(75));

        outsider = userIn(group(), "outsider@other.com");
    }

    @Test
    void getNaps_forBabyInAnotherGroup_isNotFound() throws Exception {
        getAs(outsider, "/naps?babyId=" + familyBaby.getId())
                .andExpect(status().isNotFound());
    }

    @Test
    void createNap_againstBabyInAnotherGroup_isNotFound() throws Exception {
        postAs(outsider, "/naps", napDto(UUID.randomUUID(), familyBaby.getId(), START, null))
                .andExpect(status().isNotFound());
    }

    @Test
    void createNap_replayingAnotherGroupsNapId_isNotFound() throws Exception {
        Baby outsiderBaby = babyIn(outsider.getFamilyGroup());

        postAs(outsider, "/naps", napDto(familyNap.getId(), outsiderBaby.getId(), START, null))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateNap_inAnotherGroup_isNotFoundAndNapIsUnchanged() throws Exception {
        putAs(outsider, "/naps/" + familyNap.getId(),
                napDto(familyNap.getId(), familyBaby.getId(), START.plusHours(4), null))
                .andExpect(status().isNotFound());

        assertEquals(START, napRepository.findById(familyNap.getId()).orElseThrow().getStartTime());
    }

    @Test
    void deleteNap_inAnotherGroup_isNotFoundAndNapSurvives() throws Exception {
        deleteAs(outsider, "/naps/" + familyNap.getId())
                .andExpect(status().isNotFound());

        assertTrue(napRepository.existsById(familyNap.getId()));
    }

    @Test
    void groupMember_mayUpdateANapCreatedByTheOtherParent() throws Exception {
        AppUser otherParent = userIn(familyParent.getFamilyGroup(), "other@family.com");

        putAs(otherParent, "/naps/" + familyNap.getId(),
                napDto(familyNap.getId(), familyBaby.getId(), START, START.plusMinutes(90)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endTime").isNotEmpty());
    }
}
