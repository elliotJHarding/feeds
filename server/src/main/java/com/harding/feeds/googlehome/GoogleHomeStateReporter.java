package com.harding.feeds.googlehome;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pushes device state to Google's Home Graph
 * (devices:reportStateAndNotification), authenticated by the service-account
 * key from the Home Developer Console's GCP project. Sends happen on a
 * single background thread and failures are logged, never thrown - reporting
 * is best-effort and must not affect the mutation that triggered it.
 *
 * Disabled (no-op) when no service-account key is configured, as in localdev
 * and tests.
 */
@Component
public class GoogleHomeStateReporter {

    private static final Logger log = LoggerFactory.getLogger(GoogleHomeStateReporter.class);
    private static final String ENDPOINT = "https://homegraph.googleapis.com/v1/devices:reportStateAndNotification";
    private static final String SCOPE = "https://www.googleapis.com/auth/homegraph";

    private final GoogleHomeProperties properties;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private volatile GoogleCredentials credentials;

    public GoogleHomeStateReporter(GoogleHomeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean enabled() {
        return !properties.getServiceAccountKey().isEmpty();
    }

    /** Queues a state report for one device; returns immediately. */
    public void report(String agentUserId, String deviceId, Map<String, Object> states) {
        if (!enabled()) {
            return;
        }
        executor.submit(() -> send(agentUserId, deviceId, states));
    }

    private void send(String agentUserId, String deviceId, Map<String, Object> states) {
        try {
            Map<String, Object> body = Map.of(
                    "requestId", UUID.randomUUID().toString(),
                    "agentUserId", agentUserId,
                    "payload", Map.of("devices", Map.of("states", Map.of(deviceId, states))));

            HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT))
                    .header("Authorization", "Bearer " + accessToken())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("Report State failed for device {}: HTTP {} {}",
                        deviceId, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.warn("Report State failed for device {}: {}", deviceId, e.getMessage());
        }
    }

    private String accessToken() throws Exception {
        if (credentials == null) {
            synchronized (this) {
                if (credentials == null) {
                    credentials = GoogleCredentials
                            .fromStream(new ByteArrayInputStream(serviceAccountJson()))
                            .createScoped(List.of(SCOPE));
                }
            }
        }
        credentials.refreshIfExpired();
        return credentials.getAccessToken().getTokenValue();
    }

    /** The configured key, accepted as raw JSON or base64-encoded JSON. */
    private byte[] serviceAccountJson() {
        String key = properties.getServiceAccountKey().strip();
        if (key.startsWith("{")) {
            return key.getBytes(StandardCharsets.UTF_8);
        }
        return Base64.getDecoder().decode(key);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
