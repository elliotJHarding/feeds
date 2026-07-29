package com.harding.feeds.googlehome;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Google Home cloud-to-cloud integration, prefix "app.googlehome". The
 * client id and secret are credentials we invented and handed to Google in
 * the Home Developer Console; the project id is the console project's id,
 * which fixes the only redirect URIs Google may present.
 */
@Configuration
@ConfigurationProperties(prefix = "app.googlehome")
public class GoogleHomeProperties {

    /** Client id assigned to Google in the Home Developer Console. */
    private String clientId = "";

    /** Client secret assigned to Google in the Home Developer Console. */
    private String clientSecret = "";

    /** Home Developer Console project id (forms the allowed redirect URIs). */
    private String projectId = "";

    /**
     * Service-account key JSON (raw or base64) from the console's GCP
     * project, used for Report State; empty disables reporting.
     */
    private String serviceAccountKey = "";

    /** The only redirect URIs Google uses: its production and sandbox OAuth relays. */
    public List<String> allowedRedirectUris() {
        return List.of(
                "https://oauth-redirect.googleusercontent.com/r/" + projectId,
                "https://oauth-redirect-sandbox.googleusercontent.com/r/" + projectId
        );
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    public void setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
    }
}
