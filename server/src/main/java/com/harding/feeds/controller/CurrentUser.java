package com.harding.feeds.controller;

import com.harding.feeds.entity.AppUser;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The authenticated AppUser for the current request. The generated API
 * interfaces fix the method signatures, so the principal cannot be injected
 * as a controller method parameter and is read from the security context
 * instead. Both authentication paths (JWT via JwtAuthenticationConverter,
 * test requests via spring-security-test) set an AppUser principal.
 */
final class CurrentUser {

    private CurrentUser() {
    }

    static AppUser get() {
        return (AppUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
