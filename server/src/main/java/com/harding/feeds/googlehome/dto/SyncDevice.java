package com.harding.feeds.googlehome.dto;

import java.util.List;
import java.util.Map;

/** One device in a SYNC response. */
public record SyncDevice(
        String id,
        String type,
        List<String> traits,
        Name name,
        boolean willReportState,
        Map<String, Object> attributes) {

    public record Name(String name) {
    }
}
