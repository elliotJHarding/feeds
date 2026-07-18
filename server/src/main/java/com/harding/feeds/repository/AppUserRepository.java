package com.harding.feeds.repository;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.FamilyGroup;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface AppUserRepository extends CrudRepository<AppUser, Long> {

    AppUser findByUsername(String username);

    AppUser findByEmail(String email);

    List<AppUser> findAllByFamilyGroup(FamilyGroup familyGroup);
}
