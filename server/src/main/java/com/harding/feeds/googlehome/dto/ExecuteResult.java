package com.harding.feeds.googlehome.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/** Outcome for a set of device ids in an EXECUTE response. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExecuteResult(
        List<String> ids,
        String status,
        Map<String, Object> states,
        String errorCode) {

    public static ExecuteResult success(String deviceId, Map<String, Object> states) {
        return new ExecuteResult(List.of(deviceId), "SUCCESS", states, null);
    }

    public static ExecuteResult error(String deviceId, String errorCode) {
        return new ExecuteResult(List.of(deviceId), "ERROR", null, errorCode);
    }
}
