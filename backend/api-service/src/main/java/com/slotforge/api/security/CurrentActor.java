package com.slotforge.api.security;

import java.util.Set;
import java.util.UUID;

import com.slotforge.api.user.RoleName;

public record CurrentActor(
        UUID userId,
        Set<RoleName> roles
) {

    public CurrentActor {
        roles = Set.copyOf(roles);
    }

    public boolean isAdmin() {
        return roles.contains(RoleName.ADMIN);
    }
}
