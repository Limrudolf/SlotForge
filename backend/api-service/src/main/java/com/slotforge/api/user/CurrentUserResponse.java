package com.slotforge.api.user;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record CurrentUserResponse(
        UUID id,
        String email,
        String status,
        Set<String> roles,
        Instant createdAt
) {

    public static CurrentUserResponse from(UserAccount user) {
        Set<String> roles = user.getRoles()
                .stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toUnmodifiableSet());

        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getStatus().name(),
                roles,
                user.getCreatedAt()
        );
    }
}
