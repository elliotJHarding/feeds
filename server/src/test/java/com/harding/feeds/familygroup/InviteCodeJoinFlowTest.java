package com.harding.feeds.familygroup;

import com.fasterxml.jackson.databind.JsonNode;
import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.FamilyGroup;
import com.harding.feeds.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Group joining (SPEC.md): the creator generates a short code, the second
 * user enters it to join. A user belongs to exactly one group.
 */
class InviteCodeJoinFlowTest extends IntegrationTest {

    private FamilyGroup family;
    private AppUser creator;
    private AppUser joiner;

    @BeforeEach
    void setUp() {
        family = group();
        creator = userIn(family, "creator@test.com");
        joiner = userIn(null, "joiner@test.com");
    }

    @Test
    void inviteCodeIsEightCharactersFromTheUnambiguousAlphabet() throws Exception {
        postAs(creator, "/familyGroup/inviteCode")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(
                        org.hamcrest.Matchers.matchesPattern("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}")));
    }

    @Test
    void joiningWithAValidCodeAddsTheUserToTheGroup() throws Exception {
        postAs(joiner, "/familyGroup/join", Map.of("code", inviteCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(family.getUuid().toString()))
                .andExpect(jsonPath("$.users.length()").value(2));

        assertEquals(family.getUuid(),
                appUserRepository.findByEmail("joiner@test.com").getFamilyGroup().getUuid());
    }

    @Test
    void joiningWithAnUnknownCodeIsNotFound() throws Exception {
        postAs(joiner, "/familyGroup/join", Map.of("code", "WRONGCODE"))
                .andExpect(status().isNotFound());
    }

    @Test
    void joiningAnotherGroupWhileAlreadyInOneIsAConflict() throws Exception {
        String code = inviteCode();
        AppUser committedElsewhere = userIn(group(), "elsewhere@test.com");

        postAs(committedElsewhere, "/familyGroup/join", Map.of("code", code))
                .andExpect(status().isConflict());
    }

    @Test
    void regeneratingTheInviteCodeInvalidatesThePreviousOne() throws Exception {
        String oldCode = inviteCode();
        inviteCode(); // regenerate

        postAs(joiner, "/familyGroup/join", Map.of("code", oldCode))
                .andExpect(status().isNotFound());
    }

    private String inviteCode() throws Exception {
        String body = postAs(creator, "/familyGroup/inviteCode")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("code").asText();
    }
}
