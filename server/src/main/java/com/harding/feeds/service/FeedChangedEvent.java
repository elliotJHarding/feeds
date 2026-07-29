package com.harding.feeds.service;

import com.harding.feeds.entity.Baby;

/**
 * Published after any feed mutation (app CRUD or voice start/stop) so
 * interested parties - currently Google Home state reporting - can react
 * without FeedService knowing about them.
 */
public record FeedChangedEvent(Baby baby) {
}
