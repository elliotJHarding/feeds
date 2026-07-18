package com.harding.feeds.config.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.harding.feeds.entity.AppUser;
import com.harding.feeds.repository.AppUserRepository;
import com.harding.feeds.service.auth.VerifyGoogleJwtService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Verifies a Google ID token and upserts the AppUser: users are looked up by
 * the stable Google subject claim and created on first login.
 */
@Component
public class GoogleAuthenticationProvider implements AuthenticationProvider {

    private final VerifyGoogleJwtService jwtService;
    private final AppUserRepository userRepository;

    GoogleAuthenticationProvider(VerifyGoogleJwtService jwtService, AppUserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        String token = (String) authentication.getCredentials();

        try {
            GoogleIdToken googleIdToken = jwtService.verify(token);

            AppUser principal = userRepository.findByUsername(googleIdToken.getPayload().getSubject());

            if (principal == null) {
                principal = userRepository.save(new AppUser(googleIdToken));
            }

            List<GrantedAuthority> grantedAuthorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));

            return GoogleJwtAuthenticationToken.authenticated(principal, token, grantedAuthorities);

        } catch (Exception e) {
            throw new BadCredentialsException(e.getMessage());
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.equals(GoogleJwtAuthenticationToken.class);
    }
}
