package com.harding.feeds.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Two filter chains, as in meals: a public chain for the auth endpoints
 * (which authenticate via their request bodies) and a JWT resource-server
 * chain for everything else. The server is stateless - mobile-only, no
 * sessions, no CORS (the only client is the native Android app).
 */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    public WebSecurityConfig(JwtAuthenticationConverter jwtAuthenticationConverter) {
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Bean
    @Order(1)
    public SecurityFilterChain publicSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher(
                "/auth/login",
                "/auth/refresh",
                "/error",
                "/actuator/health/**"
            )
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
            )
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(GoogleAuthenticationProvider googleAuthenticationProvider) {
        return new ProviderManager(googleAuthenticationProvider);
    }
}
