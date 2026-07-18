package com.harding.feeds.service;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.FamilyGroup;
import com.harding.feeds.repository.AppUserRepository;
import com.harding.feeds.repository.FamilyGroupRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

import static com.harding.feeds.service.GroupScope.notFound;
import static com.harding.feeds.service.GroupScope.requireGroup;

/**
 * Group lifecycle: create, invite, join. A user belongs to exactly one group
 * (SPEC.md), so creating or joining while already in one is a 409 conflict -
 * except re-joining your own group, which is treated as an idempotent no-op.
 */
@Service
public class FamilyGroupService {

    private final FamilyGroupRepository familyGroupRepository;
    private final AppUserRepository appUserRepository;
    private final InviteCodeGenerator inviteCodeGenerator;

    public FamilyGroupService(FamilyGroupRepository familyGroupRepository,
                              AppUserRepository appUserRepository,
                              InviteCodeGenerator inviteCodeGenerator) {
        this.familyGroupRepository = familyGroupRepository;
        this.appUserRepository = appUserRepository;
        this.inviteCodeGenerator = inviteCodeGenerator;
    }

    @Transactional(readOnly = true)
    public FamilyGroup getGroup(AppUser user) {
        return requireGroup(user);
    }

    /** Members read via query rather than the mapped collection so a just-joined user is always included. */
    @Transactional(readOnly = true)
    public List<AppUser> membersOf(FamilyGroup group) {
        return appUserRepository.findAllByFamilyGroup(group);
    }

    @Transactional
    public FamilyGroup create(AppUser user) {
        requireNoGroup(user);

        FamilyGroup group = familyGroupRepository.save(new FamilyGroup());
        user.setFamilyGroup(group);
        appUserRepository.save(user);
        return group;
    }

    /** The group's current code, minting one on first request so the code is stable thereafter. */
    @Transactional
    public String getOrCreateInviteCode(AppUser user) {
        FamilyGroup group = requireGroup(user);
        if (group.getInviteCode() == null) {
            assignFreshCode(group);
        }
        return group.getInviteCode();
    }

    /** Mints a fresh code, invalidating any previous one for the group. */
    @Transactional
    public String regenerateInviteCode(AppUser user) {
        FamilyGroup group = requireGroup(user);
        assignFreshCode(group);
        return group.getInviteCode();
    }

    private void assignFreshCode(FamilyGroup group) {
        String code;
        do {
            code = inviteCodeGenerator.generate();
        } while (familyGroupRepository.findByInviteCode(code).isPresent());

        group.setInviteCode(code);
        familyGroupRepository.save(group);
    }

    @Transactional
    public FamilyGroup join(AppUser user, String code) {
        FamilyGroup group = familyGroupRepository
                .findByInviteCode(normalise(code))
                .orElseThrow(() -> notFound("Invite code invalid or expired"));

        if (user.getFamilyGroup() != null) {
            if (user.getFamilyGroup().getUuid().equals(group.getUuid())) {
                return group;
            }
            throw alreadyInGroup();
        }

        user.setFamilyGroup(group);
        appUserRepository.save(user);
        return group;
    }

    private void requireNoGroup(AppUser user) {
        if (user.getFamilyGroup() != null) {
            throw alreadyInGroup();
        }
    }

    private ResponseStatusException alreadyInGroup() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "User is already in a family group");
    }

    private String normalise(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
