package com.harding.feeds.mapping;

import com.harding.feeds.dto.FamilyGroupDto;
import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.FamilyGroup;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FamilyGroupMapper {

    private final UserMapper userMapper;

    public FamilyGroupMapper(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public FamilyGroupDto toDto(FamilyGroup group, List<AppUser> members) {
        return new FamilyGroupDto()
                .uuid(group.getUuid())
                .users(members.stream().map(userMapper::toDto).toList());
    }
}
