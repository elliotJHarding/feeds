package com.harding.feeds.service;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.Baby;
import com.harding.feeds.repository.BabyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static com.harding.feeds.service.GroupScope.notFound;
import static com.harding.feeds.service.GroupScope.requireGroup;
import static com.harding.feeds.service.GroupScope.requireInGroup;

@Service
public class BabyService {

    private final BabyRepository babyRepository;

    public BabyService(BabyRepository babyRepository) {
        this.babyRepository = babyRepository;
    }

    @Transactional(readOnly = true)
    public List<Baby> getBabies(AppUser user) {
        return babyRepository.findAllByFamilyGroup(requireGroup(user));
    }

    @Transactional
    public Baby create(AppUser user, String name, LocalDate dateOfBirth) {
        return babyRepository.save(new Baby(name, dateOfBirth, requireGroup(user)));
    }

    @Transactional
    public Baby update(AppUser user, Long id, String name, LocalDate dateOfBirth) {
        Baby baby = babyRepository.findById(id)
                .orElseThrow(() -> notFound("Baby not found"));
        requireInGroup(user, baby.getFamilyGroup(), "Baby");

        baby.setName(name);
        baby.setDateOfBirth(dateOfBirth);
        return babyRepository.save(baby);
    }
}
