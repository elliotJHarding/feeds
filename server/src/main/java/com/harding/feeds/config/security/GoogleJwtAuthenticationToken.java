package com.harding.feeds.config.security;

import com.harding.feeds.entity.AppUser;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;

public class GoogleJwtAuthenticationToken extends AbstractAuthenticationToken {

    private final String token;
    private final AppUser principal;

    public GoogleJwtAuthenticationToken(AppUser principal, String token, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.token = token;
        this.principal = principal;
    }

    public static GoogleJwtAuthenticationToken unauthenticated(String token) {
        return new GoogleJwtAuthenticationToken(null, token, null);
    }

    public static GoogleJwtAuthenticationToken authenticated(AppUser principal, String token, List<GrantedAuthority> authorities) {
        GoogleJwtAuthenticationToken idToken = new GoogleJwtAuthenticationToken(principal, token, authorities);
        idToken.setAuthenticated(true);
        return idToken;
    }

    @Override
    public String getCredentials() {
        return this.token;
    }

    @Override
    public AppUser getPrincipal() {
        return this.principal;
    }
}
