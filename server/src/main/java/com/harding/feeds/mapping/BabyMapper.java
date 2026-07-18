package com.harding.feeds.mapping;

import com.harding.feeds.dto.BabyDto;
import com.harding.feeds.entity.Baby;
import org.springframework.stereotype.Component;

@Component
public class BabyMapper {

    public BabyDto toDto(Baby baby) {
        return new BabyDto()
                .id(baby.getId())
                .name(baby.getName())
                .dateOfBirth(baby.getDateOfBirth());
    }
}
