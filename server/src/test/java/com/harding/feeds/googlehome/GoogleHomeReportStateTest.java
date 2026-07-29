package com.harding.feeds.googlehome;

import com.harding.feeds.dto.Side;
import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.Baby;
import com.harding.feeds.entity.FamilyGroup;
import com.harding.feeds.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Report State is triggered by feed mutations - from the app API and from
 * voice EXECUTE - with the same states QUERY serves, and only for families
 * that actually hold a Google Home link.
 */
class GoogleHomeReportStateTest extends IntegrationTest {

    @MockitoBean
    private GoogleHomeStateReporter reporter;

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
        when(reporter.enabled()).thenReturn(true);
    }

    @Test
    void appFeedCreateReportsTheNewState() throws Exception {
        googleHomeTokenService.issueRefreshToken(parent);

        postAs(parent, "/feeds", breastFeedDto(UUID.randomUUID(), baby.getId(),
                Side.L, OffsetDateTime.now().minusMinutes(1), null))
                .andExpect(status().isCreated());

        ArgumentCaptor<Map<String, Object>> states = statesCaptor();
        verify(reporter).report(
                eq(family.getUuid().toString()),
                eq(baby.getId().toString()),
                states.capture());
        assertThat(states.getValue()).containsEntry("isRunning", true);
    }

    @Test
    void voiceStartReportsTheNewState() throws Exception {
        googleHomeTokenService.issueRefreshToken(parent);

        postAs(parent, "/googlehome/fulfillment", Map.of(
                "requestId", "req-1",
                "inputs", List.of(Map.of(
                        "intent", "action.devices.EXECUTE",
                        "payload", Map.of("commands", List.of(Map.of(
                                "devices", List.of(Map.of("id", baby.getId().toString())),
                                "execution", List.of(Map.of(
                                        "command", "action.devices.commands.StartStop",
                                        "params", Map.of("start", true))))))))))
                .andExpect(status().isOk());

        ArgumentCaptor<Map<String, Object>> states = statesCaptor();
        verify(reporter).report(anyString(), anyString(), states.capture());
        assertThat(states.getValue())
                .containsEntry("isRunning", true)
                .containsEntry("currentModeSettings", Map.of("side", "left", "last_feed", "right_now"));
    }

    @Test
    void familiesWithoutALinkAreNeverReported() throws Exception {
        postAs(parent, "/feeds", breastFeedDto(UUID.randomUUID(), baby.getId(),
                Side.L, OffsetDateTime.now().minusMinutes(1), null))
                .andExpect(status().isCreated());

        verify(reporter, never()).report(anyString(), anyString(), anyMap());
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> statesCaptor() {
        return ArgumentCaptor.forClass((Class) Map.class);
    }
}
