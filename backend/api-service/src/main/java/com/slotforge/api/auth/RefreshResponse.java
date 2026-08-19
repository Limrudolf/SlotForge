package com.slotforge.api.auth;

import java.time.Instant;

import com.slotforge.api.refreshtoken.RefreshedTokenPair;

public record RefreshResponse(
        String accessToken,
        String tokenType,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt
) {

    public static RefreshResponse from(
            RefreshedTokenPair tokens
    ) {
        return new RefreshResponse(
                tokens.accessToken().value(),
                "Bearer",
                tokens.accessToken().expiresAt(),
                tokens.refreshToken().value(),
                tokens.refreshToken().expiresAt()
        );
    }
}
