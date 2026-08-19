package com.slotforge.api.auth;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.slotforge.api.user.UserAccount;

public record RegistrationResponse(
        UUID id,
        String email,
        Set<String> roles,
        Instant createdAt
) {

    public static RegistrationResponse from(UserAccount user) {
        Set<String> roles = user.getRoles()
                .stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toUnmodifiableSet());

        return new RegistrationResponse(
                user.getId(),
                user.getEmail(),
                roles,
                user.getCreatedAt()
        );
    }
}
