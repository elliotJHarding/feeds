package com.harding.feeds.repository;

import com.harding.feeds.entity.Baby;
import com.harding.feeds.entity.FamilyGroup;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface BabyRepository extends CrudRepository<Baby, Long> {

    List<Baby> findAllByFamilyGroup(FamilyGroup familyGroup);
}
