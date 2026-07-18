package com.harding.feeds.config.security;

import com.harding.feeds.entity.AppUser;
import com.harding.feeds.repository.AppUserRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Converts a decoded Bearer JWT into an Authentication whose principal is the
 * AppUser entity (loaded by the email claim), so controllers can work with the
 * domain user directly.
 */
@Component
public class JwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AppUserRepository userRepository;

    public JwtAuthenticationConverter(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        String email = jwt.getClaimAsString("email");

        if (email == null) {
            throw new IllegalArgumentException("JWT does not contain email claim");
        }

        AppUser user = userRepository.findByEmail(email);

        if (user == null) {
            throw new IllegalArgumentException("User not found: " + email);
        }

        return new UsernamePasswordAuthenticationToken(user, jwt, user.getAuthorities());
    }
}
