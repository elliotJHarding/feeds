package com.harding.feeds.service.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

@Component
public class VerifyGoogleJwtService {

    private final GoogleIdTokenVerifier verifier;

    VerifyGoogleJwtService(@Value("${oauth.google-client-id}") String clientId) {
        verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(List.of(clientId))
                .build();
    }

    public GoogleIdToken verify(String token) throws GeneralSecurityException, IOException {

        GoogleIdToken idToken = verifier.verify(token);

        if (idToken == null) {
            throw new GeneralSecurityException("Invalid token");
        }

        return idToken;
    }
}
