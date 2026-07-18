package com.harding.feeds.controller;

import com.harding.feeds.api.FeedsApi;
import com.harding.feeds.dto.FeedDto;
import com.harding.feeds.entity.Feed;
import com.harding.feeds.mapping.FeedMapper;
import com.harding.feeds.service.FeedService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
public class FeedController implements FeedsApi {

    private final FeedService feedService;
    private final FeedMapper feedMapper;

    public FeedController(FeedService feedService, FeedMapper feedMapper) {
        this.feedService = feedService;
        this.feedMapper = feedMapper;
    }

    @Override
    public ResponseEntity<List<FeedDto>> getFeeds(Long babyId, OffsetDateTime from, OffsetDateTime to,
                                                  OffsetDateTime updatedSince) {
        List<Feed> feeds = feedService.getFeeds(CurrentUser.get(), babyId, from, to, updatedSince);
        return ResponseEntity.ok(feeds.stream().map(feedMapper::toDto).toList());
    }

    @Override
    public ResponseEntity<FeedDto> createFeed(FeedDto feedDto) {
        FeedService.Creation creation = feedService.create(
                CurrentUser.get(),
                feedDto.getId(),
                feedDto.getBabyId(),
                feedMapper.type(feedDto),
                feedMapper.side(feedDto),
                feedDto.getAmountMl(),
                feedDto.getStartTime(),
                feedDto.getEndTime());

        // 201 for a new feed, 200 for an idempotent replay of an existing id.
        HttpStatus status = creation.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(feedMapper.toDto(creation.feed()));
    }

    @Override
    public ResponseEntity<FeedDto> updateFeed(UUID id, FeedDto feedDto) {
        Feed feed = feedService.update(
                CurrentUser.get(),
                id,
                feedDto.getBabyId(),
                feedMapper.type(feedDto),
                feedMapper.side(feedDto),
                feedDto.getAmountMl(),
                feedDto.getStartTime(),
                feedDto.getEndTime());

        return ResponseEntity.ok(feedMapper.toDto(feed));
    }

    @Override
    public ResponseEntity<Void> deleteFeed(UUID id) {
        feedService.delete(CurrentUser.get(), id);
        return ResponseEntity.noContent().build();
    }
}
