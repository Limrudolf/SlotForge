package com.slotforge.api.security;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.slotforge.api.user.RoleName;

@Component
public class CurrentActorProvider {

    private static final String ROLE_PREFIX = "ROLE_";

    public CurrentActor currentActor() {
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException(
                    "An authenticated JWT principal is required"
            );
        }

        UUID userId = UUID.fromString(jwt.getToken().getSubject());

        Set<RoleName> roles = authentication.getAuthorities()
                .stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(
                        ROLE_PREFIX.length()
                ))
                .map(RoleName::valueOf)
                .collect(Collectors.toUnmodifiableSet());

        return new CurrentActor(userId, roles);
    }
}
