package com.slotforge.api.refreshtoken;

import com.slotforge.api.security.IssuedAccessToken;

public record RefreshedTokenPair(
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken
) {
}
