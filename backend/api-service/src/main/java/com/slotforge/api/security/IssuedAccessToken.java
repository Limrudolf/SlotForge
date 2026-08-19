package com.slotforge.api.security;

import java.time.Instant;

public record IssuedAccessToken(
        String value,
        Instant expiresAt
) {
}
