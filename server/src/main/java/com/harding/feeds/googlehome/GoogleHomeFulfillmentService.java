package com.harding.feeds.googlehome;

import com.fasterxml.jackson.databind.JsonNode;
import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.Baby;
import com.harding.feeds.entity.Feed;
import com.harding.feeds.googlehome.dto.ExecuteResult;
import com.harding.feeds.googlehome.dto.FulfillmentRequest;
import com.harding.feeds.googlehome.dto.FulfillmentResponse;
import com.harding.feeds.googlehome.dto.SyncDevice;
import com.harding.feeds.service.BabyService;
import com.harding.feeds.service.FeedService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The four Google smart home intents, backed by one virtual StartStop+Modes
 * device per baby. Device ids are baby ids, resolved only against the
 * caller's own babies so a foreign id is indistinguishable from a missing
 * device. QUERY state is computed live by {@link GoogleHomeDeviceStates};
 * changes are additionally pushed via Report State.
 */
@Service
public class GoogleHomeFulfillmentService {

    static final String DEVICE_TYPE = "action.devices.types.WASHER";
    static final String TRAIT_START_STOP = "action.devices.traits.StartStop";
    static final String TRAIT_MODES = "action.devices.traits.Modes";
    static final String COMMAND_START_STOP = "action.devices.commands.StartStop";

    static final String MODE_SIDE = "side";
    static final String MODE_LAST_FEED = "last_feed";

    private final BabyService babyService;
    private final FeedService feedService;
    private final GoogleHomeDeviceStates deviceStates;
    private final GoogleHomeTokenService tokenService;

    public GoogleHomeFulfillmentService(BabyService babyService, FeedService feedService,
                                        GoogleHomeDeviceStates deviceStates,
                                        GoogleHomeTokenService tokenService) {
        this.babyService = babyService;
        this.feedService = feedService;
        this.deviceStates = deviceStates;
        this.tokenService = tokenService;
    }

    public FulfillmentResponse handle(AppUser user, FulfillmentRequest request) {
        FulfillmentRequest.Input input = request.inputs().get(0);
        Object payload = switch (input.intent()) {
            case "action.devices.SYNC" -> sync(user);
            case "action.devices.QUERY" -> query(user, input.payload());
            case "action.devices.EXECUTE" -> execute(user, input.payload());
            case "action.devices.DISCONNECT" -> disconnect(user);
            default -> throw new IllegalArgumentException("Unknown intent: " + input.intent());
        };
        return new FulfillmentResponse(request.requestId(), payload);
    }

    private Map<String, Object> sync(AppUser user) {
        List<SyncDevice> devices = babyService.getBabies(user).stream()
                .map(this::device)
                .toList();
        return Map.of(
                "agentUserId", user.getFamilyGroup().getUuid().toString(),
                "devices", devices);
    }

    private SyncDevice device(Baby baby) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("pausable", false);
        attributes.put("availableZones", List.of("left", "right"));
        attributes.put("availableModes", List.of(
                mode(MODE_SIDE, List.of("side", "feeding side", "last side", "breast side"), List.of(
                        setting("left", List.of("left", "the left", "left side")),
                        setting("right", List.of("right", "the right", "right side")))),
                mode(MODE_LAST_FEED,
                        List.of("last feed", "time since last feed", "last fed",
                                "last feeding", "feed recency"),
                        List.of(
                        setting("right_now", List.of("right now", "in progress", "happening now")),
                        setting("just_now", List.of("just now", "less than half an hour ago")),
                        setting("under_an_hour", List.of("under an hour ago", "less than an hour ago")),
                        setting("one_to_two_hours", List.of("one to two hours ago", "over an hour ago")),
                        setting("two_to_four_hours", List.of("two to four hours ago", "a few hours ago")),
                        setting("over_four_hours", List.of("over four hours ago", "more than four hours ago"))))));
        attributes.put("queryOnlyModes", true);

        return new SyncDevice(
                String.valueOf(baby.getId()),
                DEVICE_TYPE,
                List.of(TRAIT_START_STOP, TRAIT_MODES),
                new SyncDevice.Name(
                        baby.getName() + " feed",
                        List.of("the feed", baby.getName() + "'s feed", "feed tracker")),
                true,
                attributes);
    }

    private Map<String, Object> query(AppUser user, JsonNode payload) {
        Map<String, Baby> babies = babiesById(user);
        Map<String, Object> devices = new LinkedHashMap<>();
        for (JsonNode device : payload.path("devices")) {
            String deviceId = device.path("id").asText();
            Baby baby = babies.get(deviceId);
            if (baby == null) {
                devices.put(deviceId, Map.of("status", "ERROR", "errorCode", "deviceNotFound"));
            } else {
                Map<String, Object> state = new LinkedHashMap<>(deviceStates.forBaby(baby));
                state.put("status", "SUCCESS");
                devices.put(deviceId, state);
            }
        }
        return Map.of("devices", devices);
    }

    private Map<String, Object> execute(AppUser user, JsonNode payload) {
        Map<String, Baby> babies = babiesById(user);
        List<ExecuteResult> results = new ArrayList<>();
        for (JsonNode command : payload.path("commands")) {
            for (JsonNode device : command.path("devices")) {
                String deviceId = device.path("id").asText();
                for (JsonNode execution : command.path("execution")) {
                    results.add(executeOne(user, babies.get(deviceId), deviceId, execution));
                }
            }
        }
        return Map.of("commands", results);
    }

    private ExecuteResult executeOne(AppUser user, Baby baby, String deviceId, JsonNode execution) {
        if (!COMMAND_START_STOP.equals(execution.path("command").asText())) {
            return ExecuteResult.error(deviceId, "functionNotSupported");
        }
        if (baby == null) {
            return ExecuteResult.error(deviceId, "deviceNotFound");
        }

        Optional<Feed> outcome;
        String failureCode;
        if (execution.path("params").path("start").asBoolean()) {
            Feed.Side side = sideFromZone(execution.path("params").path("zone").asText(null));
            outcome = feedService.startVoiceFeed(user, baby.getId(), side);
            failureCode = "alreadyStarted";
        } else {
            outcome = feedService.stopVoiceFeed(user, baby.getId());
            failureCode = "alreadyStopped";
        }
        return outcome
                .map(feed -> ExecuteResult.success(deviceId, deviceStates.forBaby(baby)))
                .orElseGet(() -> ExecuteResult.error(deviceId, failureCode));
    }

    private Feed.Side sideFromZone(String zone) {
        if ("left".equalsIgnoreCase(zone)) return Feed.Side.L;
        if ("right".equalsIgnoreCase(zone)) return Feed.Side.R;
        return null;
    }

    private Map<String, Object> disconnect(AppUser user) {
        tokenService.revokeFor(user);
        return Map.of();
    }

    /** The caller's babies keyed by device id - the only ids that resolve. */
    private Map<String, Baby> babiesById(AppUser user) {
        return babyService.getBabies(user).stream()
                .collect(Collectors.toMap(baby -> String.valueOf(baby.getId()), Function.identity()));
    }

    private Map<String, Object> mode(String name, List<String> synonyms, List<Map<String, Object>> settings) {
        return Map.of(
                "name", name,
                "name_values", List.of(Map.of("name_synonym", synonyms, "lang", "en")),
                "settings", settings,
                "ordered", false);
    }

    private Map<String, Object> setting(String name, List<String> synonyms) {
        return Map.of(
                "setting_name", name,
                "setting_values", List.of(Map.of("setting_synonym", synonyms, "lang", "en")));
    }
}
