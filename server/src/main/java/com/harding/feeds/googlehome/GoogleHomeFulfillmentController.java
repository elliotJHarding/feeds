package com.harding.feeds.googlehome;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.googlehome.dto.FulfillmentRequest;
import com.harding.feeds.googlehome.dto.FulfillmentResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Google smart home fulfillment webhook. Authenticated by the app's own
 * access tokens (minted during account linking) on the standard resource
 * server chain; like the account-linking endpoints, its shape is fixed by
 * Google's protocol and deliberately lives outside model/openapi.yaml.
 */
@RestController
public class GoogleHomeFulfillmentController {

    private final GoogleHomeFulfillmentService fulfillmentService;

    public GoogleHomeFulfillmentController(GoogleHomeFulfillmentService fulfillmentService) {
        this.fulfillmentService = fulfillmentService;
    }

    @PostMapping("/googlehome/fulfillment")
    public FulfillmentResponse fulfill(@AuthenticationPrincipal AppUser user,
                                       @RequestBody FulfillmentRequest request) {
        return fulfillmentService.handle(user, request);
    }
}
