package com.harding.feeds.googlehome;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.Baby;
import com.harding.feeds.entity.FamilyGroup;
import com.harding.feeds.entity.Feed;
import com.harding.feeds.repository.GoogleHomeLinkRepository;
import com.harding.feeds.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.JsonPathResultMatchers;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The fulfillment intents against the virtual per-baby feed device: SYNC
 * device shape, live QUERY state (running + side + recency modes), EXECUTE
 * start/stop with side alternation and zone override, and DISCONNECT.
 */
class GoogleHomeFulfillmentTest extends IntegrationTest {

    @Autowired
    private GoogleHomeLinkRepository googleHomeLinkRepository;

    @Autowired
    private GoogleHomeTokenService googleHomeTokenService;

    private FamilyGroup family;
    private AppUser parent;
    private Baby baby;

    @BeforeEach
    void fixtures() {
        family = group();
        parent = userIn(family, "parent@example.com");
        baby = babyIn(family);
    }

    @Test
    void syncDescribesOneDevicePerBaby() throws Exception {
        babyIn(family);

        sync()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("req-1"))
                .andExpect(jsonPath("$.payload.agentUserId").value(family.getUuid().toString()))
                .andExpect(jsonPath("$.payload.devices.length()").value(2))
                .andExpect(jsonPath("$.payload.devices[0].id").value(baby.getId().toString()))
                .andExpect(jsonPath("$.payload.devices[0].type").value("action.devices.types.WASHER"))
                .andExpect(jsonPath("$.payload.devices[0].traits").value(containsInAnyOrder(
                        "action.devices.traits.StartStop", "action.devices.traits.Modes")))
                .andExpect(jsonPath("$.payload.devices[0].name.name").value("Baby feed"))
                .andExpect(jsonPath("$.payload.devices[0].willReportState").value(true))
                .andExpect(jsonPath("$.payload.devices[0].attributes.queryOnlyModes").value(true))
                .andExpect(jsonPath("$.payload.devices[0].attributes.availableZones")
                        .value(contains("left", "right")));
    }

    @Test
    void queryWithNoFeedsReportsNotRunningAndNoModeState() throws Exception {
        query()
                .andExpect(status().isOk())
                .andExpect(devicePath("isRunning").value(false))
                .andExpect(devicePath("online").value(true))
                .andExpect(devicePath("status").value("SUCCESS"))
                .andExpect(devicePath("currentModeSettings.side").doesNotExist())
                .andExpect(devicePath("currentModeSettings.last_feed").doesNotExist());
    }

    @Test
    void queryReflectsAnInProgressFeed() throws Exception {
        savedFeed(baby, parent, Feed.Side.R, OffsetDateTime.now().minusMinutes(5), null);

        query()
                .andExpect(devicePath("isRunning").value(true))
                .andExpect(devicePath("currentModeSettings.side").value("right"))
                .andExpect(devicePath("currentModeSettings.last_feed").value("right_now"));
    }

    @Test
    void queryBucketsTimeSinceTheLastFeedEnded() throws Exception {
        savedFeed(baby, parent,
                OffsetDateTime.now().minusMinutes(110), OffsetDateTime.now().minusMinutes(90));

        query()
                .andExpect(devicePath("isRunning").value(false))
                .andExpect(devicePath("currentModeSettings.side").value("left"))
                .andExpect(devicePath("currentModeSettings.last_feed").value("one_to_two_hours"));
    }

    @Test
    void startWithNoHistoryBeginsOnTheLeft() throws Exception {
        execute(Map.of("start", true))
                .andExpect(commandPath("status").value("SUCCESS"))
                .andExpect(commandPath("states.isRunning").value(true));

        Optional<Feed> active = feedRepository.findFirstByBabyAndEndTimeIsNullOrderByStartTimeDesc(baby);
        assertThat(active).isPresent();
        assertThat(active.get().getType()).isEqualTo(Feed.Type.BREAST);
        assertThat(active.get().getSide()).isEqualTo(Feed.Side.L);
        assertThat(active.get().getStartTime()).isCloseTo(OffsetDateTime.now(),
                within(5, ChronoUnit.SECONDS));
    }

    @Test
    void startAlternatesToTheOppositeOfTheLastFeedsSide() throws Exception {
        savedFeed(baby, parent, Feed.Side.L,
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2));

        execute(Map.of("start", true)).andExpect(commandPath("status").value("SUCCESS"));

        assertThat(feedRepository.findFirstByBabyAndEndTimeIsNullOrderByStartTimeDesc(baby))
                .get().extracting(Feed::getSide).isEqualTo(Feed.Side.R);
    }

    @Test
    void startWithAZoneOverridesTheSide() throws Exception {
        savedFeed(baby, parent, Feed.Side.L,
                OffsetDateTime.now().minusHours(3), OffsetDateTime.now().minusHours(2));

        execute(Map.of("start", true, "zone", "left")).andExpect(commandPath("status").value("SUCCESS"));

        assertThat(feedRepository.findFirstByBabyAndEndTimeIsNullOrderByStartTimeDesc(baby))
                .get().extracting(Feed::getSide).isEqualTo(Feed.Side.L);
    }

    @Test
    void startWhileAFeedIsRunningErrors() throws Exception {
        savedFeed(baby, parent, OffsetDateTime.now().minusMinutes(10), null);

        execute(Map.of("start", true))
                .andExpect(commandPath("status").value("ERROR"))
                .andExpect(commandPath("errorCode").value("alreadyStarted"));
    }

    @Test
    void stopEndsTheInProgressFeed() throws Exception {
        Feed feed = savedFeed(baby, parent, OffsetDateTime.now().minusMinutes(10), null);

        execute(Map.of("start", false))
                .andExpect(commandPath("status").value("SUCCESS"))
                .andExpect(commandPath("states.isRunning").value(false));

        assertThat(feedRepository.findById(feed.getId())).get()
                .extracting(Feed::getEndTime).isNotNull();
    }

    @Test
    void stopWithNothingRunningErrors() throws Exception {
        execute(Map.of("start", false))
                .andExpect(commandPath("status").value("ERROR"))
                .andExpect(commandPath("errorCode").value("alreadyStopped"));
    }

    @Test
    void disconnectRevokesOnlyTheCallersLinks() throws Exception {
        AppUser otherParent = userIn(family, "other@example.com");
        googleHomeTokenService.issueRefreshToken(parent);
        googleHomeTokenService.issueRefreshToken(otherParent);

        postAs(parent, "/googlehome/fulfillment", request("action.devices.DISCONNECT", Map.of()))
                .andExpect(status().isOk());

        assertThat(googleHomeLinkRepository.findAll())
                .allSatisfy(link -> assertThat(link.getUser().getId()).isEqualTo(otherParent.getId()));
    }

    // Helpers

    private ResultActions sync() throws Exception {
        return postAs(parent, "/googlehome/fulfillment", request("action.devices.SYNC", Map.of()));
    }

    private ResultActions query() throws Exception {
        return postAs(parent, "/googlehome/fulfillment", request("action.devices.QUERY",
                Map.of("devices", List.of(Map.of("id", baby.getId().toString())))));
    }

    private ResultActions execute(Map<String, Object> params) throws Exception {
        return postAs(parent, "/googlehome/fulfillment", request("action.devices.EXECUTE",
                Map.of("commands", List.of(Map.of(
                        "devices", List.of(Map.of("id", baby.getId().toString())),
                        "execution", List.of(Map.of(
                                "command", "action.devices.commands.StartStop",
                                "params", params)))))));
    }

    private Map<String, Object> request(String intent, Map<String, Object> payload) {
        return Map.of("requestId", "req-1",
                "inputs", List.of(Map.of("intent", intent, "payload", payload)));
    }

    private JsonPathResultMatchers devicePath(String path) {
        return jsonPath("$.payload.devices['" + baby.getId() + "']." + path);
    }

    private JsonPathResultMatchers commandPath(String path) {
        return jsonPath("$.payload.commands[0]." + path);
    }
}
