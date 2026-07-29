package com.harding.feeds.googlehome;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.harding.feeds.entity.AppUser;
import com.harding.feeds.entity.PublicDetails;
import com.harding.feeds.service.auth.VerifyGoogleJwtService;
import com.harding.feeds.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Google Home account-linking OAuth flow: authorize page, consent,
 * code-for-token exchange, and the non-expiring non-rotating refresh grant.
 */
class GoogleHomeLinkingTest extends IntegrationTest {

    private static final String REDIRECT_URI = "https://oauth-redirect.googleusercontent.com/r/test-gh-project";

    @MockitoBean
    private VerifyGoogleJwtService verifyGoogleJwtService;

    @Test
    void authorizePageRendersForAValidRequest() throws Exception {
        authorize(REDIRECT_URI, "test-gh-client")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("test-client-id")));
    }

    @Test
    void authorizeRejectsUnknownClientAndForeignRedirect() throws Exception {
        authorize(REDIRECT_URI, "wrong-client").andExpect(status().isBadRequest());
        authorize("https://evil.example.com/r/test-gh-project", "test-gh-client")
                .andExpect(status().isBadRequest());
    }

    @Test
    void fullLinkingFlowIssuesTokensThatWorkOnTheApi() throws Exception {
        AppUser user = userIn(group(), "parent@example.com");
        signInVerifies(user.getUsername());

        String code = consentCode();
        JsonNode tokens = readTree(exchange("authorization_code", "code", code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").isNumber())
                .andReturn().getResponse().getContentAsString());

        // The minted access token authenticates a real API call through the
        // resource-server chain, not the test principal shortcut.
        mockMvc.perform(get("/babies")
                        .header("Authorization", "Bearer " + tokens.get("access_token").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void consentRejectsUnknownAccountsAndUsersWithoutAGroup() throws Exception {
        signInVerifies("google-sub-nobody@example.com");
        consent().andExpect(status().isForbidden());

        AppUser groupless = new AppUser();
        groupless.setUsername("google-sub-solo@example.com");
        groupless.setEmail("solo@example.com");
        groupless.setPublicDetails(new PublicDetails("solo@example.com", null, null, null, true));
        appUserRepository.save(groupless);
        signInVerifies(groupless.getUsername());
        consent().andExpect(status().isForbidden());
    }

    @Test
    void consentRejectsAnUnverifiableCredential() throws Exception {
        when(verifyGoogleJwtService.verify(anyString()))
                .thenThrow(new GeneralSecurityException("Invalid token"));
        consent().andExpect(status().isUnauthorized());
    }

    @Test
    void authCodeIsSingleUse() throws Exception {
        AppUser user = userIn(group(), "parent@example.com");
        signInVerifies(user.getUsername());

        String code = consentCode();
        exchange("authorization_code", "code", code).andExpect(status().isOk());
        exchange("authorization_code", "code", code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void tokenEndpointRejectsBadClientCredentialsAndGrants() throws Exception {
        AppUser user = userIn(group(), "parent@example.com");
        signInVerifies(user.getUsername());
        String code = consentCode();

        mockMvc.perform(post("/googlehome/auth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", "test-gh-client")
                        .param("client_secret", "wrong-secret")
                        .param("grant_type", "authorization_code")
                        .param("code", code)
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));

        exchange("password", "code", code)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    void refreshGrantNeverRotatesTheRefreshToken() throws Exception {
        AppUser user = userIn(group(), "parent@example.com");
        signInVerifies(user.getUsername());

        JsonNode tokens = readTree(exchange("authorization_code", "code", consentCode())
                .andReturn().getResponse().getContentAsString());
        String refreshToken = tokens.get("refresh_token").asText();

        // Two consecutive refreshes with the same token both succeed, and no
        // replacement refresh token is ever issued.
        for (int i = 0; i < 2; i++) {
            exchange("refresh_token", "refresh_token", refreshToken)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.access_token").exists())
                    .andExpect(jsonPath("$.refresh_token").doesNotExist());
        }
    }

    // Helpers

    private ResultActions authorize(String redirectUri, String clientId) throws Exception {
        return mockMvc.perform(get("/googlehome/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("state", "opaque-state"));
    }

    private void signInVerifies(String subject) throws Exception {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject(subject);
        GoogleIdToken idToken = new GoogleIdToken(
                new JsonWebSignature.Header(), payload, new byte[0], new byte[0]);
        when(verifyGoogleJwtService.verify(anyString())).thenReturn(idToken);
    }

    private ResultActions consent() throws Exception {
        return mockMvc.perform(post("/googlehome/auth/consent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "credential", "gis-credential",
                        "clientId", "test-gh-client",
                        "redirectUri", REDIRECT_URI,
                        "state", "opaque-state"))));
    }

    /** Runs consent and pulls the authorization code out of the redirect URL. */
    private String consentCode() throws Exception {
        String body = consent()
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String redirectUrl = readTree(body).get("redirectUrl").asText();
        assertThat(redirectUrl).startsWith(REDIRECT_URI).contains("state=opaque-state");

        String query = URI.create(redirectUrl).getQuery();
        for (String param : query.split("&")) {
            if (param.startsWith("code=")) {
                return param.substring("code=".length());
            }
        }
        throw new AssertionError("No code in redirect url: " + redirectUrl);
    }

    private ResultActions exchange(String grantType, String paramName, String paramValue) throws Exception {
        return mockMvc.perform(post("/googlehome/auth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("client_id", "test-gh-client")
                .param("client_secret", "test-gh-secret")
                .param("grant_type", grantType)
                .param(paramName, paramValue)
                .param("redirect_uri", REDIRECT_URI));
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
