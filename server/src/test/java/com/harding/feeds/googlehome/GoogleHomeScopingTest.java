package com.harding.feeds.googlehome;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.Baby;
import com.harding.feeds.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Group scoping across the fulfillment boundary: another family's baby id
 * must be indistinguishable from a nonexistent device (mirrors the 404-not-403
 * convention of FeedGroupScopingTest).
 */
class GoogleHomeScopingTest extends IntegrationTest {

    @Test
    void anotherFamilysBabyIsDeviceNotFound() throws Exception {
        Baby baby = babyIn(group());
        AppUser outsider = userIn(group(), "outsider@example.com");

        postAs(outsider, "/googlehome/fulfillment", Map.of(
                "requestId", "req-1",
                "inputs", List.of(Map.of(
                        "intent", "action.devices.QUERY",
                        "payload", Map.of("devices", List.of(Map.of("id", baby.getId().toString())))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.devices['" + baby.getId() + "'].status").value("ERROR"))
                .andExpect(jsonPath("$.payload.devices['" + baby.getId() + "'].errorCode").value("deviceNotFound"))
                .andExpect(jsonPath("$.payload.devices['" + baby.getId() + "'].isRunning").doesNotExist());

        postAs(outsider, "/googlehome/fulfillment", Map.of(
                "requestId", "req-2",
                "inputs", List.of(Map.of(
                        "intent", "action.devices.EXECUTE",
                        "payload", Map.of("commands", List.of(Map.of(
                                "devices", List.of(Map.of("id", baby.getId().toString())),
                                "execution", List.of(Map.of(
                                        "command", "action.devices.commands.StartStop",
                                        "params", Map.of("start", true))))))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.commands[0].status").value("ERROR"))
                .andExpect(jsonPath("$.payload.commands[0].errorCode").value("deviceNotFound"));

        // Nothing was created for the other family's baby.
        assertThat(feedRepository.findFirstByBabyAndEndTimeIsNullOrderByStartTimeDesc(baby)).isEmpty();
    }

    @Test
    void syncListsOnlyTheCallersBabies() throws Exception {
        babyIn(group());
        AppUser outsider = userIn(group(), "outsider@example.com");

        postAs(outsider, "/googlehome/fulfillment", Map.of(
                "requestId", "req-3",
                "inputs", List.of(Map.of("intent", "action.devices.SYNC", "payload", Map.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload.devices.length()").value(0));
    }
}
