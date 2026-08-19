package com.slotforge.api.refreshtoken;

import java.time.Instant;

public record IssuedRefreshToken(
        String value,
        Instant expiresAt
) {
}
