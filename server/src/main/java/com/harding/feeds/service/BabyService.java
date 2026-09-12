package com.harding.feeds.service;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.Baby;
import com.harding.feeds.repository.BabyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static com.harding.feeds.service.GroupScope.requireGroup;

@Service
public class BabyService {

    private final BabyRepository babyRepository;
    private final BabyScope babyScope;

    public BabyService(BabyRepository babyRepository, BabyScope babyScope) {
        this.babyRepository = babyRepository;
        this.babyScope = babyScope;
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
        Baby baby = babyScope.require(user, id);

        baby.setName(name);
        baby.setDateOfBirth(dateOfBirth);
        return babyRepository.save(baby);
    }
}
