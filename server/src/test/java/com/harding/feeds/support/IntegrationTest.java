package com.harding.feeds.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harding.feeds.dto.FeedDto;
import com.harding.feeds.dto.FeedType;
import com.harding.feeds.dto.Side;
import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.Baby;
import com.harding.feeds.entity.FamilyGroup;
import com.harding.feeds.entity.Feed;
import com.harding.feeds.entity.PublicDetails;
import com.harding.feeds.repository.AppUserRepository;
import com.harding.feeds.repository.BabyRepository;
import com.harding.feeds.repository.FamilyGroupRepository;
import com.harding.feeds.repository.FeedRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base for integration tests: full application context on H2, MockMvc with
 * the real security filter chain, rollback after every test, and fixture
 * builders so each test states only what it is about.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected AppUserRepository appUserRepository;

    @Autowired
    protected FamilyGroupRepository familyGroupRepository;

    @Autowired
    protected BabyRepository babyRepository;

    @Autowired
    protected FeedRepository feedRepository;

    // Fixtures

    protected FamilyGroup group() {
        return familyGroupRepository.save(new FamilyGroup());
    }

    protected AppUser userIn(FamilyGroup group, String email) {
        AppUser user = new AppUser();
        user.setUsername("google-sub-" + email);
        user.setEmail(email);
        user.setPublicDetails(new PublicDetails(email, null, null, null, true));
        user.setFamilyGroup(group);
        return appUserRepository.save(user);
    }

    protected Baby babyIn(FamilyGroup group) {
        return babyRepository.save(new Baby("Baby", LocalDate.of(2026, 5, 1), group));
    }

    /** A persisted breast feed; endTime null means in progress. */
    protected Feed savedFeed(Baby baby, AppUser createdBy, OffsetDateTime startTime, OffsetDateTime endTime) {
        return feedRepository.save(new Feed(
                UUID.randomUUID(), baby, Feed.Type.BREAST, Feed.Side.L, null, startTime, endTime, createdBy));
    }

    protected FeedDto breastFeedDto(UUID id, Long babyId, Side side, OffsetDateTime startTime, OffsetDateTime endTime) {
        return new FeedDto()
                .id(id)
                .babyId(babyId)
                .type(FeedType.BREAST)
                .side(side)
                .startTime(startTime)
                .endTime(endTime);
    }

    // Authenticated requests

    protected ResultActions getAs(AppUser user, String url) throws Exception {
        return mockMvc.perform(get(url).with(user(user)));
    }

    protected ResultActions postAs(AppUser user, String url) throws Exception {
        return mockMvc.perform(post(url).with(user(user)));
    }

    protected ResultActions postAs(AppUser user, String url, Object body) throws Exception {
        return mockMvc.perform(post(url).with(user(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions putAs(AppUser user, String url, Object body) throws Exception {
        return mockMvc.perform(put(url).with(user(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected ResultActions deleteAs(AppUser user, String url) throws Exception {
        return mockMvc.perform(delete(url).with(user(user)));
    }
}
