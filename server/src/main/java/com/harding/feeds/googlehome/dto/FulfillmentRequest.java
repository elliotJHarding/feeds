package com.harding.feeds.googlehome.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A Google smart home fulfillment request. The payload shape depends on the
 * intent, so it stays a JsonNode and each handler reads what it needs.
 */
public record FulfillmentRequest(String requestId, List<Input> inputs) {

    public record Input(String intent, JsonNode payload) {
    }
}
