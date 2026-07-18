package com.harding.feeds.repository;

import com.harding.feeds.entity.FamilyGroup;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface FamilyGroupRepository extends CrudRepository<FamilyGroup, UUID> {

    Optional<FamilyGroup> findByInviteCode(String inviteCode);
}
