package com.harding.feeds.mapping;

import com.harding.feeds.dto.FeedDto;
import com.harding.feeds.dto.FeedType;
import com.harding.feeds.dto.Side;
import com.harding.feeds.entity.Feed;
import org.springframework.stereotype.Component;

@Component
public class FeedMapper {

    public FeedDto toDto(Feed feed) {
        return new FeedDto()
                .id(feed.getId())
                .babyId(feed.getBaby().getId())
                .type(FeedType.valueOf(feed.getType().name()))
                .side(feed.getSide() != null ? Side.valueOf(feed.getSide().name()) : null)
                .amountMl(feed.getAmountMl())
                .startTime(feed.getStartTime())
                .endTime(feed.getEndTime())
                .createdBy(feed.getCreatedBy().getId())
                .createdAt(feed.getCreatedAt())
                .updatedAt(feed.getUpdatedAt());
    }

    public Feed.Type type(FeedDto dto) {
        return Feed.Type.valueOf(dto.getType().name());
    }

    public Feed.Side side(FeedDto dto) {
        return dto.getSide() != null ? Feed.Side.valueOf(dto.getSide().name()) : null;
    }
}
