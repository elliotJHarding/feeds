package com.harding.feeds.mapping;

import com.harding.feeds.dto.NapDto;
import com.harding.feeds.entity.Nap;
import org.springframework.stereotype.Component;

@Component
public class NapMapper {

    public NapDto toDto(Nap nap) {
        return new NapDto()
                .id(nap.getId())
                .babyId(nap.getBaby().getId())
                .startTime(nap.getStartTime())
                .endTime(nap.getEndTime())
                .createdBy(nap.getCreatedBy().getId())
                .createdAt(nap.getCreatedAt())
                .updatedAt(nap.getUpdatedAt());
    }
}
