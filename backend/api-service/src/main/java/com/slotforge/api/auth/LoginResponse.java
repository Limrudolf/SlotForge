package com.slotforge.api.auth;

import java.time.Instant;

import com.slotforge.api.refreshtoken.IssuedRefreshToken;
import com.slotforge.api.security.IssuedAccessToken;

public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {

    public static LoginResponse from(
            IssuedAccessToken accessToken,
            IssuedRefreshToken refreshToken
    ) {
        return new LoginResponse(
                accessToken.value(),
                "Bearer",
                accessToken.expiresAt(),
                refreshToken.value(),
                refreshToken.expiresAt()
        );
    }
}
