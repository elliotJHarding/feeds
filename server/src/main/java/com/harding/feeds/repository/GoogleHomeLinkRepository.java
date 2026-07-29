package com.harding.feeds.repository;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.FamilyGroup;
import com.harding.feeds.entity.GoogleHomeLink;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface GoogleHomeLinkRepository extends CrudRepository<GoogleHomeLink, Long> {

    Optional<GoogleHomeLink> findByTokenHash(String tokenHash);

    void deleteByUser(AppUser user);

    /** Whether anyone in the family still has a Google Home link (gates Report State). */
    boolean existsByUserFamilyGroup(FamilyGroup familyGroup);
}
