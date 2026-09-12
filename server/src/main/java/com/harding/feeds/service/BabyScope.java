package com.harding.feeds.service;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.Baby;
import com.harding.feeds.repository.BabyRepository;
import org.springframework.stereotype.Component;

import static com.harding.feeds.service.GroupScope.notFound;
import static com.harding.feeds.service.GroupScope.requireInGroup;

/**
 * Resolves a baby id to a Baby the caller may actually touch. The single place
 * the "reach ownership only via Baby -&gt; FamilyGroup" rule is written, so
 * {@link FeedService}, {@link NapService} and {@link BabyService} cannot drift
 * apart on it.
 */
@Component
class BabyScope {

    private final BabyRepository babyRepository;

    BabyScope(BabyRepository babyRepository) {
        this.babyRepository = babyRepository;
    }

    Baby require(AppUser user, Long babyId) {
        Baby baby = babyRepository.findById(babyId)
                .orElseThrow(() -> notFound("Baby not found"));
        requireInGroup(user, baby.getFamilyGroup(), "Baby");
        return baby;
    }
}
