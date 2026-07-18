package com.harding.feeds.feed;

import com.harding.feeds.dto.Side;
import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.Baby;
import com.harding.feeds.entity.FamilyGroup;
import com.harding.feeds.entity.Feed;
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
 * The security invariant: every access is scoped to the caller's family
 * group. An outsider addressing another group's baby or feed gets a 404
 * (never the data, and never a 403 that would confirm the id exists).
 */
class FeedGroupScopingTest extends IntegrationTest {

    private static final OffsetDateTime START = OffsetDateTime.of(2026, 7, 17, 3, 0, 0, 0, ZoneOffset.UTC);

    private AppUser familyParent;
    private Baby familyBaby;
    private Feed familyFeed;
    private AppUser outsider;

    @BeforeEach
    void setUp() {
        FamilyGroup family = group();
        familyParent = userIn(family, "parent@family.com");
        familyBaby = babyIn(family);
        familyFeed = savedFeed(familyBaby, familyParent, START, START.plusMinutes(20));

        outsider = userIn(group(), "outsider@other.com");
    }

    @Test
    void getFeeds_forBabyInAnotherGroup_isNotFound() throws Exception {
        getAs(outsider, "/feeds?babyId=" + familyBaby.getId())
                .andExpect(status().isNotFound());
    }

    @Test
    void createFeed_againstBabyInAnotherGroup_isNotFound() throws Exception {
        postAs(outsider, "/feeds", breastFeedDto(UUID.randomUUID(), familyBaby.getId(), Side.L, START, null))
                .andExpect(status().isNotFound());
    }

    @Test
    void createFeed_replayingAnotherGroupsFeedId_isNotFound() throws Exception {
        Baby outsiderBaby = babyIn(outsider.getFamilyGroup());

        postAs(outsider, "/feeds", breastFeedDto(familyFeed.getId(), outsiderBaby.getId(), Side.L, START, null))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateFeed_inAnotherGroup_isNotFound() throws Exception {
        putAs(outsider, "/feeds/" + familyFeed.getId(),
                breastFeedDto(familyFeed.getId(), familyBaby.getId(), Side.R, START, null))
                .andExpect(status().isNotFound());

        assertEquals(Feed.Side.L, feedRepository.findById(familyFeed.getId()).orElseThrow().getSide());
    }

    @Test
    void deleteFeed_inAnotherGroup_isNotFoundAndFeedSurvives() throws Exception {
        deleteAs(outsider, "/feeds/" + familyFeed.getId())
                .andExpect(status().isNotFound());

        assertTrue(feedRepository.existsById(familyFeed.getId()));
    }

    @Test
    void getBabies_returnsOnlyTheCallersGroupsBabies() throws Exception {
        babyIn(outsider.getFamilyGroup());

        getAs(outsider, "/babies")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(org.hamcrest.Matchers.not(familyBaby.getId().intValue())));
    }

    @Test
    void groupMember_mayUpdateAFeedCreatedByTheOtherParent() throws Exception {
        AppUser otherParent = userIn(familyParent.getFamilyGroup(), "other@family.com");

        putAs(otherParent, "/feeds/" + familyFeed.getId(),
                breastFeedDto(familyFeed.getId(), familyBaby.getId(), Side.R, START, START.plusMinutes(25)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.side").value("R"));
    }
}
