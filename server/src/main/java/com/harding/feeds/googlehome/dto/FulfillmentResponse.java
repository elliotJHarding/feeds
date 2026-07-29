package com.harding.feeds.googlehome.dto;

/** A fulfillment response: the echoed requestId plus an intent-shaped payload. */
public record FulfillmentResponse(String requestId, Object payload) {
}
